package th.ac.kmutnb.prachin.map.core.geo

/**
 * A WGS84 coordinate, latitude first.
 *
 * Deliberately a plain Kotlin class with no Android or MapLibre dependency so that every
 * distance and routing calculation is unit-testable on the JVM.
 *
 * Note the field order: this type is `(lat, lon)` like MapLibre's `LatLng`, while GeoJSON
 * stores `[lon, lat]`. Never build one of these from raw GeoJSON coordinates by hand -
 * use [GeoUtils.fromGeoJsonCoordinates], which is the single place that conversion happens.
 */
data class GeoPoint(
    val lat: Double,
    val lon: Double,
) {
    init {
        require(lat in -90.0..90.0) { "latitude out of range: $lat" }
        require(lon in -180.0..180.0) { "longitude out of range: $lon" }
    }

    /** Rounded to ~1 cm, which is far finer than any GPS fix; used for logs and debug UI. */
    fun format(): String = "%.7f, %.7f".format(lat, lon)
}
