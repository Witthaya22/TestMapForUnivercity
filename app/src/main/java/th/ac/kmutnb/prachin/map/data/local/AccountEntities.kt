package th.ac.kmutnb.prachin.map.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.Permission
import th.ac.kmutnb.prachin.map.data.model.TouristPosition
import th.ac.kmutnb.prachin.map.data.model.UserAccount
import th.ac.kmutnb.prachin.map.data.model.UserRole

/**
 * Accounts of tourists, park officers and administrators.
 *
 * shortcut: kept on this phone until the backend exists; the shape matches what a server
 * account table will need, so moving it there is a copy, not a redesign.
 */
@Entity(
    tableName = "user_account",
    indices = [Index(value = ["username"], unique = true), Index(value = ["email"])],
)
data class UserAccountEntity(
    @PrimaryKey val id: String,
    val memberId: String,
    val username: String,
    val passwordHash: String,
    val fullName: String,
    val phone: String,
    val email: String,
    val nationality: String,
    val role: String,
    val permissions: String,
    val createdAt: Long,
    val isActive: Boolean,
    val isDemo: Boolean,
)

@Dao
interface UserAccountDao {
    @Query("SELECT * FROM user_account ORDER BY role, createdAt")
    fun observeAll(): Flow<List<UserAccountEntity>>

    @Query("SELECT * FROM user_account WHERE id = :id")
    fun observe(id: String): Flow<UserAccountEntity?>

    @Query("SELECT * FROM user_account WHERE id = :id")
    suspend fun find(id: String): UserAccountEntity?

    @Query("SELECT * FROM user_account WHERE username = :username COLLATE NOCASE LIMIT 1")
    suspend fun findByUsername(username: String): UserAccountEntity?

    @Query("SELECT * FROM user_account WHERE email = :email COLLATE NOCASE LIMIT 1")
    suspend fun findByEmail(email: String): UserAccountEntity?

    @Query("SELECT memberId FROM user_account WHERE role = :role")
    suspend fun memberIds(role: String): List<String>

    @Query("SELECT COUNT(*) FROM user_account")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: UserAccountEntity)

    @Upsert
    suspend fun upsert(account: UserAccountEntity)

    @Query("UPDATE user_account SET passwordHash = :hash WHERE id = :id")
    suspend fun setPassword(id: String, hash: String)

    @Query("DELETE FROM user_account WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM user_account WHERE isDemo = 1")
    suspend fun deleteDemo()
}

/**
 * Where a tourist was, every half minute or so while walking a trail (§2.3.1.2). The
 * newest row per person is their "latest position"; the rest is the walked history.
 * [synced] is for the backend to come: rows not yet sent stay false.
 */
@Entity(
    tableName = "tourist_position",
    indices = [Index(value = ["userId", "recordedAt"])],
)
data class TouristPositionEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val trailId: String,
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
    val recordedAt: Long,
    val synced: Boolean,
)

@Dao
interface TouristPositionDao {
    @Insert
    suspend fun insert(position: TouristPositionEntity)

    @Insert
    suspend fun insertAll(positions: List<TouristPositionEntity>)

    /** The newest row of every person: their latest known position. */
    @Query(
        "SELECT p.* FROM tourist_position p JOIN (SELECT userId, MAX(recordedAt) AS t FROM tourist_position GROUP BY userId) l " +
            "ON p.userId = l.userId AND p.recordedAt = l.t",
    )
    fun observeLatest(): Flow<List<TouristPositionEntity>>

    @Query("SELECT * FROM tourist_position WHERE userId = :userId ORDER BY recordedAt DESC LIMIT :limit")
    fun observeHistory(userId: String, limit: Int = 500): Flow<List<TouristPositionEntity>>

    @Query("SELECT COUNT(*) FROM tourist_position WHERE synced = 0")
    fun observeUnsynced(): Flow<Int>

    /** Keeps the newest [keep] rows of a person; older history is not worth the space. */
    @Query(
        "DELETE FROM tourist_position WHERE userId = :userId AND id NOT IN " +
            "(SELECT id FROM tourist_position WHERE userId = :userId ORDER BY recordedAt DESC LIMIT :keep)",
    )
    suspend fun prune(userId: String, keep: Int)

    @Query("DELETE FROM tourist_position WHERE userId = :userId")
    suspend fun deleteFor(userId: String)
}

fun UserAccountEntity.toAccount() = UserAccount(
    id = id,
    memberId = memberId,
    username = username,
    fullName = fullName,
    phone = phone,
    email = email,
    nationality = nationality,
    role = UserRole.fromId(role),
    permissions = Permission.fromIds(permissions),
    createdAt = createdAt,
    isActive = isActive,
    isDemo = isDemo,
)

fun TouristPositionEntity.toPosition() = TouristPosition(
    userId = userId,
    trailId = trailId,
    point = GeoPoint(lat = lat, lon = lon),
    accuracyMeters = accuracyMeters,
    recordedAt = recordedAt,
)
