package th.ac.kmutnb.prachin.map.data.model

import java.util.Calendar
import java.util.TimeZone

/** How the emergency report groups calls over time (§2.3.2.5 "ราย วัน, เดือน, ปี"). */
enum class ReportPeriod { DAY, MONTH, YEAR }

/** One bar of a report: a day, a month or a year, and how many calls fell in it. */
data class PeriodCount(val year: Int, val month: Int, val day: Int, val count: Int)

/**
 * The field summary and the report of §2.3.2.4–2.3.2.5, computed from what is on the
 * phone. Pure; [timeZone] is passed so tests do not depend on the machine's zone.
 */
object Reports {

    fun byCategory(reports: List<SosReport>): Map<SosCategory, Int> =
        SosCategory.entries.associateWith { c -> reports.count { it.category == c } }

    fun byStatus(reports: List<SosReport>): Map<SosStatus, Int> =
        SosStatus.entries.associateWith { s -> reports.count { it.status == s } }

    /** The category called most often; null when there are no calls. */
    fun mostFrequent(reports: List<SosReport>): Pair<SosCategory, Int>? =
        byCategory(reports).entries.filter { it.value > 0 }.maxByOrNull { it.value }?.let { it.key to it.value }

    /**
     * The last [buckets] days, months or years ending at [now], oldest first, with the
     * number of calls in each - empty periods included, so the chart has no gaps.
     */
    fun timeline(
        reports: List<SosReport>,
        period: ReportPeriod,
        now: Long,
        buckets: Int = defaultBuckets(period),
        timeZone: TimeZone = TimeZone.getDefault(),
    ): List<PeriodCount> {
        val keys = (buckets - 1 downTo 0).map { back ->
            Calendar.getInstance(timeZone).apply {
                timeInMillis = now
                when (period) {
                    ReportPeriod.DAY -> add(Calendar.DAY_OF_MONTH, -back)
                    ReportPeriod.MONTH -> { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -back) }
                    ReportPeriod.YEAR -> add(Calendar.YEAR, -back)
                }
            }.let { key(it, period) }
        }
        val counts = reports.groupingBy {
            key(Calendar.getInstance(timeZone).apply { timeInMillis = it.createdAt }, period)
        }.eachCount()
        return keys.map { k -> PeriodCount(k.first, k.second, k.third, counts[k] ?: 0) }
    }

    fun defaultBuckets(period: ReportPeriod): Int = when (period) {
        ReportPeriod.DAY -> 14
        ReportPeriod.MONTH -> 12
        ReportPeriod.YEAR -> 5
    }

    /** Calls made on the same calendar day as [now]. */
    fun today(reports: List<SosReport>, now: Long, timeZone: TimeZone = TimeZone.getDefault()): Int {
        val k = key(Calendar.getInstance(timeZone).apply { timeInMillis = now }, ReportPeriod.DAY)
        return reports.count { key(Calendar.getInstance(timeZone).apply { timeInMillis = it.createdAt }, ReportPeriod.DAY) == k }
    }

    fun usersByRole(accounts: List<UserAccount>): Map<UserRole, Int> =
        UserRole.entries.associateWith { r -> accounts.count { it.role == r } }

    /** Tourists whose last position is newer than [withinMs]: on a trail now, as far as the phone knows. */
    fun onTrailNow(tourists: List<TrackedTourist>, now: Long, withinMs: Long = 3 * 60 * 60 * 1000L): Int =
        tourists.count { t -> t.latest?.let { now - it.recordedAt <= withinMs } == true }

    /** CSV of the calls, for passing on to whoever writes the park's reports. */
    fun csv(reports: List<SosReport>, label: (SosCategory) -> String, statusLabel: (SosStatus) -> String): String =
        buildString {
            appendLine("id,created_at_ms,tourist_id,name,category,status,lat,lon,accuracy_m,trail,message")
            reports.forEach { r ->
                appendLine(
                    listOf(
                        r.id, r.createdAt.toString(), r.touristId, r.username, label(r.category), statusLabel(r.status),
                        r.point?.lat?.toString().orEmpty(), r.point?.lon?.toString().orEmpty(),
                        r.accuracyMeters?.toString().orEmpty(), r.trailName, r.message,
                    ).joinToString(",") { field -> "\"" + field.replace("\"", "\"\"") + "\"" },
                )
            }
        }

    private fun key(c: Calendar, period: ReportPeriod): Triple<Int, Int, Int> = when (period) {
        ReportPeriod.DAY -> Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        ReportPeriod.MONTH -> Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, 0)
        ReportPeriod.YEAR -> Triple(c.get(Calendar.YEAR), 0, 0)
    }
}
