package th.ac.kmutnb.prachin.map.data.khaoyai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KhaoYaiContentTest {

    private val assetsDir: File = sequenceOf(File("src/main/assets"), File("app/src/main/assets"))
        .firstOrNull { it.isDirectory } ?: error("cannot locate the assets directory")

    private val shipped: KhaoYaiContent by lazy {
        KhaoYaiContentJson.parse(File(assetsDir, "data/khaoyai_content.json").readText(Charsets.UTF_8))
    }

    @Test
    fun `the shipped content has the campus demo first, then the park's seven trails`() {
        assertEquals(8, shipped.trails.size)
        val demo = shipped.trails.first()
        assertTrue(demo.demoUnlocked)
        // Only the demo may be started from anywhere; a real trail that could would let a
        // visitor "walk" Khao Yai from Bangkok.
        assertTrue(shipped.trails.drop(1).none { it.demoUnlocked })
        assertTrue(shipped.trails.all { it.trailhead != null })
        assertTrue(shipped.news.isNotEmpty())
        assertTrue(shipped.species.any { it.kind == SpeciesKind.ANIMAL })
        assertTrue(shipped.species.any { it.kind == SpeciesKind.PLANT })
    }

    @Test
    fun `every shipped item is complete enough to show`() {
        shipped.trails.forEach {
            assertTrue(it.id, it.name.isNotBlank() && it.distanceKm > 0 && it.durationMinutes > 0)
            assertTrue(it.id, it.stations.isNotEmpty() && it.stations.all { s -> s.name.isNotBlank() })
            assertTrue(it.id, it.entryRadiusMeters > 0)
        }
        // The demo exists to show the station pop-ups, so all its stations must have a point.
        assertTrue(shipped.trails.first().stations.all { it.point != null })
        shipped.news.forEach {
            assertTrue(it.id, Regex("""\d{4}-\d{2}-\d{2}""").matches(it.date))
            assertTrue(it.id, it.sourceUrl.startsWith("http"))
        }
        shipped.species.forEach { assertTrue(it.id, it.name.isNotBlank() && it.scientificName.isNotBlank()) }
    }

    @Test
    fun `every bundled photo exists and says who took it`() {
        val credits = KhaoYaiContentJson.parseCredits(File(assetsDir, "photos/credits.json").readText(Charsets.UTF_8))
        val photos = shipped.trails.flatMap { t -> listOf(t.photo) + t.stations.map { it.photo } } +
            shipped.news.map { it.photo } + shipped.species.map { it.photo } + "photos/hero_khaoyai.jpg"
        photos.filter { it.isNotBlank() }.distinct().forEach { photo ->
            assertTrue("missing $photo", File(assetsDir, photo).isFile)
            val credit = credits[photo]
            assertNotNull("no credit for $photo", credit)
            assertTrue(photo, credit!!.author.isNotBlank() && credit.license.isNotBlank())
        }
    }

    @Test
    fun `ids are unique across trails, news and species, and stations within a trail`() {
        // Detail screens are looked up by id alone.
        val ids = shipped.trails.map { it.id } + shipped.news.map { it.id } + shipped.species.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it.contains('/') || it.contains('?') })
        shipped.trails.forEach { t -> assertEquals(t.id, t.stations.size, t.stations.map { it.id }.toSet().size) }
    }

    @Test
    fun `what is written reads back as the same content`() {
        // Admin edits are saved with write() and read with parse() on the next start.
        assertEquals(shipped, KhaoYaiContentJson.parse(KhaoYaiContentJson.write(shipped)))
    }

    @Test
    fun `points are read as GeoJSON lon-lat`() {
        val content = KhaoYaiContentJson.parse(
            """{"trails":[{"id":"t","name":"x","trailhead":[101.37, 14.43],"path":[[101.1,14.1],[101.2,14.2]]}]}""",
        )
        val trail = content.trails.single()
        assertEquals(14.43, trail.trailhead!!.lat, 0.0)
        assertEquals(101.37, trail.trailhead!!.lon, 0.0)
        assertEquals(2, trail.path.size)
    }

    @Test
    fun `missing fields fall back to defaults, and old string stations still load`() {
        val content = KhaoYaiContentJson.parse(
            """{"trails":[{"id":"t","name":"x","stations":["old style"]}],"species":[{"id":"s"}]}""",
        )
        val trail = content.trails.single()
        assertEquals(TrailDifficulty.EASY, trail.difficulty)
        assertTrue(trail.isOpen)
        assertFalse(trail.needsGuide)
        assertFalse(trail.demoUnlocked)
        assertNull(trail.trailhead)
        assertEquals(Trail.DEFAULT_ENTRY_RADIUS_METERS, trail.entryRadiusMeters)
        assertEquals("old style", trail.stations.single().name)
        assertNull(trail.stations.single().point)
        assertEquals(SpeciesKind.ANIMAL, content.species.single().kind)
        assertTrue(content.news.isEmpty())
    }

    @Test
    fun `news come newest first`() {
        val content = KhaoYaiContent(news = listOf(news("a", "2026-01-05"), news("b", "2026-11-01"), news("c", "2025-12-31")))
        assertEquals(listOf("b", "a", "c"), content.newsByDate.map { it.id })
    }

    @Test
    fun `upsert replaces in place, or appends when new`() {
        val list = listOf(news("a", "1"), news("b", "2"))
        assertEquals(listOf("a", "b"), list.upsertBy(news("a", "9")) { it.id }.map { it.id })
        assertEquals("9", list.upsertBy(news("a", "9")) { it.id }.first().date)
        assertEquals(listOf("a", "b", "c"), list.upsertBy(news("c", "3")) { it.id }.map { it.id })
    }

    @Test
    fun `species filter matches kind and either name, ignoring case`() {
        val all = shipped.species
        assertTrue(filterSpecies(all, SpeciesKind.PLANT, "").all { it.kind == SpeciesKind.PLANT })
        assertEquals("animal_elephant", filterSpecies(all, null, "elephas").single().id)
        assertEquals("animal_elephant", filterSpecies(all, null, " ช้างเอเชีย ").single().id)
        assertTrue(filterSpecies(all, SpeciesKind.PLANT, "ช้าง").isEmpty())
        assertEquals(all.size, filterSpecies(all, null, "").size)
    }

    private fun news(id: String, date: String) = NewsItem(id, "t", "", date, "", "", "")
}
