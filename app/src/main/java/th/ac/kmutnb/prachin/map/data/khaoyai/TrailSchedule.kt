package th.ac.kmutnb.prachin.map.data.khaoyai

import java.util.Calendar

/** Whether a trail may be started right now, by the clock and the calendar. */
enum class TrailOpenState {
    /** Within today's start window. */
    OPEN_NOW,

    /** Too early: the start window has not opened yet today. */
    NOT_YET,

    /** Too late to start today. */
    ENDED_TODAY,

    /** Inside the trail's yearly closure, e.g. trail 6 from 1 July to 31 August. */
    SEASON_CLOSED,

    /** Closed by the park until further notice ([Trail.isOpen] false). */
    CLOSED;

    val canStart: Boolean get() = this == OPEN_NOW
}

/**
 * The opening rules from the park's trail page (§2.3.1.2 of the proposal): a daily
 * window in which a walk may begin, and an optional closed season. Pure: the time is
 * passed in, so the rules are tested at any hour of any day.
 */
object TrailSchedule {

    fun state(trail: Trail, month: Int, day: Int, minuteOfDay: Int): TrailOpenState {
        if (!trail.isOpen) return TrailOpenState.CLOSED
        if (inSeasonClosure(trail, month * 100 + day)) return TrailOpenState.SEASON_CLOSED
        val from = minutes(trail.startFrom)
        val until = minutes(trail.startUntil)
        if (from != null && minuteOfDay < from) return TrailOpenState.NOT_YET
        if (until != null && minuteOfDay > until) return TrailOpenState.ENDED_TODAY
        return TrailOpenState.OPEN_NOW
    }

    fun stateNow(trail: Trail, now: Calendar = Calendar.getInstance()): TrailOpenState = state(
        trail,
        month = now.get(Calendar.MONTH) + 1,
        day = now.get(Calendar.DAY_OF_MONTH),
        minuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE),
    )

    /** `08:00` → 480; blank or unreadable → null (no limit). */
    fun minutes(hhmm: String): Int? {
        val parts = hhmm.trim().split(':', '.')
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
        val m = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
        return h * 60 + m
    }

    /** `07-01` → 701; blank or unreadable → null. */
    fun monthDay(mmdd: String): Int? {
        val parts = mmdd.trim().split('-', '/')
        if (parts.size != 2) return null
        val m = parts[0].toIntOrNull()?.takeIf { it in 1..12 } ?: return null
        val d = parts[1].toIntOrNull()?.takeIf { it in 1..31 } ?: return null
        return m * 100 + d
    }

    /** Handles a closure that runs over New Year, e.g. 11-15 to 02-15. */
    private fun inSeasonClosure(trail: Trail, today: Int): Boolean {
        val from = monthDay(trail.seasonClosedFrom) ?: return false
        val until = monthDay(trail.seasonClosedUntil) ?: return false
        return if (from <= until) today in from..until else today >= from || today <= until
    }
}
