package th.ac.kmutnb.prachin.map.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint

/**
 * Which tone a hazard warning plays.
 *
 * Worth testing on its own because every interesting case is a hazard whose chosen sound
 * is not on this phone - which is the normal state of any hazard file that has been shared
 * with anybody - and the wrong answer there is silence.
 */
class HazardSoundTest {

    private val imported = HazardSound(
        id = "sound_imported",
        name = "เสียงที่อัดเอง",
        isBundled = false,
        fileName = "abc.mp3",
        addedAt = 1_789_889_525_000L,
    )

    private val catalogue = HazardSound.bundled + imported

    private fun hazard(
        severity: HazardSeverity = HazardSeverity.WARNING,
        soundId: String? = null,
    ) = HazardPoint(
        id = "hazard_1",
        type = HazardType.DOG,
        severity = severity,
        point = GeoPoint(lat = 14.1610243, lon = 101.3529617),
        radiusMeters = 15.0,
        description = "",
        soundId = soundId,
        isActive = true,
        gpsAccuracy = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    @Test
    fun `a hazard with no chosen sound follows its severity`() {
        assertEquals(
            BundledHazardSound.CHIME.id,
            resolveHazardSound(hazard(HazardSeverity.CAUTION), catalogue)?.id,
        )
        assertEquals(
            BundledHazardSound.CHIME.id,
            resolveHazardSound(hazard(HazardSeverity.WARNING), catalogue)?.id,
        )
        assertEquals(
            BundledHazardSound.ALERT.id,
            resolveHazardSound(hazard(HazardSeverity.DANGER), catalogue)?.id,
        )
    }

    @Test
    fun `a chosen sound is used`() {
        val hazard = hazard(soundId = imported.id)

        assertEquals(imported, resolveHazardSound(hazard, catalogue))
    }

    @Test
    fun `asking for silence gets silence`() {
        assertNull(resolveHazardSound(hazard(soundId = HAZARD_SOUND_NONE), catalogue))
    }

    @Test
    fun `a sound this phone does not have falls back rather than going quiet`() {
        // The everyday case: the hazard arrived in a file from someone else's survey, and
        // the tone they picked stayed on their phone.
        val fromSomeoneElse = hazard(HazardSeverity.DANGER, soundId = "sound_not_here")

        assertEquals(
            BundledHazardSound.ALERT.id,
            resolveHazardSound(fromSomeoneElse, HazardSound.bundled)?.id,
        )
    }

    @Test
    fun `the bundled tones are always resolvable, even with an empty catalogue`() {
        // A catalogue that has not loaded yet must not silence a warning that is due now.
        assertEquals(
            BundledHazardSound.CHIME.id,
            resolveHazardSound(hazard(), emptyList())?.id,
        )
    }

    @Test
    fun `every bundled tone is in the catalogue exactly once`() {
        val ids = HazardSound.bundled.map { it.id }

        assertEquals(BundledHazardSound.entries.map { it.id }, ids)
        assertEquals(ids.size, ids.toSet().size)
    }
}
