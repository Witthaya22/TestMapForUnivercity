package th.ac.kmutnb.prachin.map.navigation

import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.navigation.model.PathType
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath

/**
 * Helpers for building small networks in metres, so routing tests read as geometry rather
 * than as decimal degrees. The origin sits inside the Prachin Buri campus latitude band.
 */
object TestGeo {

    val ORIGIN = GeoPoint(lat = 14.1100, lon = 101.3800)

    /** A point [north] metres north and [east] metres east of [ORIGIN]. */
    fun at(north: Double, east: Double, origin: GeoPoint = ORIGIN): GeoPoint {
        val (latPerMeter, lonPerMeter) = GeoUtils.degreesForMeters(1.0, origin)
        return GeoPoint(
            lat = origin.lat + north * latPerMeter,
            lon = origin.lon + east * lonPerMeter,
        )
    }

    fun path(
        id: String,
        vararg points: GeoPoint,
        type: PathType = PathType.FOOTWAY,
        oneway: Boolean = false,
    ) = WalkPath(id = id, type = type, points = points.toList(), oneway = oneway)
}
