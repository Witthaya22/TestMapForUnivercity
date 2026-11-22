package th.ac.kmutnb.prachin.map.data.khaoyai

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import java.util.UUID

/**
 * Everything the Khao Yai app shows apart from the map: nature trails, news, animals and
 * plants. Seeded from `assets/data/khaoyai_content.json`, edited from the admin screen and
 * saved to the app's own storage from then on (see `KhaoYaiContentRepository`).
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

/** What a station is, for its label and marker (§2.3.1.5: attraction, viewpoint, waterfall, rest stop). */
enum class StationKind(val id: String, val emoji: String) {
    NATURE("nature", "🌿"),
    ATTRACTION("attraction", "📍"),
    VIEWPOINT("viewpoint", "🔭"),
    WATERFALL("waterfall", "💦"),
    REST("rest", "⛺");

    companion object {
        fun fromId(id: String?): StationKind = entries.firstOrNull { it.id == id } ?: NATURE
    }
}

/**
 * A point of interest along a trail - an old tree, an odd rock, a salt lick. Walking into
 * [radiusMeters] of [point] brings its card up on the map (see `StationMonitor`).
 * A station without a [point] is listed on the trail page but never pops up.
 */
data class TrailStation(
    val id: String,
    val name: String,
    val emoji: String,
    val description: String,
    val point: GeoPoint?,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    /** A photo reference, see [PhotoRef]; blank for none. */
    val photo: String = "",
    val kind: StationKind = StationKind.NATURE,
) {
    companion object {
        /** About a fix's error and a few paces: close enough to be looking at it. */
        const val DEFAULT_RADIUS_METERS = 25
    }
}

/** A nature study trail, walked on foot. */
data class Trail(
    val id: String,
    val name: String,
    val emoji: String,
    val photo: String,
    val distanceKm: Double,
    val durationMinutes: Int,
    val difficulty: TrailDifficulty,
    /** Where the walk starts, in words, e.g. "the suspension bridge behind the visitor centre". */
    val startPoint: String,
    val endPoint: String,
    val summary: String,
    val description: String,
    val stations: List<TrailStation>,
    /** Some trails may only be walked with a park ranger. */
    val needsGuide: Boolean,
    val isOpen: Boolean,
    /** Opening hours or seasonal notes, shown as they are written. */
    val openNote: String,
    /** Where the walk starts on the ground; null when nobody has surveyed it yet. */
    val trailhead: GeoPoint?,
    /** The trail itself, when its line is known - from OpenStreetMap or walked in. */
    val path: List<GeoPoint>,
    /** How close to the trail one has to be before it can be started. */
    val entryRadiusMeters: Int,
    /**
     * shortcut: lets this trail be started from anywhere, so the walking screens can be
     * shown before anyone stands at a real trailhead. Turn it off for real trails.
     */
    val demoUnlocked: Boolean,
    val sourceUrl: String,
    /** Earliest start, `HH:mm`; blank for no limit. The park's trails start 08:00. */
    val startFrom: String = "",
    /** Latest start, `HH:mm`; blank for no limit. 14:00 for most, 10:00 for trail 6. */
    val startUntil: String = "",
    /** Yearly closure, `MM-dd` to `MM-dd`; blank for none. Trail 6: 07-01 to 08-31. */
    val seasonClosedFrom: String = "",
    val seasonClosedUntil: String = "",
    /**
     * How far off the line a fix may be and still count as on the trail; the dot is held
     * on the line within it. Set per trail by an officer to absorb the GPS error found
     * when the line was surveyed (§2.3.2.3 "ชดเชยระยะทางผิดพลาด").
     */
    val gpsToleranceMeters: Int = DEFAULT_GPS_TOLERANCE_METERS,
) {
    companion object {
        const val DEFAULT_ENTRY_RADIUS_METERS = 1_000
        const val DEFAULT_GPS_TOLERANCE_METERS = 10
    }
}

data class NewsItem(
    val id: String,
    val title: String,
    val emoji: String,
    /** `yyyy-MM-dd`. */
    val date: String,
    val category: String,
    val summary: String,
    val body: String,
    val photo: String = "",
    /** Who published it, e.g. the park or the national parks department. */
    val sourceName: String = "",
    val sourceUrl: String = "",
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
    val photo: String = "",
)

/**
 * Where a photo lives. Bundled photos are asset paths (`photos/elephant.jpg`); photos an
 * admin picked are copied into app storage and written `file:<path under filesDir>`.
 */
object PhotoRef {
    const val FILE_PREFIX = "file:"

    fun isFile(ref: String): Boolean = ref.startsWith(FILE_PREFIX)
    fun filePath(ref: String): String = ref.removePrefix(FILE_PREFIX)
    fun ofFile(relativePath: String): String = FILE_PREFIX + relativePath
}

/** Who took a bundled photo and under what licence; printed under it. */
data class PhotoCredit(val author: String, val license: String, val source: String)

/** Species of [kind] (all kinds when null) whose Thai or scientific name contains [query]. */
fun filterSpecies(species: List<Species>, kind: SpeciesKind?, query: String): List<Species> {
    val q = query.trim()
    return species.filter { s ->
        (kind == null || s.kind == kind) &&
            (q.isEmpty() || s.name.contains(q, ignoreCase = true) || s.scientificName.contains(q, ignoreCase = true))
    }
}

/** Replaces the item with the same id where it stands, or appends it when it is new. */
fun <T> List<T>.upsertBy(item: T, id: (T) -> String): List<T> {
    val key = id(item)
    val index = indexOfFirst { id(it) == key }
    return if (index < 0) this + item else toMutableList().also { it[index] = item }
}

fun newContentId(prefix: String): String = "${prefix}_${UUID.randomUUID()}"
