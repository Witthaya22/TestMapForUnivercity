package th.ac.kmutnb.prachin.map.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.GpsCaptureMode
import th.ac.kmutnb.prachin.map.data.model.GpsPoint

/**
 * A measured coordinate as stored on the device.
 *
 * Column names match the model's property names, which in turn match the keys of the
 * exported GeoJSON and the headers of the exported CSV. One name per value across the whole
 * system means a field can be traced from the screen to the file without a lookup table.
 *
 * Nothing seeds this table: every row was produced by someone standing outdoors with the
 * phone, so there is no asset file to reconcile it against and no seeded/user distinction
 * to keep.
 */
@Entity(tableName = "gps_point")
data class GpsPointEntity(
    @PrimaryKey val id: String,
    val code: String,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    val elevationMeters: Double?,
    val verticalAccuracyMeters: Float?,
    val satellitesUsed: Int,
    val satellitesVisible: Int,
    val sampleCount: Int,
    val rejectedCount: Int,
    val spreadMeters: Double,
    val durationSeconds: Int,
    val note: String,
    val recordedAt: Long,
    /**
     * [GpsCaptureMode.id]; an unknown value reads back as the map screen.
     *
     * The default is declared here as well as in the migration so the two schemas are
     * identical whether a device upgraded into this column or was installed with it.
     */
    @ColumnInfo(defaultValue = "map") val captureMode: String,
)

fun GpsPointEntity.toGpsPoint(): GpsPoint = GpsPoint(
    id = id,
    code = code,
    point = GeoPoint(lat = lat, lon = lon),
    accuracyMeters = accuracyMeters,
    elevationMeters = elevationMeters,
    verticalAccuracyMeters = verticalAccuracyMeters,
    satellitesUsed = satellitesUsed,
    satellitesVisible = satellitesVisible,
    sampleCount = sampleCount,
    rejectedCount = rejectedCount,
    spreadMeters = spreadMeters,
    durationSeconds = durationSeconds,
    note = note,
    recordedAt = recordedAt,
    captureMode = GpsCaptureMode.fromId(captureMode),
)

fun GpsPoint.toEntity(): GpsPointEntity = GpsPointEntity(
    id = id,
    code = code,
    lat = point.lat,
    lon = point.lon,
    accuracyMeters = accuracyMeters,
    elevationMeters = elevationMeters,
    verticalAccuracyMeters = verticalAccuracyMeters,
    satellitesUsed = satellitesUsed,
    satellitesVisible = satellitesVisible,
    sampleCount = sampleCount,
    rejectedCount = rejectedCount,
    spreadMeters = spreadMeters,
    durationSeconds = durationSeconds,
    note = note,
    recordedAt = recordedAt,
    captureMode = captureMode.id,
)
