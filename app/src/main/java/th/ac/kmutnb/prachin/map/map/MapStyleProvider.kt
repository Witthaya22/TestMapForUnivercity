package th.ac.kmutnb.prachin.map.map

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import th.ac.kmutnb.prachin.map.data.model.TileSourceMode
import th.ac.kmutnb.prachin.map.data.repository.CampusRepository

/**
 * Decides which style MapLibre should load.
 *
 * In [TileSourceMode.OFFLINE_PACK] that is the remote style URL - MapLibre serves it, and
 * every tile and glyph it references, from the downloaded offline database, so nothing is
 * actually fetched over the network once the pack exists.
 *
 * In [TileSourceMode.BUNDLED] it is the loopback URL of [LocalTileServer], which reads the
 * MBTiles pack shipped inside the APK. That mode needs no network even on first launch.
 */
class MapStyleProvider(
    context: Context,
    private val campusRepository: CampusRepository,
) {

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var server: LocalTileServer? = null

    /**
     * @return the style URI for [mode], falling back to the remote style if the bundled pack
     * turns out to be unusable. A blank map is the worse failure of the two.
     */
    suspend fun styleUri(mode: TileSourceMode): String {
        val remote = campusRepository.config().styleUrl
        if (mode != TileSourceMode.BUNDLED) return remote
        return runCatching { bundledStyleUrl() }.getOrElse { e ->
            Log.e(TAG, "bundled tiles unavailable, falling back to $remote", e)
            remote
        }
    }

    /**
     * Starts the loopback server once and reuses it.
     *
     * It is deliberately never stopped: the socket costs one idle thread, while stopping it
     * while MapLibre still holds the style would blank the map mid-pan. The process dying
     * takes it with it.
     */
    private suspend fun bundledStyleUrl(): String = mutex.withLock {
        server?.let { return it.styleUrl }
        withContext(Dispatchers.IO) {
            val mbtiles = LocalTileServer.materialiseMbtiles(appContext)
            LocalTileServer(appContext, mbtiles).also {
                it.start()
                server = it
            }
        }.styleUrl
    }

    private companion object {
        const val TAG = "MapStyleProvider"
    }
}
