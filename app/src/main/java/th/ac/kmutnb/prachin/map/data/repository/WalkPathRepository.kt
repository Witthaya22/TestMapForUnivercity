package th.ac.kmutnb.prachin.map.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import th.ac.kmutnb.prachin.map.data.geojson.GeoJsonParseResult
import th.ac.kmutnb.prachin.map.data.geojson.GeoJsonParser
import th.ac.kmutnb.prachin.map.data.local.WalkPathDao
import th.ac.kmutnb.prachin.map.data.local.toEntity
import th.ac.kmutnb.prachin.map.data.local.toWalkPath
import th.ac.kmutnb.prachin.map.navigation.model.PathType
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath

/** What an import of a recorded-path file produced. */
data class PathImportResult(
    val imported: Int,
    val skipped: Int,
)

/**
 * Paths the user walked and recorded.
 *
 * The counterpart of [PoiRepository]: a surveyed path is stored here the moment recording
 * stops, and [RouteNetworkRepository] folds it straight into the routing graph. That is what
 * makes surveying useful on the spot - walk a path, and the next route can use it, with no
 * export, no editing of assets and no rebuild. Export stays available, but as a backup and a
 * way to move a survey between devices rather than as the only way to use it.
 */
class WalkPathRepository(private val walkPathDao: WalkPathDao) {

    val paths: Flow<List<WalkPath>> =
        walkPathDao.observeAll().map { rows -> rows.map { it.toWalkPath() } }

    suspend fun all(): List<WalkPath> = walkPathDao.getAll().map { it.toWalkPath() }

    suspend fun count(): Int = walkPathDao.count()

    /** Metres walked in total, for the survey progress readout. */
    suspend fun totalLengthMeters(): Double =
        walkPathDao.getAll().sumOf { it.lengthMeters }

    suspend fun save(
        path: WalkPath,
        lengthMeters: Double = 0.0,
        gpsAccuracy: Float? = null,
    ) = walkPathDao.upsert(path.toEntity(lengthMeters, gpsAccuracy))

    suspend fun rename(id: String, name: String?, type: PathType) =
        walkPathDao.updateDetails(id, name, type.id, System.currentTimeMillis())

    suspend fun delete(id: String) = walkPathDao.deleteById(id)

    suspend fun deleteAll() = walkPathDao.deleteAll()

    suspend fun exportGeoJson(): String = withContext(Dispatchers.Default) {
        GeoJsonParser.writePaths(all())
    }

    /**
     * Reads a recorded-path file back in, so a survey can be moved between devices or
     * restored after a reinstall.
     *
     * Ids are preserved, which makes importing the same file twice a no-op rather than a
     * duplicate of every path in it.
     */
    suspend fun importGeoJson(json: String, replaceExisting: Boolean): PathImportResult =
        withContext(Dispatchers.Default) {
            val parsed: GeoJsonParseResult<WalkPath> = GeoJsonParser.parsePaths(json)
            if (replaceExisting) walkPathDao.deleteAll()
            val entities = parsed.items.map { it.toEntity() }
            walkPathDao.upsertAll(entities)
            PathImportResult(imported = entities.size, skipped = parsed.issues.size)
        }
}
