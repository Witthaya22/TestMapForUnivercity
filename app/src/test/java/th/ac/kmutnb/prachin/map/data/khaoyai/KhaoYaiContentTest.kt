package th.ac.kmutnb.prachin.map.data.khaoyai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class KhaoYaiContentTest {

    private val shipped: KhaoYaiContent by lazy {
        val file = sequenceOf(
            File("src/main/assets/data/khaoyai_content.json"),
            File("app/src/main/assets/data/khaoyai_content.json"),
        ).firstOrNull { it.isFile } ?: error("cannot locate khaoyai_content.json")
        KhaoYaiContentParser.parse(file.readText(Charsets.UTF_8))
    }

    @Test
    fun `the shipped sample has the seven trails and both kinds of species`() {
        assertEquals(7, shipped.trails.size)
        assertTrue(shipped.news.isNotEmpty())
        assertTrue(shipped.species.any { it.kind == SpeciesKind.ANIMAL })
        assertTrue(shipped.species.any { it.kind == SpeciesKind.PLANT })
    }

    @Test
    fun `every shipped item is complete enough to show`() {
        shipped.trails.forEach {
            assertTrue(it.id, it.name.isNotBlank() && it.distanceKm > 0 && it.durationMinutes > 0)
            assertTrue(it.id, it.stations.isNotEmpty())
        }
        shipped.news.forEach { assertTrue(it.id, Regex("""\d{4}-\d{2}-\d{2}""").matches(it.date)) }
        shipped.species.forEach { assertTrue(it.id, it.name.isNotBlank() && it.scientificName.isNotBlank()) }
    }

    @Test
    fun `ids are unique across trails, news and species`() {
        // Detail screens are looked up by id alone.
        val ids = shipped.trails.map { it.id } + shipped.news.map { it.id } + shipped.species.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it.contains('/') })
    }

    @Test
    fun `missing fields fall back to defaults instead of failing`() {
        val content = KhaoYaiContentParser.parse("""{"trails":[{"id":"t","name":"x"}],"species":[{"id":"s"}]}""")
        val trail = content.trails.single()
        assertEquals(TrailDifficulty.EASY, trail.difficulty)
        assertTrue(trail.isOpen)
        assertFalse(trail.needsGuide)
        assertTrue(trail.stations.isEmpty())
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
        assertEquals("animal_elephant", filterSpecies(all, null, " ช้าง ").single().id)
        assertTrue(filterSpecies(all, SpeciesKind.PLANT, "ช้าง").isEmpty())
        assertEquals(all.size, filterSpecies(all, null, "").size)
    }

    private fun news(id: String, date: String) = NewsItem(id, "t", "", date, "", "", "")
}
