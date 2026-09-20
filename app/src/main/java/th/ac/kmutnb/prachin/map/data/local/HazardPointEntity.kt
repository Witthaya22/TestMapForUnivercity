package th.ac.kmutnb.prachin.map.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.HazardPoint
import th.ac.kmutnb.prachin.map.data.model.HazardSeverity
import th.ac.kmutnb.prachin.map.data.model.HazardType

/**
 * A marked hazard as stored on the device.
 *
 * Its own table rather than a POI category: a hazard has a radius and an active flag that
 * mean nothing for a place, and every query that matters here - "what should I warn about
 * near this position" - would otherwise have to filter the whole POI table on every GPS
 * fix.
 *
 * [type] and [severity] are stored as their string ids, so an id that a future version no
 * longer knows degrades to OTHER / WARNING instead of failing to read the row.
 */
@Entity(tableName = "hazard_point", indices = [Index("isActive")])
data class HazardPointEntity(
    @PrimaryKey val id: String,
    val type: String,
    val severity: String,
    val lat: Double,
    val lon: Double,
    val radiusMeters: Double,
    val description: String,
    val isActive: Boolean,
    val gpsAccuracy: Float?,
    val createdAt: Long,
    val updatedAt: Long,
)

fun HazardPointEntity.toHazardPoint(): HazardPoint = HazardPoint(
    id = id,
    type = HazardType.fromId(type),
    severity = HazardSeverity.fromId(severity),
    point = GeoPoint(lat = lat, lon = lon),
    radiusMeters = radiusMeters,
    description = description,
    isActive = isActive,
    gpsAccuracy = gpsAccuracy,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun HazardPoint.toEntity(): HazardPointEntity = HazardPointEntity(
    id = id,
    type = type.id,
    severity = severity.id,
    lat = point.lat,
    lon = point.lon,
    radiusMeters = radiusMeters,
    description = description,
    isActive = isActive,
    gpsAccuracy = gpsAccuracy,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
