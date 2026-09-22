package th.ac.kmutnb.prachin.map.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.geojson.HazardGeoJson
import th.ac.kmutnb.prachin.map.data.local.HazardPointDao
import th.ac.kmutnb.prachin.map.data.local.toEntity
import th.ac.kmutnb.prachin.map.data.local.toHazardPoint
import th.ac.kmutnb.prachin.map.data.model.HazardPoint
import th.ac.kmutnb.prachin.map.data.model.HazardSeverity
import th.ac.kmutnb.prachin.map.data.model.HazardType
import java.util.UUID

/** What an import of a hazard file produced. */
data class HazardImportResult(
    val imported: Int,
    val skipped: Int,
)

/**
 * The hazards people have marked on campus.
 *
 * [activeHazards] is the one the map and the warning engine collect; it is the whole live
 * set rather than a radius query, because a campus carries tens of hazards, not thousands,
 * and holding them in memory turns "what should I warn about" into arithmetic instead of a
 * database round trip on every GPS fix.
 */
class HazardRepository(private val hazardPointDao: HazardPointDao) {

    val hazards: Flow<List<HazardPoint>> =
        hazardPointDao.observeAll().map { rows -> rows.map { it.toHazardPoint() } }

    val activeHazards: Flow<List<HazardPoint>> =
        hazardPointDao.observeActive().map { rows -> rows.map { it.toHazardPoint() } }

    suspend fun all(): List<HazardPoint> = hazardPointDao.getAll().map { it.toHazardPoint() }

    suspend fun find(id: String): HazardPoint? = hazardPointDao.findById(id)?.toHazardPoint()

    suspend fun countActive(): Int = hazardPointDao.countActive()

    /** Marks a new hazard and returns it. */
    suspend fun create(
        type: HazardType,
        severity: HazardSeverity,
        point: GeoPoint,
        radiusMeters: Double = HazardPoint.DEFAULT_RADIUS_M,
        description: String = "",
        soundId: String? = null,
        gpsAccuracy: Float? = null,
        now: Long = System.currentTimeMillis(),
    ): HazardPoint {
        val hazard = HazardPoint(
            id = "hazard_${UUID.randomUUID()}",
            type = type,
            severity = severity,
            point = point,
            radiusMeters = radiusMeters.coerceIn(
                HazardPoint.MIN_RADIUS_M,
                HazardPoint.MAX_RADIUS_M,
            ),
            description = description,
            soundId = soundId,
            isActive = true,
            gpsAccuracy = gpsAccuracy,
            createdAt = now,
            updatedAt = now,
        )
        hazardPointDao.upsert(hazard.toEntity())
        return hazard
    }

    suspend fun update(
        id: String,
        type: HazardType,
        severity: HazardSeverity,
        radiusMeters: Double,
        description: String,
        soundId: String?,
    ) {
        val existing = hazardPointDao.findById(id) ?: return
        hazardPointDao.upsert(
            existing.copy(
                type = type.id,
                severity = severity.id,
                radiusMeters = radiusMeters.coerceIn(
                    HazardPoint.MIN_RADIUS_M,
                    HazardPoint.MAX_RADIUS_M,
                ),
                description = description,
                soundId = soundId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Switches a hazard's warnings off without losing it.
     *
     * The normal way a hazard ends: the work finished, the drain was fixed. Deleting is for
     * a mark that was wrong in the first place.
     */
    suspend fun setActive(id: String, active: Boolean) =
        hazardPointDao.setActive(id, active, System.currentTimeMillis())

    suspend fun delete(id: String) = hazardPointDao.deleteById(id)

    suspend fun deleteAll() = hazardPointDao.deleteAll()

    suspend fun exportGeoJson(): String = withContext(Dispatchers.Default) {
        HazardGeoJson.write(all())
    }

    /** Ids are preserved, so importing the same file twice updates rather than duplicates. */
    suspend fun importGeoJson(json: String): HazardImportResult = withContext(Dispatchers.Default) {
        val parsed = HazardGeoJson.parse(json)
        if (parsed.items.isNotEmpty()) {
            hazardPointDao.upsertAll(parsed.items.map { it.toEntity() })
        }
        HazardImportResult(imported = parsed.items.size, skipped = parsed.issues.size)
    }
}
