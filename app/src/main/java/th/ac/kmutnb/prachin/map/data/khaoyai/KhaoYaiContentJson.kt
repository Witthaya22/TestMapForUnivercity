package th.ac.kmutnb.prachin.map.data.khaoyai

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils

/**
 * Reads and writes `khaoyai_content.json` by hand rather than through Gson reflection, so
 * release shrinking cannot strip the model classes and no keep rules are needed for them.
 *
 * A missing field becomes an empty value rather than an error: the bundled file is edited
 * by hand. Points are GeoJSON `[lon, lat]`, converted only through [GeoUtils].
 */
object KhaoYaiContentJson {

    fun parse(json: String): KhaoYaiContent {
        val root = JsonParser.parseString(json).asJsonObject
        return KhaoYaiContent(
            trails = root.objects("trails").map(::trail),
            news = root.objects("news").map(::news),
            species = root.objects("species").map(::species),
        )
    }

    fun write(content: KhaoYaiContent): String {
        val root = JsonObject().apply {
            add("trails", JsonArray().also { a -> content.trails.forEach { a.add(trail(it)) } })
            add("news", JsonArray().also { a -> content.news.forEach { a.add(news(it)) } })
            add("species", JsonArray().also { a -> content.species.forEach { a.add(species(it)) } })
        }
        return GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root)
    }

    /** `photos/credits.json` as `tools/fetch_photos.py` writes it, keyed by asset path. */
    fun parseCredits(json: String): Map<String, PhotoCredit> {
        val root = JsonParser.parseString(json).asJsonObject
        return root.entrySet().mapNotNull { (name, value) ->
            val o = value as? JsonObject ?: return@mapNotNull null
            "photos/$name.jpg" to PhotoCredit(o.text("author"), o.text("license"), o.text("source"))
        }.toMap()
    }

    // ----------------------------------------------------------------------------------

    private fun trail(o: JsonObject) = Trail(
        id = o.text("id"),
        name = o.text("name"),
        emoji = o.text("emoji"),
        photo = o.text("photo"),
        distanceKm = o.number("distanceKm"),
        durationMinutes = o.number("durationMinutes").toInt(),
        difficulty = TrailDifficulty.fromId(o.text("difficulty")),
        startPoint = o.text("startPoint"),
        endPoint = o.text("endPoint"),
        summary = o.text("summary"),
        description = o.text("description"),
        stations = o.array("stations").mapIndexedNotNull { i, e -> station(e, i) },
        needsGuide = o.flag("needsGuide", default = false),
        isOpen = o.flag("isOpen", default = true),
        openNote = o.text("openNote"),
        trailhead = o.point("trailhead"),
        path = o.array("path").mapNotNull(::point),
        entryRadiusMeters = o.number("entryRadiusMeters").toInt()
            .takeIf { it > 0 } ?: Trail.DEFAULT_ENTRY_RADIUS_METERS,
        demoUnlocked = o.flag("demoUnlocked", default = false),
        sourceUrl = o.text("sourceUrl"),
        startFrom = o.text("startFrom"),
        startUntil = o.text("startUntil"),
        seasonClosedFrom = o.text("seasonClosedFrom"),
        seasonClosedUntil = o.text("seasonClosedUntil"),
        gpsToleranceMeters = o.number("gpsToleranceMeters").toInt()
            .takeIf { it > 0 } ?: Trail.DEFAULT_GPS_TOLERANCE_METERS,
    )

    private fun trail(t: Trail) = JsonObject().apply {
        addProperty("id", t.id)
        addProperty("name", t.name)
        addProperty("emoji", t.emoji)
        addProperty("photo", t.photo)
        addProperty("distanceKm", t.distanceKm)
        addProperty("durationMinutes", t.durationMinutes)
        addProperty("difficulty", t.difficulty.id)
        addProperty("startPoint", t.startPoint)
        addProperty("endPoint", t.endPoint)
        addProperty("summary", t.summary)
        addProperty("description", t.description)
        add("stations", JsonArray().also { a -> t.stations.forEach { a.add(station(it)) } })
        addProperty("needsGuide", t.needsGuide)
        addProperty("isOpen", t.isOpen)
        addProperty("openNote", t.openNote)
        t.trailhead?.let { add("trailhead", point(it)) }
        add("path", JsonArray().also { a -> t.path.forEach { a.add(point(it)) } })
        addProperty("entryRadiusMeters", t.entryRadiusMeters)
        addProperty("demoUnlocked", t.demoUnlocked)
        addProperty("sourceUrl", t.sourceUrl)
        addProperty("startFrom", t.startFrom)
        addProperty("startUntil", t.startUntil)
        addProperty("seasonClosedFrom", t.seasonClosedFrom)
        addProperty("seasonClosedUntil", t.seasonClosedUntil)
        addProperty("gpsToleranceMeters", t.gpsToleranceMeters)
    }

    /** A bare string is an old-style station with a name only. */
    private fun station(e: JsonElement, index: Int): TrailStation? = when {
        e.isJsonPrimitive -> TrailStation(id = "station_$index", name = e.asString, emoji = "", description = "", point = null)
        e is JsonObject -> TrailStation(
            id = e.text("id").ifBlank { "station_$index" },
            name = e.text("name"),
            emoji = e.text("emoji"),
            description = e.text("description"),
            point = e.point("point"),
            radiusMeters = e.number("radiusMeters").toInt().takeIf { it > 0 } ?: TrailStation.DEFAULT_RADIUS_METERS,
            photo = e.text("photo"),
            kind = StationKind.fromId(e.text("kind")),
        )
        else -> null
    }

    private fun station(s: TrailStation) = JsonObject().apply {
        addProperty("id", s.id)
        addProperty("name", s.name)
        addProperty("emoji", s.emoji)
        addProperty("description", s.description)
        s.point?.let { add("point", point(it)) }
        addProperty("radiusMeters", s.radiusMeters)
        addProperty("photo", s.photo)
        addProperty("kind", s.kind.id)
    }

    private fun news(o: JsonObject) = NewsItem(
        id = o.text("id"),
        title = o.text("title"),
        emoji = o.text("emoji"),
        date = o.text("date"),
        category = o.text("category"),
        summary = o.text("summary"),
        body = o.text("body"),
        photo = o.text("photo"),
        sourceName = o.text("sourceName"),
        sourceUrl = o.text("sourceUrl"),
    )

    private fun news(n: NewsItem) = JsonObject().apply {
        addProperty("id", n.id)
        addProperty("title", n.title)
        addProperty("emoji", n.emoji)
        addProperty("date", n.date)
        addProperty("category", n.category)
        addProperty("summary", n.summary)
        addProperty("body", n.body)
        addProperty("photo", n.photo)
        addProperty("sourceName", n.sourceName)
        addProperty("sourceUrl", n.sourceUrl)
    }

    private fun species(o: JsonObject) = Species(
        id = o.text("id"),
        kind = SpeciesKind.fromId(o.text("kind")),
        name = o.text("name"),
        scientificName = o.text("scientificName"),
        emoji = o.text("emoji"),
        conservationStatus = o.text("conservationStatus"),
        habitat = o.text("habitat"),
        description = o.text("description"),
        tip = o.text("tip"),
        photo = o.text("photo"),
    )

    private fun species(s: Species) = JsonObject().apply {
        addProperty("id", s.id)
        addProperty("kind", s.kind.id)
        addProperty("name", s.name)
        addProperty("scientificName", s.scientificName)
        addProperty("emoji", s.emoji)
        addProperty("conservationStatus", s.conservationStatus)
        addProperty("habitat", s.habitat)
        addProperty("description", s.description)
        addProperty("tip", s.tip)
        addProperty("photo", s.photo)
    }

    // ----------------------------------------------------------------------------------

    private fun point(e: JsonElement): GeoPoint? {
        val array = e as? JsonArray ?: return null
        if (array.size() < 2) return null
        val coordinates = array.mapNotNull { c -> c.takeIf { it.isJsonPrimitive }?.asDouble }
        return if (coordinates.size < 2) null else GeoUtils.fromGeoJsonCoordinates(coordinates)
    }

    private fun point(p: GeoPoint) = JsonArray().also { a -> GeoUtils.toGeoJsonCoordinates(p).forEach(a::add) }

    private fun JsonObject.point(key: String): GeoPoint? = get(key)?.let(::point)

    private fun JsonObject.objects(key: String): List<JsonObject> = array(key).mapNotNull { it as? JsonObject }

    private fun JsonObject.array(key: String): List<JsonElement> = (get(key) as? JsonArray)?.toList().orEmpty()

    private fun JsonObject.text(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.number(key: String): Double =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull() ?: 0.0

    private fun JsonObject.flag(key: String, default: Boolean): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: default
}
