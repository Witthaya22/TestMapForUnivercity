package th.ac.kmutnb.prachin.map.navigation

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.khaoyai.TrailStation

/**
 * Decides when a trail's interpretation station should pop up: once, the first time the
 * walker comes within its radius on this walk.
 *
 * Unlike a hazard there is nothing to warn about twice - a story read at the old fig tree
 * is not news on the way back - so a station never pops again until another walk starts
 * ([reset], or a different trail). A tap on its marker still opens it any time.
 *
 * Pure Kotlin, like `HazardMonitor`, so the rules are unit-tested without a phone.
 */
class StationMonitor {

    private var trailId: String? = null
    private val shown = mutableSetOf<String>()

    fun reset() {
        trailId = null
        shown.clear()
    }

    /** Stations already reached on this walk, for working out which comes next. */
    val visited: Set<String> get() = shown.toSet()

    /**
     * @return the stations this fix has just walked into, nearest first; usually none.
     * A fix worse than [MAX_ACCURACY_METERS] is ignored: with an error that wide the
     * walker could be at the next station along, and the wrong story is worse than a late one.
     */
    fun onPosition(
        trailId: String,
        stations: List<TrailStation>,
        here: GeoPoint,
        accuracyMeters: Float,
    ): List<TrailStation> {
        if (trailId != this.trailId) {
            reset()
            this.trailId = trailId
        }
        if (accuracyMeters > MAX_ACCURACY_METERS) return emptyList()
        val entered = stations.mapNotNull { station ->
            val point = station.point ?: return@mapNotNull null
            if (station.id in shown) return@mapNotNull null
            val distance = GeoUtils.haversineMeters(here, point)
            if (distance <= station.radiusMeters) station to distance else null
        }.sortedBy { it.second }.map { it.first }
        entered.forEach { shown += it.id }
        return entered
    }

    companion object {
        const val MAX_ACCURACY_METERS = 30f
    }
}
