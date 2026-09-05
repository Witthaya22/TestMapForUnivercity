package th.ac.kmutnb.prachin.map.data.model

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint

/** POI grouping. `id` is what appears in `pois.geojson`; the label lives in `strings.xml`. */
enum class PoiCategory(val id: String) {
    GATE("gate"),
    DORM("dorm"),
    ACADEMIC("academic"),
    CANTEEN("canteen"),
    SPORT("sport"),
    PARKING("parking"),
    SERVICE("service"),
    CUSTOM("custom"),
    ;

    companion object {
        /** Unknown values fall back to [CUSTOM] rather than failing the whole file. */
        fun fromId(id: String?): PoiCategory = entries.firstOrNull { it.id == id } ?: CUSTOM
    }
}

/** A place on campus, whether shipped in assets or added by the user. */
data class Poi(
    val id: String,
    val name: String,
    val shortName: String?,
    val category: PoiCategory,
    val order: Int,
    val description: String,
    /** Free-text note the user may edit in the app. */
    val note: String,
    val icon: String?,
    val point: GeoPoint,
    val isUserCreated: Boolean,
    /** Metres of GPS accuracy at survey time; `null` for POIs that were not surveyed. */
    val gpsAccuracy: Float?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /** Short label for the map, falling back to the full name. */
    val label: String get() = shortName?.takeIf { it.isNotBlank() } ?: name
}
