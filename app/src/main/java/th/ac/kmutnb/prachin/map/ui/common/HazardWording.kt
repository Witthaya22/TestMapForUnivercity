package th.ac.kmutnb.prachin.map.ui.common

import android.content.Context
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.model.HazardPoint
import th.ac.kmutnb.prachin.map.data.model.HazardSeverity
import th.ac.kmutnb.prachin.map.navigation.HazardAlert
import kotlin.math.roundToInt

/**
 * Turns a hazard warning into Thai, for the screen and for the voice.
 *
 * Kept in the UI layer, like the rest of [Messages], so that [HazardAlert] and the monitor
 * behind it stay free of resources and can be unit tested. The spoken and the written forms
 * live side by side here on purpose: they have to agree, and the way they drift apart is
 * one of them being edited alone.
 */
object HazardWording {

    /**
     * What the hazard is, as one phrase: the type, plus the marker's own words when they
     * added any.
     *
     * The description wins the tail of the sentence because "สุนัข" tells the walker to
     * look up, while "เฝ้ารถอยู่ 3 ตัว หน้าโรงอาหาร" tells them where to look.
     */
    fun subject(context: Context, hazard: HazardPoint): String {
        val type = context.getString(hazard.type.labelRes)
        val description = hazard.description.trim()
        return if (description.isEmpty()) type else "$type - $description"
    }

    /** The banner line: what it is and how far off. */
    fun bannerText(context: Context, alert: HazardAlert): String {
        val subject = subject(context, alert.hazard)
        val metres = alert.distanceMeters.roundToInt()
        return if (metres <= AT_THE_SPOT_METERS) {
            context.getString(R.string.hazard_alert_here, subject)
        } else {
            context.getString(R.string.hazard_alert_ahead, subject, metres)
        }
    }

    /**
     * The sentence the phone speaks.
     *
     * Deliberately not the banner text: read aloud, a distance rounded to the metre sounds
     * like precision the fix does not have, and by the time the sentence finishes the
     * walker has moved. Distances are rounded to five metres, and a repeat drops the number
     * altogether - someone who is still inside a hazard does not need it measured.
     */
    fun spokenText(context: Context, alert: HazardAlert): String {
        val subject = subject(context, alert.hazard)
        if (alert.isRepeat) {
            return context.getString(R.string.hazard_voice_repeat, subject)
        }
        val metres = roundToFive(alert.distanceMeters)
        val template = if (alert.hazard.severity == HazardSeverity.DANGER) {
            R.string.hazard_voice_danger_ahead
        } else {
            R.string.hazard_voice_ahead
        }
        return context.getString(template, subject, metres)
    }

    /** A real danger cuts off whatever is being said; a caution waits its turn. */
    fun interrupts(alert: HazardAlert): Boolean =
        alert.hazard.severity == HazardSeverity.DANGER && !alert.isRepeat

    private fun roundToFive(meters: Double): Int =
        ((meters / 5.0).roundToInt() * 5).coerceAtLeast(5)

    /** Closer than this and "ahead" is the wrong word; the walker is in it. */
    private const val AT_THE_SPOT_METERS = 5
}
