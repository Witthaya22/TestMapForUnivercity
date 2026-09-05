package th.ac.kmutnb.prachin.map.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.Poi
import th.ac.kmutnb.prachin.map.data.model.PoiCategory

/**
 * A POI as stored on the device.
 *
 * Seeded from `assets/data/pois.geojson` on first launch, after which this table is the
 * source of truth - the user can edit notes and descriptions and add their own places, and
 * those edits must survive an app update that ships a new asset file.
 */
@Entity(tableName = "poi")
data class PoiEntity(
    @PrimaryKey val id: String,
    val name: String,
    val shortName: String?,
    val lat: Double,
    val lon: Double,
    val category: String,
    /** Sort position within a category; unspecified POIs sort last. */
    val displayOrder: Int,
    val description: String,
    val note: String,
    val icon: String?,
    val isUserCreated: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** Metres of GPS accuracy when surveyed; null if the POI did not come from a survey. */
    val gpsAccuracy: Float?,
)

/** A completed navigation session, kept so the user can repeat a route they walked before. */
@Entity(tableName = "route_history")
data class RouteHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Waypoint POI ids in visiting order, comma separated. */
    val waypointIds: String,
    val distanceMeters: Double,
    val durationSeconds: Int,
    val completedAt: Long,
)

// --------------------------------------------------------------------------------------
// Mapping
// --------------------------------------------------------------------------------------

fun PoiEntity.toPoi(): Poi = Poi(
    id = id,
    name = name,
    shortName = shortName,
    category = PoiCategory.fromId(category),
    order = displayOrder,
    description = description,
    note = note,
    icon = icon,
    point = GeoPoint(lat = lat, lon = lon),
    isUserCreated = isUserCreated,
    gpsAccuracy = gpsAccuracy,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Poi.toEntity(): PoiEntity = PoiEntity(
    id = id,
    name = name,
    shortName = shortName,
    lat = point.lat,
    lon = point.lon,
    category = category.id,
    displayOrder = order,
    description = description,
    note = note,
    icon = icon,
    isUserCreated = isUserCreated,
    createdAt = createdAt,
    updatedAt = updatedAt,
    gpsAccuracy = gpsAccuracy,
)

fun RouteHistoryEntity.waypointIdList(): List<String> =
    waypointIds.split(',').map(String::trim).filter(String::isNotEmpty)
