package th.ac.kmutnb.prachin.map.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Locale
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.TrackCaptureMode
import th.ac.kmutnb.prachin.map.data.model.TrackLog
import th.ac.kmutnb.prachin.map.data.model.TrackVertexInfo
import th.ac.kmutnb.prachin.map.data.model.VertexSource

/**
 * A walked path as stored on the device.
 *
 * Replaced the old `walk_path` table, which mixed the geometry with routing description -
 * a name, a path type, whether it was lit or covered. Those were four questions asked at
 * the moment a walk ended, answered by guessing, and none of them were measurements. What
 * is here instead is what the receiver observed plus one free-text note.
 *
 * Geometry is kept as a `lon,lat;lon,lat` string. It is only ever read whole, never
 * queried by coordinate, so a child table with one row per vertex would cost a join and
 * thousands of rows to serve exactly one access pattern.
 *
 * Indexed on `isUsedForRouting` because the routing graph re-reads the enabled tracks on
 * every change, while the full list is only opened when someone is managing them.
 */
@Entity(tableName = "track_log", indices = [Index("isUsedForRouting")])
data class TrackLogEntity(
    @PrimaryKey val id: String,
    val code: String,
    val encodedPoints: String,
    val lengthMeters: Double,
    val averageAccuracyMeters: Float,
    val worstAccuracyMeters: Float,
    val satellitesUsed: Int,
    val satellitesVisible: Int,
    val fixCount: Int,
    val rejectedCount: Int,
    val durationSeconds: Int,
    val note: String,
    val isUsedForRouting: Boolean,
    val recordedAt: Long,
    val captureMode: String,
    /**
     * Per-vertex accuracy and provenance, `acc|source|lon,lat` per vertex, `;` between.
     * Blank for rows written before version 8 - see [TrackLog.vertexInfo].
     */
    @ColumnInfo(defaultValue = "")
    val encodedVertexInfo: String = "",
)

fun TrackLogEntity.toTrackLog(): TrackLog = TrackLog(
    id = id,
    code = code,
    points = decodePoints(encodedPoints),
    lengthMeters = lengthMeters,
    averageAccuracyMeters = averageAccuracyMeters,
    worstAccuracyMeters = worstAccuracyMeters,
    satellitesUsed = satellitesUsed,
    satellitesVisible = satellitesVisible,
    fixCount = fixCount,
    rejectedCount = rejectedCount,
    durationSeconds = durationSeconds,
    note = note,
    isUsedForRouting = isUsedForRouting,
    recordedAt = recordedAt,
    captureMode = TrackCaptureMode.fromId(captureMode),
    vertexInfo = decodeVertexInfo(encodedVertexInfo),
)

fun TrackLog.toEntity(): TrackLogEntity = TrackLogEntity(
    id = id,
    code = code,
    encodedPoints = encodePoints(points),
    lengthMeters = lengthMeters,
    averageAccuracyMeters = averageAccuracyMeters,
    worstAccuracyMeters = worstAccuracyMeters,
    satellitesUsed = satellitesUsed,
    satellitesVisible = satellitesVisible,
    fixCount = fixCount,
    rejectedCount = rejectedCount,
    durationSeconds = durationSeconds,
    note = note,
    isUsedForRouting = isUsedForRouting,
    recordedAt = recordedAt,
    captureMode = captureMode.id,
    encodedVertexInfo = encodeVertexInfo(vertexInfo),
)

/** `lon,lat;lon,lat` - seven decimals, which is about a centimetre. */
internal fun encodePoints(points: List<GeoPoint>): String =
    points.joinToString(";") { "%.7f,%.7f".format(it.lon, it.lat) }

internal fun decodePoints(encoded: String): List<GeoPoint> =
    encoded.split(';').mapNotNull { pair ->
        val parts = pair.split(',')
        if (parts.size != 2) return@mapNotNull null
        val lon = parts[0].toDoubleOrNull() ?: return@mapNotNull null
        val lat = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        GeoPoint(lat = lat, lon = lon)
    }

/**
 * `acc|source|lon,lat` per vertex. Accuracy is written with [Float.toString] so it comes
 * back bit for bit, and in a fixed locale-free form: a device set to a comma-decimal locale
 * must not write a row that it then cannot read.
 */
internal fun encodeVertexInfo(info: List<TrackVertexInfo>): String =
    info.joinToString(";") { vertex ->
        val accuracy = vertex.accuracyMeters?.toString().orEmpty()
        val original = vertex.originalPoint
            ?.let { String.format(Locale.US, "%.7f,%.7f", it.lon, it.lat) }
            .orEmpty()
        "$accuracy|${vertex.source.id}|$original"
    }

/** Anything unreadable becomes "unknown" rather than failing the whole track. */
internal fun decodeVertexInfo(encoded: String): List<TrackVertexInfo> {
    if (encoded.isBlank()) return emptyList()
    return encoded.split(';').map { entry ->
        val parts = entry.split('|')
        val original = parts.getOrNull(2)
            ?.takeIf { it.isNotBlank() }
            ?.let { decodePoints(it).firstOrNull() }
        TrackVertexInfo(
            accuracyMeters = parts.getOrNull(0)?.toFloatOrNull(),
            source = VertexSource.fromId(parts.getOrNull(1)),
            originalPoint = original,
        )
    }
}
