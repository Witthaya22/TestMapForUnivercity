package th.ac.kmutnb.prachin.map.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.local.TouristPositionDao
import th.ac.kmutnb.prachin.map.data.local.TouristPositionEntity
import th.ac.kmutnb.prachin.map.data.local.toPosition
import th.ac.kmutnb.prachin.map.data.model.TouristPosition
import th.ac.kmutnb.prachin.map.data.model.TrackedTourist
import th.ac.kmutnb.prachin.map.data.model.UserRole
import th.ac.kmutnb.prachin.map.location.LocationFix

/**
 * Where tourists are on the trails (§2.3.1.2): recorded on the phone every
 * [MIN_INTERVAL_MS] or [MIN_DISTANCE_METERS] while a trail is walked, kept even with no
 * signal, and marked unsynced for the backend to pick up later.
 *
 * shortcut: officers see these on the same phone only until there is a server.
 */
class TrackingRepository(
    private val dao: TouristPositionDao,
    private val accounts: AccountRepository,
) {
    private var lastSaved: TouristPositionEntity? = null

    /** Every tourist with their newest position, for the officer's search and map. */
    val trackedTourists: Flow<List<TrackedTourist>> =
        combine(accounts.accounts, dao.observeLatest()) { all, latest ->
            val byUser = latest.associateBy { it.userId }
            all.filter { it.role == UserRole.TOURIST }
                .map { TrackedTourist(it, byUser[it.id]?.toPosition()) }
                .sortedByDescending { it.latest?.recordedAt ?: 0L }
        }

    fun history(userId: String): Flow<List<TouristPosition>> =
        dao.observeHistory(userId).map { list -> list.map { it.toPosition() } }

    val unsynced: Flow<Int> = dao.observeUnsynced()

    /**
     * Records [fix] for the signed-in walker when enough time or distance has passed.
     * Coarse fixes are skipped: a position 60 m off is worse than the last good one.
     */
    suspend fun record(userId: String, trailId: String, fix: LocationFix, now: Long = System.currentTimeMillis()) {
        if (fix.accuracyMeters > MAX_ACCURACY_METERS) return
        val previous = lastSaved?.takeIf { it.userId == userId && it.trailId == trailId }
        if (previous != null) {
            val moved = GeoUtils.haversineMeters(fix.point, th.ac.kmutnb.prachin.map.core.geo.GeoPoint(previous.lat, previous.lon))
            if (now - previous.recordedAt < MIN_INTERVAL_MS && moved < MIN_DISTANCE_METERS) return
        }
        val row = TouristPositionEntity(
            userId = userId,
            trailId = trailId,
            lat = fix.point.lat,
            lon = fix.point.lon,
            accuracyMeters = fix.accuracyMeters,
            recordedAt = now,
            synced = false,
        )
        dao.insert(row)
        lastSaved = row
        dao.prune(userId, KEEP_PER_USER)
    }

    suspend fun insertDemo(rows: List<TouristPositionEntity>) = dao.insertAll(rows)

    suspend fun deleteFor(userId: String) = dao.deleteFor(userId)

    private companion object {
        const val MIN_INTERVAL_MS = 30_000L
        const val MIN_DISTANCE_METERS = 25.0
        const val MAX_ACCURACY_METERS = 40f
        const val KEEP_PER_USER = 2_000
    }
}
