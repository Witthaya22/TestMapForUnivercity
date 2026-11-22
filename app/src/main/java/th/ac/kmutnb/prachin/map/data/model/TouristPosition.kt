package th.ac.kmutnb.prachin.map.data.model

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint

/** One recorded position of a tourist on a trail; see `TouristPositionEntity`. */
data class TouristPosition(
    val userId: String,
    val trailId: String,
    val point: GeoPoint,
    val accuracyMeters: Float,
    val recordedAt: Long,
)

/** A tourist as an officer sees them: who, and where they were last. */
data class TrackedTourist(
    val account: UserAccount,
    val latest: TouristPosition?,
) {
    fun matches(query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return account.fullName.contains(q, ignoreCase = true) ||
            account.memberId.contains(q, ignoreCase = true) ||
            account.username.contains(q, ignoreCase = true) ||
            account.phone.contains(q)
    }
}
