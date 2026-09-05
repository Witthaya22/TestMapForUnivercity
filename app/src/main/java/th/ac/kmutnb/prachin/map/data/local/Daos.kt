package th.ac.kmutnb.prachin.map.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PoiDao {

    @Query("SELECT * FROM poi ORDER BY displayOrder ASC, name ASC")
    fun observeAll(): Flow<List<PoiEntity>>

    @Query("SELECT * FROM poi ORDER BY displayOrder ASC, name ASC")
    suspend fun getAll(): List<PoiEntity>

    @Query("SELECT * FROM poi WHERE id = :id")
    suspend fun findById(id: String): PoiEntity?

    @Query("SELECT * FROM poi WHERE id = :id")
    fun observeById(id: String): Flow<PoiEntity?>

    @Query("SELECT COUNT(*) FROM poi")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(poi: PoiEntity)

    @Upsert
    suspend fun upsertAll(pois: List<PoiEntity>)

    /**
     * Used when seeding from assets: existing rows keep the user's edits, only genuinely new
     * places are added.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(pois: List<PoiEntity>): List<Long>

    @Query("UPDATE poi SET note = :note, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateNote(id: String, note: String, updatedAt: Long)

    @Query("DELETE FROM poi WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM poi WHERE isUserCreated = 0")
    suspend fun deleteSeeded()
}

@Dao
interface RouteHistoryDao {

    @Query("SELECT * FROM route_history ORDER BY completedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<RouteHistoryEntity>>

    @Insert
    suspend fun insert(entry: RouteHistoryEntity): Long

    @Query("DELETE FROM route_history")
    suspend fun clear()
}
