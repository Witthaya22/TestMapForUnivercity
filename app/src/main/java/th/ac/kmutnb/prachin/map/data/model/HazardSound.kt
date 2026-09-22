package th.ac.kmutnb.prachin.map.data.model

/**
 * The tones that ship with the app.
 *
 * Two of them, with different jobs rather than different decorations: one that says "look
 * up" and one that says "stop". Adding a third is cheap, but every extra choice is one the
 * person marking a hazard has to make while standing next to it, so the list stays short
 * for the same reason [HazardType] does.
 *
 * `id` is what goes in the database and in the exported file, so it has to survive the
 * enum being renamed.
 */
enum class BundledHazardSound(val id: String, val fileName: String) {
    /** A soft two-note rise. Noticed without being alarming. */
    CHIME("bundled:chime", "hazard_chime"),

    /** Three short pulses above traffic noise. For something to stop for. */
    ALERT("bundled:alert", "hazard_alert"),
    ;

    companion object {
        fun fromId(id: String?): BundledHazardSound? = entries.firstOrNull { it.id == id }
    }
}

/**
 * A sound that can be played ahead of a hazard warning.
 *
 * One flat type for both the bundled tones and the files the user imported, because
 * everything downstream - the picker, the player, the stored id - treats them the same.
 * [isBundled] decides where [fileName] points: a raw resource, or a file the app copied
 * into its own storage when it was imported.
 */
data class HazardSound(
    val id: String,
    /**
     * What to call it in the picker. Empty for a bundled tone, whose name is a Thai string
     * resource rather than stored text - see `ui/common/Messages.kt`.
     */
    val name: String,
    val isBundled: Boolean,
    val fileName: String,
    val addedAt: Long,
) {
    companion object {
        /** The bundled tones, in picker order. Always available, on every device. */
        val bundled: List<HazardSound> = BundledHazardSound.entries.map { tone ->
            HazardSound(
                id = tone.id,
                name = "",
                isBundled = true,
                fileName = tone.fileName,
                addedAt = 0L,
            )
        }
    }
}

/**
 * Which sound a hazard uses, when the person marking it did not choose one.
 *
 * Tied to severity rather than being one tone for everything, so that the sound carries
 * the same information the warning distance does: a walker who has heard both learns which
 * one means "step around it" and which means "stop", and gets that in the third of a
 * second before the sentence starts.
 */
val HazardSeverity.defaultSound: BundledHazardSound
    get() = when (this) {
        HazardSeverity.CAUTION -> BundledHazardSound.CHIME
        HazardSeverity.WARNING -> BundledHazardSound.CHIME
        HazardSeverity.DANGER -> BundledHazardSound.ALERT
    }

/**
 * The id [HazardPoint.soundId] carries when the hazard is meant to be spoken without a
 * tone in front of it.
 *
 * Needed as a real value rather than as null, because null already means "whatever suits
 * the severity" - which is what every hazard marked before sounds existed means, and what
 * someone who never opened the sound picker meant.
 */
const val HAZARD_SOUND_NONE = "none"

/**
 * Picks the sound to play for [hazard], or null to play none.
 *
 * [available] is the whole catalogue: the bundled tones plus whatever has been imported on
 * this device.
 *
 * An id that is not in the catalogue falls back to the severity's default instead of going
 * silent. That is the normal case for a shared file, not an error - hazards travel between
 * phones as `hazards.geojson`, and the tone a surveyor imported onto their own phone does
 * not travel with it. Dropping the sound would be defensible; dropping it quietly on the
 * hazards someone bothered to customise is the wrong half to lose.
 */
fun resolveHazardSound(hazard: HazardPoint, available: List<HazardSound>): HazardSound? {
    if (hazard.soundId == HAZARD_SOUND_NONE) return null
    hazard.soundId?.let { id ->
        available.firstOrNull { it.id == id }?.let { return it }
    }
    val fallback = hazard.severity.defaultSound.id
    return available.firstOrNull { it.id == fallback }
        ?: HazardSound.bundled.firstOrNull { it.id == fallback }
}
