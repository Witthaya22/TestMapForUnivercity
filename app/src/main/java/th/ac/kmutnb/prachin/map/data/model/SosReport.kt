package th.ac.kmutnb.prachin.map.data.model

import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import java.util.Locale

/**
 * The emergency types of the proposal (§2.3.1.3). [needsPlace] types also show the
 * nearest hospital or police station, from `assets/data/emergency_places.json`.
 */
enum class SosCategory(val id: String, val labelRes: Int, val emoji: String, val needsPlace: EmergencyPlaceKind? = null) {
    ACCIDENT("injured", R.string.sos_category_accident, "🩹"),
    LOST("lost", R.string.sos_category_lost, "🧭"),
    WILDLIFE("wildlife", R.string.sos_category_wildlife, "🐘"),
    HOSPITAL("hospital", R.string.sos_category_hospital, "🏥", EmergencyPlaceKind.HOSPITAL),
    POLICE("police", R.string.sos_category_police, "🚓", EmergencyPlaceKind.POLICE),
    OTHER("other", R.string.sos_category_other, "❗");

    companion object {
        // "unwell" was a type before the proposal's list; someone unwell needs a hospital.
        fun fromId(id: String?): SosCategory =
            if (id == "unwell") HOSPITAL else entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/**
 * NEW as sent; then the three steps the officer records (§2.3.2.2): received,
 * in progress, help completed.
 */
enum class SosStatus(val id: String, val labelRes: Int) {
    NEW("new", R.string.sos_status_new),
    ACKNOWLEDGED("acknowledged", R.string.sos_status_acknowledged),
    IN_PROGRESS("in_progress", R.string.sos_status_in_progress),
    RESOLVED("resolved", R.string.sos_status_resolved);

    /** The step an officer moves a call to next; null once it is done. */
    val next: SosStatus? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun fromId(id: String?): SosStatus = entries.firstOrNull { it.id == id } ?: NEW
    }
}

data class SosReport(
    val id: String,
    val createdAt: Long,
    /** The account that sent it, so officers can find the person and their latest position. */
    val userId: String = "",
    val touristId: String = "",
    /** Who was signed in; blank for a visitor who gave no name. */
    val username: String,
    /** Null when the phone had no fix at the moment of sending. */
    val point: GeoPoint?,
    val accuracyMeters: Float?,
    val category: SosCategory,
    val message: String,
    /** The trail being walked, if any, to tell rescuers which way to look. */
    val trailName: String,
    val status: SosStatus = SosStatus.NEW,
    val handledAt: Long? = null,
    val adminNote: String = "",
    /** Seeded examples for the report screens; never shown as a real call. */
    val isDemo: Boolean = false,
    /** Sent to the server - always false until there is one. */
    val synced: Boolean = false,
) {
    /** Opens in Google Maps (or a browser) on any phone, so it can be passed on as text. */
    val mapLink: String?
        get() = point?.let { String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", it.lat, it.lon) }
}

object SosRules {
    /**
     * The text handed to SMS or a chat app: what happened, where, how sure the position is.
     * Plain enough to read aloud to a dispatcher over the phone.
     */
    fun shareText(report: SosReport, categoryLabel: String, header: String, noFix: String): String = buildString {
        appendLine(header)
        appendLine("${report.category.emoji} $categoryLabel")
        if (report.message.isNotBlank()) appendLine(report.message.trim())
        if (report.trailName.isNotBlank()) appendLine(report.trailName)
        val point = report.point
        if (point == null) {
            appendLine(noFix)
        } else {
            val accuracy = report.accuracyMeters?.let { String.format(Locale.US, " (±%.0f m)", it) }.orEmpty()
            appendLine(String.format(Locale.US, "%.6f, %.6f", point.lat, point.lon) + accuracy)
            append(report.mapLink)
        }
    }.trim()

    /** Reports an admin should look at first: new ones, then taken ones, newest first in each. */
    fun triage(reports: List<SosReport>): List<SosReport> =
        reports.sortedWith(compareBy<SosReport> { it.status.ordinal }.thenByDescending { it.createdAt })
}
