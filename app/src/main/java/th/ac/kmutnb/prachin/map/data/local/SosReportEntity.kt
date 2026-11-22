package th.ac.kmutnb.prachin.map.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.SosCategory
import th.ac.kmutnb.prachin.map.data.model.SosReport
import th.ac.kmutnb.prachin.map.data.model.SosStatus

/**
 * A call for help sent from the SOS screen. Its own table, not a POI or a GPS reading:
 * it is a message to people, with a position attached, and is handled and closed by them.
 */
@Entity(tableName = "sos_report")
data class SosReportEntity(
    @PrimaryKey val id: String,
    val createdAt: Long,
    val username: String,
    val lat: Double?,
    val lon: Double?,
    val accuracyMeters: Float?,
    val category: String,
    val message: String,
    val trailName: String,
    val status: String,
    val handledAt: Long?,
    val adminNote: String,
    @androidx.room.ColumnInfo(defaultValue = "")
    val userId: String = "",
    @androidx.room.ColumnInfo(defaultValue = "")
    val touristId: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0")
    val isDemo: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "0")
    val synced: Boolean = false,
)

@Dao
interface SosReportDao {

    @Query("SELECT * FROM sos_report ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SosReportEntity>>

    @Insert
    suspend fun insert(report: SosReportEntity)

    @Query("UPDATE sos_report SET status = :status, handledAt = :handledAt, adminNote = :note WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, handledAt: Long?, note: String)

    @Query("DELETE FROM sos_report WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM sos_report WHERE status = :status")
    suspend fun deleteWithStatus(status: String)

    @Query("DELETE FROM sos_report WHERE isDemo = 1")
    suspend fun deleteDemo()

    @Insert
    suspend fun insertAll(reports: List<SosReportEntity>)

    @Query("SELECT COUNT(*) FROM sos_report WHERE synced = 0 AND isDemo = 0")
    fun observeUnsynced(): Flow<Int>
}

fun SosReportEntity.toReport() = SosReport(
    id = id,
    createdAt = createdAt,
    username = username,
    point = if (lat != null && lon != null) GeoPoint(lat = lat, lon = lon) else null,
    accuracyMeters = accuracyMeters,
    category = SosCategory.fromId(category),
    message = message,
    trailName = trailName,
    status = SosStatus.fromId(status),
    handledAt = handledAt,
    adminNote = adminNote,
    userId = userId,
    touristId = touristId,
    isDemo = isDemo,
    synced = synced,
)

fun SosReport.toEntity() = SosReportEntity(
    id = id,
    createdAt = createdAt,
    username = username,
    lat = point?.lat,
    lon = point?.lon,
    accuracyMeters = accuracyMeters,
    category = category.id,
    message = message,
    trailName = trailName,
    status = status.id,
    handledAt = handledAt,
    adminNote = adminNote,
    userId = userId,
    touristId = touristId,
    isDemo = isDemo,
    synced = synced,
)
