package th.ac.kmutnb.prachin.map.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import th.ac.kmutnb.prachin.map.data.geojson.TrackLogExporter
import th.ac.kmutnb.prachin.map.data.local.TrackLogDao
import th.ac.kmutnb.prachin.map.data.local.toEntity
import th.ac.kmutnb.prachin.map.data.local.toTrackLog
import th.ac.kmutnb.prachin.map.data.model.TrackCaptureMode
import th.ac.kmutnb.prachin.map.data.model.TrackLog
import th.ac.kmutnb.prachin.map.survey.RecordedTrack
import java.util.UUID

/**
 * The paths people made by walking them.
 *
 * Sibling of [GpsPointRepository] and held to the same rule: a track stores what the
 * receiver observed, and nothing about what the path is for. The one thing it can do that
 * a logged point cannot is join the routing graph - see [routableTracks] - because a line
 * walked where no map has one is only useful if something can then guide you along it.
 */
class TrackLogRepository(private val trackLogDao: TrackLogDao) {

    val tracks: Flow<List<TrackLog>> =
        trackLogDao.observeAll().map { rows -> rows.map { it.toTrackLog() } }

    /** What the router is allowed to use; the rest stay as records. */
    val routableTracks: Flow<List<TrackLog>> =
        trackLogDao.observeRoutable().map { rows -> rows.map { it.toTrackLog() } }

    suspend fun all(): List<TrackLog> = trackLogDao.getAll().map { it.toTrackLog() }

    suspend fun count(): Int = trackLogDao.count()

    suspend fun totalLengthMeters(): Double = trackLogDao.getAll().sumOf { it.lengthMeters }

    /**
     * The next running label, continuing the highest number already stored.
     *
     * Continued rather than "however many rows there are": deleting a bad T004 must not
     * make the next track T004 as well, because whatever the walker wrote down already
     * says which numbered track is which.
     */
    suspend fun nextCode(): String {
        val highest = trackLogDao.getAll()
            .mapNotNull { CODE_PATTERN.matchEntire(it.code)?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull() ?: 0
        return CODE_PREFIX + "%03d".format(highest + 1)
    }

    /**
     * Stores a finished walk and returns it.
     *
     * New tracks are routable by default. Somebody who walked a path in order to have one
     * wants to be guided along it, and making that a second deliberate step would mean a
     * survey that silently does nothing until you find the switch.
     */
    suspend fun save(
        recorded: RecordedTrack,
        code: String,
        captureMode: TrackCaptureMode,
        note: String = "",
        useForRouting: Boolean = true,
        recordedAt: Long = System.currentTimeMillis(),
    ): TrackLog {
        val track = TrackLog(
            id = "track_${UUID.randomUUID()}",
            code = code,
            points = recorded.points,
            lengthMeters = recorded.lengthMeters,
            averageAccuracyMeters = recorded.averageAccuracyMeters,
            worstAccuracyMeters = recorded.worstAccuracyMeters,
            satellitesUsed = recorded.satellites.inUse,
            satellitesVisible = recorded.satellites.visible,
            fixCount = recorded.fixCount,
            rejectedCount = recorded.rejectedCount,
            durationSeconds = recorded.durationSeconds,
            note = note,
            isUsedForRouting = useForRouting,
            recordedAt = recordedAt,
            captureMode = captureMode,
        )
        trackLogDao.upsert(track.toEntity())
        return track
    }

    /** The note is the only editable field: the rest is what the receiver measured. */
    suspend fun updateNote(id: String, note: String) = trackLogDao.updateNote(id, note)

    /**
     * Takes a track in or out of the routing graph without touching the measurement.
     *
     * The normal way a bad walk ends - the path turned out to be a driveway, or it was
     * walked twice. Deleting is for a recording that should never have been kept at all.
     */
    suspend fun setUsedForRouting(id: String, used: Boolean) =
        trackLogDao.setUsedForRouting(id, used)

    suspend fun delete(id: String) = trackLogDao.deleteById(id)

    suspend fun deleteAll() = trackLogDao.deleteAll()

    /**
     * @param ids the tracks to write, in the order they were walked. Null exports every
     * one; an empty set exports nothing, which is what an export with nothing ticked
     * should do rather than quietly meaning "all of them".
     */
    suspend fun exportGeoJson(ids: Set<String>? = null): String =
        withContext(Dispatchers.Default) { TrackLogExporter.writeGeoJson(chosen(ids)) }

    suspend fun exportCsv(ids: Set<String>? = null): String =
        withContext(Dispatchers.Default) { TrackLogExporter.writeCsv(chosen(ids)) }

    private suspend fun chosen(ids: Set<String>?): List<TrackLog> =
        all().filter { ids == null || it.id in ids }

    private companion object {
        const val CODE_PREFIX = "T"
        val CODE_PATTERN = Regex("""T(\d+)""")
    }
}
