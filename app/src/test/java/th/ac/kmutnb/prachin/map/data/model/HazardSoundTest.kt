package th.ac.kmutnb.prachin.map.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
        origin = HazardSoundOrigin.IMPORTED,
        fileName = "abc.mp3",
        addedAt = 1_789_889_525_000L,
    )

    private val shipped = requireNotNull(HazardSound.fromAssetFileName("siren.mp3"))

    private val catalogue = HazardSound.bundled + shipped + imported

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

    // ----------------------------------------------------------------------------------
    // Audio dropped into assets/sounds/ before the build
    // ----------------------------------------------------------------------------------

    @Test
    fun `a dropped-in file becomes a sound named after itself`() {
        val sound = requireNotNull(HazardSound.fromAssetFileName("เสียงออด.mp3"))

        assertEquals("asset:เสียงออด.mp3", sound.id)
        assertEquals("เสียงออด", sound.name)
        assertEquals(HazardSoundOrigin.ASSET, sound.origin)
        // Part of the build, so nobody can delete it from inside the app.
        assertFalse(sound.isRemovable)
    }

    @Test
    fun `a file the player could not open is ignored rather than offered`() {
        // Android compresses these into the APK, and openFd cannot read a compressed
        // entry - so offering them would mean a tone that fails at the kerb.
        assertNull(HazardSound.fromAssetFileName("notes.txt"))
        assertNull(HazardSound.fromAssetFileName("README.md"))
        assertNull(HazardSound.fromAssetFileName("noextension"))
    }

    @Test
    fun `extensions are matched whatever case they were saved in`() {
        assertNotNull(HazardSound.fromAssetFileName("Siren.MP3"))
    }

    @Test
    fun `a shipped default takes over the severity it is named for`() {
        val shippedDefault = requireNotNull(HazardSound.fromAssetFileName("default_danger.mp3"))
        val catalogue = HazardSound.bundled + shippedDefault

        // Nothing to configure: the file's name is the whole mechanism.
        assertEquals(shippedDefault, resolveHazardSound(hazard(HazardSeverity.DANGER), catalogue))
        // ...and it only takes over the one severity it names.
        assertEquals(
            BundledHazardSound.CHIME.id,
            resolveHazardSound(hazard(HazardSeverity.WARNING), catalogue)?.id,
        )
    }

    @Test
    fun `a hazard that chose a sound is not overridden by a shipped default`() {
        val shippedDefault = requireNotNull(HazardSound.fromAssetFileName("default_danger.mp3"))
        val catalogue = HazardSound.bundled + shippedDefault + imported
        val chosen = hazard(HazardSeverity.DANGER, soundId = imported.id)

        assertEquals(imported, resolveHazardSound(chosen, catalogue))
    }

    @Test
    fun `a shipped sound is found by the id a shared hazard file carries`() {
        // What makes assets/sounds/ worth having: the id means the same thing on every
        // phone running this build, so a shared hazards.geojson keeps its sounds.
        val fromAnotherPhone = hazard(soundId = "asset:siren.mp3")

        assertEquals(shipped, resolveHazardSound(fromAnotherPhone, catalogue))
    }
}
