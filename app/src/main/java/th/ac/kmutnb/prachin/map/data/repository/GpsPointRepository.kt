package th.ac.kmutnb.prachin.map.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import th.ac.kmutnb.prachin.map.data.geojson.GpsPointExporter
import th.ac.kmutnb.prachin.map.data.local.GpsPointDao
import th.ac.kmutnb.prachin.map.data.local.toEntity
import th.ac.kmutnb.prachin.map.data.local.toGpsPoint
import th.ac.kmutnb.prachin.map.data.model.GpsCaptureMode
import th.ac.kmutnb.prachin.map.data.model.GpsPoint
import th.ac.kmutnb.prachin.map.survey.SurveyedPoint
import java.util.UUID

/**
 * The GPS point log: coordinates measured in the field, kept as measurements.
 *
 * Sibling of [PoiRepository] and [WalkPathRepository] but deliberately not connected to
 * either. A logged point never becomes a place on the map by itself and never joins the
 * routing graph - it is raw evidence, and the value of the export is that it stays that
 * way. Turning one into a POI is a separate, explicit act in the surveyor screens.
 */
class GpsPointRepository(private val gpsPointDao: GpsPointDao) {

    val points: Flow<List<GpsPoint>> =
        gpsPointDao.observeAll().map { rows -> rows.map { it.toGpsPoint() } }

    suspend fun all(): List<GpsPoint> = gpsPointDao.getAll().map { it.toGpsPoint() }

    suspend fun count(): Int = gpsPointDao.count()

    /**
     * The next running label, continuing the highest number already stored.
     *
     * Continued rather than "however many rows there are": deleting a bad P004 must not
     * make the next point P004 as well, because the surveyor's paper notes already say
     * which numbered point is which.
     */
    suspend fun nextCode(): String {
        val highest = gpsPointDao.getAll()
            .mapNotNull { CODE_PATTERN.matchEntire(it.code)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return CODE_PREFIX + "%03d".format(highest + 1)
    }

    /** Stores a finished measurement and returns it, so the caller can show what was saved. */
    suspend fun save(
        surveyed: SurveyedPoint,
        code: String,
        captureMode: GpsCaptureMode,
        note: String = "",
        recordedAt: Long = System.currentTimeMillis(),
    ): GpsPoint {
        val point = GpsPoint(
            id = "gps_${UUID.randomUUID()}",
            code = code,
            point = surveyed.point,
            accuracyMeters = surveyed.averageAccuracyMeters,
            elevationMeters = surveyed.elevationMeters,
            verticalAccuracyMeters = surveyed.verticalAccuracyMeters,
            satellitesUsed = surveyed.satellites.inUse,
            satellitesVisible = surveyed.satellites.visible,
            sampleCount = surveyed.sampleCount,
            rejectedCount = surveyed.rejectedCount,
            spreadMeters = surveyed.spreadMeters,
            durationSeconds = surveyed.durationSeconds,
            note = note,
            recordedAt = recordedAt,
            captureMode = captureMode,
        )
        gpsPointDao.upsert(point.toEntity())
        return point
    }

    /** The note is the only editable field: everything else is what the receiver measured. */
    suspend fun updateNote(id: String, note: String) = gpsPointDao.updateNote(id, note)

    suspend fun delete(id: String) = gpsPointDao.deleteById(id)

    suspend fun deleteAll() = gpsPointDao.deleteAll()

    /**
     * @param mode null to export everything, or one capture mode to export only what that
     * screen recorded. Both screens write to one log, so this is how a file of only the
     * readings taken with the map in front of the surveyor can still be produced.
     */
    suspend fun exportGeoJson(mode: GpsCaptureMode? = null): String =
        withContext(Dispatchers.Default) { GpsPointExporter.writeGeoJson(all(mode)) }

    suspend fun exportCsv(mode: GpsCaptureMode? = null): String =
        withContext(Dispatchers.Default) { GpsPointExporter.writeCsv(all(mode)) }

    private suspend fun all(mode: GpsCaptureMode?): List<GpsPoint> =
        all().filter { mode == null || it.captureMode == mode }

    private companion object {
        const val CODE_PREFIX = "P"
        val CODE_PATTERN = Regex("""P(\d+)""")
    }
}
