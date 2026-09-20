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

    /**
     * Removes seeded POIs that a newer asset file no longer contains.
     *
     * `updatedAt = createdAt` is the untouched test: a row the user has renamed, annotated or
     * moved is kept even when its id disappears from the asset, because their work is worth
     * more than a tidy table. Everything else would otherwise linger as a duplicate when an
     * id changes - which is exactly what happened when gates moved from `osm_n<id>` to
     * `gate_<n>`.
     */
    @Query(
        "DELETE FROM poi WHERE isUserCreated = 0 AND updatedAt = createdAt" +
            " AND id NOT IN (:keepIds)"
    )
    suspend fun deleteUntouchedSeededExcept(keepIds: List<String>): Int
}

@Dao
interface WalkPathDao {

    @Query("SELECT * FROM walk_path ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<WalkPathEntity>>

    @Query("SELECT * FROM walk_path ORDER BY createdAt ASC")
    suspend fun getAll(): List<WalkPathEntity>

    @Query("SELECT COUNT(*) FROM walk_path")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(path: WalkPathEntity)

    @Upsert
    suspend fun upsertAll(paths: List<WalkPathEntity>)

    @Query("UPDATE walk_path SET name = :name, type = :type, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDetails(id: String, name: String?, type: String, updatedAt: Long)

    @Query("DELETE FROM walk_path WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM walk_path")
    suspend fun deleteAll()
}

@Dao
interface GpsPointDao {

    /** Oldest first, so the running codes P001, P002... read in the order they were walked. */
    @Query("SELECT * FROM gps_point ORDER BY recordedAt ASC")
    fun observeAll(): Flow<List<GpsPointEntity>>

    @Query("SELECT * FROM gps_point ORDER BY recordedAt ASC")
    suspend fun getAll(): List<GpsPointEntity>

    @Query("SELECT COUNT(*) FROM gps_point")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(point: GpsPointEntity)

    @Query("UPDATE gps_point SET note = :note WHERE id = :id")
    suspend fun updateNote(id: String, note: String)

    @Query("DELETE FROM gps_point WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM gps_point")
    suspend fun deleteAll()
}

@Dao
interface HazardPointDao {

    @Query("SELECT * FROM hazard_point ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<HazardPointEntity>>

    /**
     * Only the hazards still worth warning about.
     *
     * A separate query rather than filtering in Kotlin: this one is collected for the whole
     * life of the map screen and re-read on every change, while the full list is only
     * opened when someone is managing hazards.
     */
    @Query("SELECT * FROM hazard_point WHERE isActive = 1 ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<HazardPointEntity>>

    @Query("SELECT * FROM hazard_point ORDER BY createdAt ASC")
    suspend fun getAll(): List<HazardPointEntity>

    @Query("SELECT * FROM hazard_point WHERE id = :id")
    suspend fun findById(id: String): HazardPointEntity?

    @Query("SELECT COUNT(*) FROM hazard_point WHERE isActive = 1")
    suspend fun countActive(): Int

    @Upsert
    suspend fun upsert(hazard: HazardPointEntity)

    @Upsert
    suspend fun upsertAll(hazards: List<HazardPointEntity>)

    @Query("UPDATE hazard_point SET isActive = :active, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setActive(id: String, active: Boolean, updatedAt: Long)

    @Query("DELETE FROM hazard_point WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM hazard_point")
    suspend fun deleteAll()
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
