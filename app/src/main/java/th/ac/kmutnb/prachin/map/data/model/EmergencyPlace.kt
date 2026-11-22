package th.ac.kmutnb.prachin.map.data.model

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils

enum class EmergencyPlaceKind(val id: String) {
    HOSPITAL("hospital"),
    POLICE("police");

    companion object {
        fun fromId(id: String?): EmergencyPlaceKind? = entries.firstOrNull { it.id == id }
    }
}

/** A hospital or police station near the park, bundled so "the nearest" works offline. */
data class EmergencyPlace(
    val name: String,
    val kind: EmergencyPlaceKind,
    val point: GeoPoint,
    val phone: String,
)

data class NearbyPlace(val place: EmergencyPlace, val distanceMeters: Double)

object EmergencyPlaces {

    /** `assets/data/emergency_places.json`: `{places: [{name, kind, lat, lon, phone}]}`. */
    fun parse(json: String): List<EmergencyPlace> =
        JsonParser.parseString(json).asJsonObject.getAsJsonArray("places").mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val kind = EmergencyPlaceKind.fromId(o.get("kind")?.asString) ?: return@mapNotNull null
            EmergencyPlace(
                name = o.get("name")?.asString.orEmpty(),
                kind = kind,
                point = GeoUtils.fromGeoJsonCoordinates(listOf(o.get("lon").asDouble, o.get("lat").asDouble)),
                phone = o.get("phone")?.asString.orEmpty(),
            )
        }

    /** The [count] nearest places of [kind] to [here], straight-line, nearest first. */
    fun nearest(places: List<EmergencyPlace>, kind: EmergencyPlaceKind, here: GeoPoint, count: Int = 2): List<NearbyPlace> =
        places.filter { it.kind == kind }
            .map { NearbyPlace(it, GeoUtils.haversineMeters(here, it.point)) }
            .sortedBy { it.distanceMeters }
            .take(count)
}
