package th.ac.kmutnb.prachin.map.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import th.ac.kmutnb.prachin.map.data.config.CampusConfig
import th.ac.kmutnb.prachin.map.data.prefs.AppPreferences
import kotlin.coroutines.resume

/** Why an offline download stopped. The UI turns these into Thai text. */
enum class OfflineDownloadError {
    /** No usable connection, or the tile server refused. Resuming picks up where it left off. */
    NETWORK,

    /** The bbox and zoom range need more tiles than the configured limit. */
    TILE_LIMIT,

    OTHER,
}

/** Progress of the one-off offline pack download. */
sealed interface OfflineDownloadState {

    data object Idle : OfflineDownloadState

    data class InProgress(
        val percent: Int,
        val completedBytes: Long,
        val completedTiles: Long,
        /**
         * MapLibre only knows the true resource total once it has parsed the tiles it already
         * fetched, so [percent] can move backwards until this turns true.
         */
        val isEstimatePrecise: Boolean,
    ) : OfflineDownloadState

    data class Complete(val sizeBytes: Long) : OfflineDownloadState

    data class Failed(val error: OfflineDownloadError, val detail: String? = null) : OfflineDownloadState
}

/** What is currently stored on the device. */
data class OfflinePackStatus(
    val exists: Boolean,
    val isComplete: Boolean,
    val sizeBytes: Long,
    val downloadedAt: Long,
)

/**
 * Downloads and manages the offline map pack. See `docs/OFFLINE.md`.
 *
 * MapLibre's offline API is callback-based and expects to be driven from the main thread, so
 * every call here hops to [Dispatchers.Main] and the callbacks are bridged into coroutines
 * and a [StateFlow].
 */
class OfflineMapRepository(
    context: Context,
    private val campusRepository: CampusRepository,
    private val preferences: AppPreferences,
) {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow<OfflineDownloadState>(OfflineDownloadState.Idle)
    val state: StateFlow<OfflineDownloadState> = _state.asStateFlow()

    private var activeRegion: OfflineRegion? = null

    private suspend fun manager(): OfflineManager = withContext(Dispatchers.Main) {
        OfflineManager.getInstance(appContext).apply {
            // The 6000 default is not enough: a ~4 sq km campus at z13-18 needs 10k-20k tiles,
            // and exceeding it aborts the download partway with an opaque callback.
            setOfflineMapboxTileCountLimit(TILE_COUNT_LIMIT)
        }
    }

    /**
     * Starts, or resumes, the download. Safe to call again after a failure - MapLibre keeps
     * what it already fetched and only requests the missing resources.
     */
    suspend fun startDownload() {
        val config = try {
            campusRepository.config()
        } catch (e: Exception) {
            _state.value = OfflineDownloadState.Failed(OfflineDownloadError.OTHER, e.message)
            return
        }

        withContext(Dispatchers.Main) {
            val offlineManager = manager()
            val region = existingRegion(offlineManager) ?: createRegion(offlineManager, config)
            if (region == null) {
                _state.value = OfflineDownloadState.Failed(OfflineDownloadError.OTHER)
                return@withContext
            }
            activeRegion = region
            observe(region)
            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
            _state.value = OfflineDownloadState.InProgress(0, 0, 0, isEstimatePrecise = false)
        }
    }

    /** Pauses the download without discarding what has already been fetched. */
    suspend fun pauseDownload() = withContext(Dispatchers.Main) {
        activeRegion?.setDownloadState(OfflineRegion.STATE_INACTIVE)
        _state.value = OfflineDownloadState.Idle
    }

    suspend fun status(): OfflinePackStatus = withContext(Dispatchers.Main) {
        val region = existingRegion(manager())
            ?: return@withContext OfflinePackStatus(false, false, 0, 0)
        val regionStatus = regionStatus(region)
        OfflinePackStatus(
            exists = true,
            isComplete = regionStatus?.isComplete ?: false,
            sizeBytes = regionStatus?.completedResourceSize ?: 0,
            downloadedAt = metadataCreatedAt(region),
        )
    }

    /** Removes the pack. The app falls back to needing a network until it is downloaded again. */
    suspend fun deletePack(): Boolean = withContext(Dispatchers.Main) {
        val region = existingRegion(manager()) ?: return@withContext false
        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        region.setObserver(null)
        activeRegion = null
        val deleted = suspendCancellableCoroutine { continuation ->
            region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                override fun onDelete() = continuation.resumeOnce(true)
                override fun onError(error: String) {
                    Log.w(TAG, "deleting the offline region failed: $error")
                    continuation.resumeOnce(false)
                }
            })
        }
        if (deleted) {
            preferences.setOfflineMapReady(false)
            _state.value = OfflineDownloadState.Idle
        }
        deleted
    }

    // ----------------------------------------------------------------------------------
    // Internals
    // ----------------------------------------------------------------------------------

    private fun observe(region: OfflineRegion) {
        region.setObserver(object : OfflineRegion.OfflineRegionObserver {

            override fun onStatusChanged(status: OfflineRegionStatus) {
                if (status.isComplete) {
                    // Leaving the region active keeps a background observer running and
                    // draining the battery for nothing.
                    region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    region.setObserver(null)
                    _state.value = OfflineDownloadState.Complete(status.completedResourceSize)
                    return
                }
                val required = status.requiredResourceCount
                val percent = if (required > 0) {
                    ((100.0 * status.completedResourceCount) / required).toInt().coerceIn(0, 99)
                } else {
                    0
                }
                _state.value = OfflineDownloadState.InProgress(
                    percent = percent,
                    completedBytes = status.completedResourceSize,
                    completedTiles = status.completedTileCount,
                    isEstimatePrecise = status.isRequiredResourceCountPrecise,
                )
            }

            override fun onError(error: OfflineRegionError) {
                Log.w(TAG, "offline download error: ${error.reason} ${error.message}")
                val mapped = when (error.reason) {
                    OfflineRegionError.REASON_CONNECTION -> OfflineDownloadError.NETWORK
                    OfflineRegionError.REASON_SERVER -> OfflineDownloadError.NETWORK
                    else -> OfflineDownloadError.OTHER
                }
                _state.value = OfflineDownloadState.Failed(mapped, error.message)
            }

            override fun mapboxTileCountLimitExceeded(limit: Long) {
                Log.w(TAG, "offline tile count limit exceeded: $limit")
                region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                _state.value = OfflineDownloadState.Failed(
                    OfflineDownloadError.TILE_LIMIT,
                    limit.toString(),
                )
            }
        })
    }

    private suspend fun existingRegion(offlineManager: OfflineManager): OfflineRegion? =
        suspendCancellableCoroutine { continuation ->
            offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    val match = offlineRegions?.firstOrNull { metadataName(it) == REGION_NAME }
                    continuation.resumeOnce(match)
                }

                override fun onError(error: String) {
                    Log.w(TAG, "listing offline regions failed: $error")
                    continuation.resumeOnce(null)
                }
            })
        }

    private suspend fun createRegion(
        offlineManager: OfflineManager,
        config: CampusConfig,
    ): OfflineRegion? {
        val definition = OfflineTilePyramidRegionDefinition(
            config.styleUrl,
            LatLngBounds.from(
                config.bbox.maxLat,
                config.bbox.maxLon,
                config.bbox.minLat,
                config.bbox.minLon,
            ),
            config.minZoom.toDouble(),
            config.maxZoom.toDouble(),
            appContext.resources.displayMetrics.density,
            // MUST stay false. `true` tells MapLibre to render CJK/Thai with a device font
            // and therefore NOT to download the glyph ranges, which leaves every Thai place
            // name blank once the device is offline. See docs/OFFLINE.md.
            false,
        )

        val metadata = JSONObject()
            .put(METADATA_NAME, REGION_NAME)
            .put(METADATA_CREATED_AT, System.currentTimeMillis())
            .toString()
            .toByteArray(Charsets.UTF_8)

        return suspendCancellableCoroutine { continuation ->
            offlineManager.createOfflineRegion(
                definition,
                metadata,
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) =
                        continuation.resumeOnce(offlineRegion)

                    override fun onError(error: String) {
                        Log.w(TAG, "creating the offline region failed: $error")
                        continuation.resumeOnce(null)
                    }
                },
            )
        }
    }

    private suspend fun regionStatus(region: OfflineRegion): OfflineRegionStatus? =
        suspendCancellableCoroutine { continuation ->
            // This one interface declares both parameters nullable, unlike its siblings.
            region.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                override fun onStatus(status: OfflineRegionStatus?) = continuation.resumeOnce(status)
                override fun onError(error: String?) = continuation.resumeOnce(null)
            })
        }

    private fun metadataJson(region: OfflineRegion): JSONObject? = runCatching {
        JSONObject(String(region.metadata, Charsets.UTF_8))
    }.getOrNull()

    private fun metadataName(region: OfflineRegion): String? =
        metadataJson(region)?.optString(METADATA_NAME)?.takeIf { it.isNotEmpty() }

    private fun metadataCreatedAt(region: OfflineRegion): Long =
        metadataJson(region)?.optLong(METADATA_CREATED_AT) ?: 0L

    /** MapLibre occasionally fires a callback twice; resuming a continuation twice crashes. */
    private fun <T> CancellableContinuation<T>.resumeOnce(value: T) {
        if (isActive) resume(value)
    }

    private companion object {
        const val TAG = "OfflineMapRepository"
        const val REGION_NAME = "kmutnb_prachin_campus"
        const val METADATA_NAME = "name"
        const val METADATA_CREATED_AT = "createdAt"

        /** Headroom over the ~10k-20k tiles a campus-sized bbox needs at z13-18. */
        const val TILE_COUNT_LIMIT = 50_000L
    }
}
