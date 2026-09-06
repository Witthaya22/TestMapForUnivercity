package th.ac.kmutnb.prachin.map.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import th.ac.kmutnb.prachin.map.data.model.TileSourceMode

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Small persisted flags. Anything larger belongs in Room. */
class AppPreferences(context: Context) {

    private val store = context.applicationContext.dataStore

    /** True once an offline pack has finished downloading and is usable without a network. */
    val offlineMapReady: Flow<Boolean> = store.data.map { it[KEY_OFFLINE_READY] ?: false }

    /** Epoch millis of the last successful offline download, or 0. */
    val offlineMapDownloadedAt: Flow<Long> = store.data.map { it[KEY_OFFLINE_DOWNLOADED_AT] ?: 0L }

    val tileSourceMode: Flow<TileSourceMode> = store.data.map {
        TileSourceMode.fromId(it[KEY_TILE_SOURCE_MODE])
    }

    /** True once the POIs shipped in assets have been copied into Room. */
    val poisSeeded: Flow<Boolean> = store.data.map { it[KEY_POIS_SEEDED] ?: false }

    /** Recalculate automatically when the user strays off the route. */
    val autoRecalculate: Flow<Boolean> = store.data.map { it[KEY_AUTO_RECALCULATE] ?: true }

    /** Unlocked by tapping the logo seven times; reveals the GPS debug screen. */
    val debugUnlocked: Flow<Boolean> = store.data.map { it[KEY_DEBUG_UNLOCKED] ?: false }

    /** Keep the screen awake while navigating. */
    val keepScreenOn: Flow<Boolean> = store.data.map { it[KEY_KEEP_SCREEN_ON] ?: true }

    suspend fun setOfflineMapReady(ready: Boolean, downloadedAt: Long = System.currentTimeMillis()) {
        store.edit {
            it[KEY_OFFLINE_READY] = ready
            if (ready) it[KEY_OFFLINE_DOWNLOADED_AT] = downloadedAt else it.remove(KEY_OFFLINE_DOWNLOADED_AT)
        }
    }

    suspend fun setTileSourceMode(mode: TileSourceMode) {
        store.edit { it[KEY_TILE_SOURCE_MODE] = mode.id }
    }

    suspend fun setPoisSeeded(seeded: Boolean) {
        store.edit { it[KEY_POIS_SEEDED] = seeded }
    }

    /**
     * Digest of the `pois.geojson` the database was last seeded from.
     *
     * Stored rather than a plain flag so that an app update shipping corrected coordinates
     * or new places actually reaches an existing install. Without it, everything improved
     * after the user's first launch would be invisible to them forever.
     */
    val seededPoisDigest: Flow<String?> = store.data.map { it[KEY_POIS_DIGEST] }

    suspend fun setSeededPoisDigest(digest: String) {
        store.edit { it[KEY_POIS_DIGEST] = digest }
    }

    /**
     * When true the router ignores the paths shipped in assets and uses only what the user
     * walked and recorded.
     *
     * For someone mapping the campus themselves this is the difference between surveying and
     * correcting: with the imported network switched off, what they see on the map is exactly
     * what they have walked, and nothing has to be reconciled against someone else's data.
     */
    val surveyedPathsOnly: Flow<Boolean> = store.data.map { it[KEY_SURVEYED_ONLY] ?: false }

    suspend fun setSurveyedPathsOnly(enabled: Boolean) {
        store.edit { it[KEY_SURVEYED_ONLY] = enabled }
    }

    suspend fun setAutoRecalculate(enabled: Boolean) {
        store.edit { it[KEY_AUTO_RECALCULATE] = enabled }
    }

    suspend fun setDebugUnlocked(unlocked: Boolean) {
        store.edit { it[KEY_DEBUG_UNLOCKED] = unlocked }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        store.edit { it[KEY_KEEP_SCREEN_ON] = enabled }
    }

    private companion object {
        val KEY_OFFLINE_READY = booleanPreferencesKey("offline_map_ready")
        val KEY_OFFLINE_DOWNLOADED_AT = longPreferencesKey("offline_map_downloaded_at")
        val KEY_TILE_SOURCE_MODE = stringPreferencesKey("tile_source_mode")
        val KEY_POIS_SEEDED = booleanPreferencesKey("pois_seeded")
        val KEY_POIS_DIGEST = stringPreferencesKey("pois_seed_digest")
        val KEY_SURVEYED_ONLY = booleanPreferencesKey("surveyed_paths_only")
        val KEY_AUTO_RECALCULATE = booleanPreferencesKey("auto_recalculate")
        val KEY_DEBUG_UNLOCKED = booleanPreferencesKey("debug_unlocked")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }
}
