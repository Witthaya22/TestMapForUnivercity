package th.ac.kmutnb.prachin.map.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import th.ac.kmutnb.prachin.map.data.assets.AssetPaths
import th.ac.kmutnb.prachin.map.data.assets.AssetReader
import th.ac.kmutnb.prachin.map.data.config.CampusConfig
import th.ac.kmutnb.prachin.map.data.config.CampusConfigException
import th.ac.kmutnb.prachin.map.data.config.CampusConfigParser
import th.ac.kmutnb.prachin.map.data.config.ConfigProblem
import th.ac.kmutnb.prachin.map.data.geojson.GeoJsonParseResult
import th.ac.kmutnb.prachin.map.data.geojson.GeoJsonParser
import th.ac.kmutnb.prachin.map.data.model.Poi
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath
import java.io.IOException

/**
 * Read-only reference data shipped in `assets/`: the campus configuration, the seed POIs and
 * the walking network. All three are small and never change at runtime, so each is parsed
 * once and cached.
 */
class CampusRepository(private val assets: AssetReader) {

    private val mutex = Mutex()

    private var cachedConfig: CampusConfig? = null
    private var cachedPaths: GeoJsonParseResult<WalkPath>? = null

    /**
     * @throws CampusConfigException when the file is missing, malformed, or still holds the
     * shipped 0.0 placeholders. Callers surface [ConfigProblem] as Thai text.
     */
    @Throws(CampusConfigException::class)
    suspend fun config(): CampusConfig = mutex.withLock {
        cachedConfig ?: withContext(Dispatchers.IO) {
            val json = try {
                assets.readText(AssetPaths.CAMPUS_CONFIG)
            } catch (e: IOException) {
                throw CampusConfigException(ConfigProblem.MALFORMED, e.message)
            }
            CampusConfigParser.parse(json)
        }.also { cachedConfig = it }
    }

    /** The walking network. An unreadable or empty file yields an empty result, not a throw. */
    suspend fun paths(): GeoJsonParseResult<WalkPath> = mutex.withLock {
        cachedPaths ?: withContext(Dispatchers.IO) {
            val json = runCatching { assets.readText(AssetPaths.PATHS) }.getOrNull()
            if (json == null) {
                GeoJsonParseResult(emptyList(), emptyList())
            } else {
                GeoJsonParser.parsePaths(json)
            }
        }.also { cachedPaths = it }
    }

    /** Raw text of the shipped POI file, or null when it is missing or unreadable. */
    suspend fun poisAssetJson(): String? = withContext(Dispatchers.IO) {
        runCatching { assets.readText(AssetPaths.POIS) }.getOrNull()
    }

    /** POIs shipped in assets, used to seed the database. */
    suspend fun seedPois(): GeoJsonParseResult<Poi> {
        val json = poisAssetJson() ?: return GeoJsonParseResult(emptyList(), emptyList())
        return GeoJsonParser.parsePois(json)
    }

    /** True when an MBTiles pack is present, i.e. TileSourceMode.BUNDLED can be offered. */
    suspend fun hasBundledTiles(): Boolean = withContext(Dispatchers.IO) {
        assets.exists(AssetPaths.BUNDLED_MBTILES) && assets.exists(AssetPaths.BUNDLED_STYLE)
    }
}
