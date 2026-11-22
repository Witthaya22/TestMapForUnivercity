package th.ac.kmutnb.prachin.map.data.khaoyai

import java.util.UUID

/**
 * Everything the Khao Yai app shows apart from the map: nature trails, news, animals and
 * plants. Seeded from `assets/data/khaoyai_content.json` and edited from the admin screen.
 */
data class KhaoYaiContent(
    val trails: List<Trail> = emptyList(),
    val news: List<NewsItem> = emptyList(),
    val species: List<Species> = emptyList(),
) {
    /** Newest first; ISO dates sort correctly as text. */
    val newsByDate: List<NewsItem> get() = news.sortedByDescending { it.date }

    fun trail(id: String): Trail? = trails.firstOrNull { it.id == id }
    fun newsItem(id: String): NewsItem? = news.firstOrNull { it.id == id }
    fun speciesItem(id: String): Species? = species.firstOrNull { it.id == id }
}

enum class TrailDifficulty(val id: String) {
    EASY("easy"),
    MODERATE("moderate"),
    HARD("hard");

    companion object {
        fun fromId(id: String?): TrailDifficulty = entries.firstOrNull { it.id == id } ?: EASY
    }
}

/** A nature study trail, walked on foot. */
data class Trail(
    val id: String,
    val name: String,
    val emoji: String,
    val distanceKm: Double,
    val durationMinutes: Int,
    val difficulty: TrailDifficulty,
    val startPoint: String,
    val summary: String,
    val description: String,
    /** Interpretation stations along the way, in walking order. */
    val stations: List<String>,
    /** Some trails may only be walked with a park ranger. */
    val needsGuide: Boolean,
    val isOpen: Boolean,
)

data class NewsItem(
    val id: String,
    val title: String,
    val emoji: String,
    /** `yyyy-MM-dd`. */
    val date: String,
    val category: String,
    val summary: String,
    val body: String,
)

enum class SpeciesKind(val id: String) {
    ANIMAL("animal"),
    PLANT("plant");

    companion object {
        fun fromId(id: String?): SpeciesKind = entries.firstOrNull { it.id == id } ?: ANIMAL
    }
}

data class Species(
    val id: String,
    val kind: SpeciesKind,
    val name: String,
    val scientificName: String,
    val emoji: String,
    val conservationStatus: String,
    val habitat: String,
    val description: String,
    /** What a visitor should do on meeting it. */
    val tip: String,
)

/** Replaces the item with the same id where it stands, or appends it when it is new. */
fun <T> List<T>.upsertBy(item: T, id: (T) -> String): List<T> {
    val key = id(item)
    val index = indexOfFirst { id(it) == key }
    return if (index < 0) this + item else toMutableList().also { it[index] = item }
}

fun newContentId(prefix: String): String = "${prefix}_${UUID.randomUUID()}"
