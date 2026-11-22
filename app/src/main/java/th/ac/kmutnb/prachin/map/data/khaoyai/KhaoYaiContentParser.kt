package th.ac.kmutnb.prachin.map.data.khaoyai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Reads `khaoyai_content.json` by hand rather than through Gson reflection, so release
 * shrinking cannot strip the model classes and no keep rules are needed for them.
 * A missing field becomes an empty value rather than an error: the file is edited by hand.
 */
object KhaoYaiContentParser {

    fun parse(json: String): KhaoYaiContent {
        val root = JsonParser.parseString(json).asJsonObject
        return KhaoYaiContent(
            trails = root.objects("trails").map(::trail),
            news = root.objects("news").map(::news),
            species = root.objects("species").map(::species),
        )
    }

    private fun trail(o: JsonObject) = Trail(
        id = o.text("id"),
        name = o.text("name"),
        emoji = o.text("emoji"),
        distanceKm = o.number("distanceKm"),
        durationMinutes = o.number("durationMinutes").toInt(),
        difficulty = TrailDifficulty.fromId(o.text("difficulty")),
        startPoint = o.text("startPoint"),
        summary = o.text("summary"),
        description = o.text("description"),
        stations = o.texts("stations"),
        needsGuide = o.flag("needsGuide", default = false),
        isOpen = o.flag("isOpen", default = true),
    )

    private fun news(o: JsonObject) = NewsItem(
        id = o.text("id"),
        title = o.text("title"),
        emoji = o.text("emoji"),
        date = o.text("date"),
        category = o.text("category"),
        summary = o.text("summary"),
        body = o.text("body"),
    )

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
    )

    private fun JsonObject.objects(key: String): List<JsonObject> =
        (get(key) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()

    private fun JsonObject.text(key: String): String =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()

    private fun JsonObject.number(key: String): Double =
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull() ?: 0.0

    private fun JsonObject.flag(key: String, default: Boolean): Boolean =
        get(key)?.takeIf { it.isJsonPrimitive }?.asBoolean ?: default

    private fun JsonObject.texts(key: String): List<String> =
        (get(key) as? JsonArray)?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString }
            .orEmpty()
}
