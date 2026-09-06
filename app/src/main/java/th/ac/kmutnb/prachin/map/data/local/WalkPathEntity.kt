package th.ac.kmutnb.prachin.map.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.navigation.model.PathType
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath

/**
 * A stretch of the route network stored on the device.
 *
 * Paths recorded by walking them land here rather than only in an exported file, which is
 * what lets a survey change the map the walker is standing on instead of requiring a rebuild
 * of the app. Seeded paths stay in assets; this table is everything the surveyor added.
 *
 * Geometry is kept as a `lon,lat;lon,lat` string. It is only ever read whole, never queried
 * by coordinate, so a child table with one row per vertex would cost a join and thousands of
 * rows to serve exactly one access pattern.
 */
@Entity(tableName = "walk_path")
data class WalkPathEntity(
    @PrimaryKey val id: String,
    val name: String?,
    /** [PathType.id]; an unknown value falls back to a footway when read. */
    val type: String,
    val encodedPoints: String,
    val oneway: Boolean,
    val lit: Boolean,
    val covered: Boolean,
    /** Metres walked while recording, before simplification. */
    val lengthMeters: Double,
    /** Mean GPS accuracy of the fixes that formed it, or null when not recorded. */
    val gpsAccuracy: Float?,
    val createdAt: Long,
    val updatedAt: Long,
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

fun WalkPathEntity.toWalkPath(): WalkPath = WalkPath(
    id = id,
    type = PathType.fromId(type) ?: PathType.FOOTWAY,
    points = decodePoints(encodedPoints),
    name = name,
    oneway = oneway,
    lit = lit,
    covered = covered,
)

fun WalkPath.toEntity(
    lengthMeters: Double = 0.0,
    gpsAccuracy: Float? = null,
    now: Long = System.currentTimeMillis(),
): WalkPathEntity = WalkPathEntity(
    id = id,
    name = name,
    type = type.id,
    encodedPoints = encodePoints(points),
    oneway = oneway,
    lit = lit,
    covered = covered,
    lengthMeters = lengthMeters,
    gpsAccuracy = gpsAccuracy,
    createdAt = now,
    updatedAt = now,
)
