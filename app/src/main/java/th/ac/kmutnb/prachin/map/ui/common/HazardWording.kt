package th.ac.kmutnb.prachin.map.ui.common

import android.content.Context
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.model.HazardPoint
import th.ac.kmutnb.prachin.map.data.model.HazardSeverity
import th.ac.kmutnb.prachin.map.data.model.HazardType
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
     * What the phone says out loud: a short phrase, said [SPOKEN_REPEATS] times.
     *
     * Deliberately not the banner text. A sentence has to be listened to from the start to
     * mean anything, and a walker beside a road hears the middle of it - "…ข้างหน้า 20
     * เมตร" on its own is no warning at all. A phrase short enough to land whole, repeated,
     * is understood however much of it you catch, and the repetition is what carries over a
     * passing engine.
     *
     * The distance is gone from the voice for the same reason. It was rounded to five
     * metres to avoid sounding more precise than the fix, and by the time it was spoken the
     * walker had moved past it anyway; the banner still shows it to the metre for anyone
     * who looks.
     *
     * The marker's own description stays on the banner and out of the voice: "หมา 3 ตัว
     * เฝ้ารถอยู่หน้าโรงอาหาร" is worth reading and far too long to hear three times.
     */
    fun spokenText(context: Context, alert: HazardAlert): String =
        spokenPhrase(context, alert.hazard.type)

    /** The phrase for a hazard type, repeated, exactly as a warning would say it. */
    fun spokenPhrase(context: Context, type: HazardType): String {
        val phrase = context.getString(type.voiceLabelRes)
        return List(SPOKEN_REPEATS) { phrase }.joinToString(SPOKEN_SEPARATOR)
    }

    /** A real danger cuts off whatever is being said; a caution waits its turn. */
    fun interrupts(alert: HazardAlert): Boolean =
        alert.hazard.severity == HazardSeverity.DANGER && !alert.isRepeat

    /**
     * Three, because two can be missed as a stutter and four is long enough that the
     * walker has reached the hazard before it finishes.
     */
    const val SPOKEN_REPEATS = 3

    /** A comma, so the engine puts a beat between them instead of running them together. */
    private const val SPOKEN_SEPARATOR = ", "

    /** Closer than this and "ahead" is the wrong word; the walker is in it. */
    private const val AT_THE_SPOT_METERS = 5
}
