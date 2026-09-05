package th.ac.kmutnb.prachin.map.core.geo

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Every distance / bearing / projection formula in the app. Nothing else may implement its own.
 *
 * Two distance models are used on purpose:
 *  - [haversineMeters] - spherical, correct anywhere, used for anything the user sees.
 *  - the local equirectangular projection behind [distanceToSegmentMeters] and friends -
 *    roughly 5x cheaper and accurate to well under a centimetre over the sub-kilometre
 *    spans inside a campus, which is what the routing graph and off-route checks run on.
 */
object GeoUtils {

    /** IUGG mean Earth radius. Mirrored in tools/geojson_validate.py. */
    const val EARTH_RADIUS_METERS = 6_371_008.8

    /** Average walking speed used for every ETA in the app. */
    const val WALKING_SPEED_MPS = 1.3

    /**
     * Metres per degree on the same sphere [haversineMeters] uses (`pi * R / 180`).
     *
     * Deliberately spherical rather than the more familiar ellipsoidal 110540 / 111320: the
     * planar and great-circle models must agree with each other, otherwise a route measured
     * one way and checked the other drifts apart by ~0.6%. Against the WGS84 ellipsoid both
     * are off by a few tenths of a percent, which is far below GPS noise at campus scale.
     */
    private const val METERS_PER_DEGREE_LAT = 111_194.926_644_558_51
    private const val METERS_PER_DEGREE_LON_AT_EQUATOR = METERS_PER_DEGREE_LAT

    private const val DEG_TO_RAD = Math.PI / 180.0
    private const val RAD_TO_DEG = 180.0 / Math.PI

    // ----------------------------------------------------------------------------------
    // GeoJSON <-> GeoPoint. The ONLY place lat/lon ordering is decided.
    // ----------------------------------------------------------------------------------

    /**
     * Converts a GeoJSON coordinate array to a [GeoPoint].
     *
     * RFC 7946 orders positions as `[longitude, latitude]`, the opposite of how humans in
     * Thailand usually write them. A third element (elevation) is allowed and ignored.
     */
    fun fromGeoJsonCoordinates(coordinates: List<Double>): GeoPoint {
        require(coordinates.size >= 2) {
            "GeoJSON coordinates need at least [lon, lat], got ${coordinates.size} value(s)"
        }
        return GeoPoint(lat = coordinates[1], lon = coordinates[0])
    }

    /** Inverse of [fromGeoJsonCoordinates]; emits `[lon, lat]`. */
    fun toGeoJsonCoordinates(point: GeoPoint): List<Double> = listOf(point.lon, point.lat)

    // ----------------------------------------------------------------------------------
    // Distances
    // ----------------------------------------------------------------------------------

    /** Great-circle distance in metres. */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = lat1 * DEG_TO_RAD
        val phi2 = lat2 * DEG_TO_RAD
        val dPhi = (lat2 - lat1) * DEG_TO_RAD
        val dLambda = (lon2 - lon1) * DEG_TO_RAD

        val sinHalfDPhi = sin(dPhi / 2.0)
        val sinHalfDLambda = sin(dLambda / 2.0)
        val a = sinHalfDPhi * sinHalfDPhi +
            cos(phi1) * cos(phi2) * sinHalfDLambda * sinHalfDLambda
        return 2.0 * EARTH_RADIUS_METERS * asin(min(1.0, sqrt(a)))
    }

    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double =
        haversineMeters(a.lat, a.lon, b.lat, b.lon)

    /**
     * Fast planar approximation, good for a few kilometres. Used inside tight loops
     * (graph building, nearest-edge search); prefer [haversineMeters] for displayed values.
     */
    fun approxDistanceMeters(a: GeoPoint, b: GeoPoint): Double {
        val scale = lonScaleAt((a.lat + b.lat) / 2.0)
        val dx = (b.lon - a.lon) * scale
        val dy = (b.lat - a.lat) * METERS_PER_DEGREE_LAT
        return hypot(dx, dy)
    }

    /** Total length of a polyline, in metres. */
    fun pathLengthMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineMeters(points[i - 1], points[i])
        }
        return total
    }

    // ----------------------------------------------------------------------------------
    // Point-to-segment geometry
    // ----------------------------------------------------------------------------------

    /**
     * Where along segment `a`->`b` the perpendicular from `p` lands, clamped to `[0, 1]`.
     * `0` means at `a`, `1` means at `b`. A zero-length segment yields `0`.
     */
    fun segmentProjectionFactor(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val scale = lonScaleAt((a.lat + b.lat) / 2.0)
        val ax = a.lon * scale
        val ay = a.lat * METERS_PER_DEGREE_LAT
        val bx = b.lon * scale
        val by = b.lat * METERS_PER_DEGREE_LAT
        val px = p.lon * scale
        val py = p.lat * METERS_PER_DEGREE_LAT

        val dx = bx - ax
        val dy = by - ay
        val lengthSq = dx * dx + dy * dy
        if (lengthSq == 0.0) return 0.0
        val t = ((px - ax) * dx + (py - ay) * dy) / lengthSq
        return t.coerceIn(0.0, 1.0)
    }

    /** The point on segment `a`->`b` closest to `p`. Used when snapping onto a path. */
    fun nearestPointOnSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): GeoPoint {
        val t = segmentProjectionFactor(p, a, b)
        return interpolate(a, b, t)
    }

    /** Perpendicular distance in metres from `p` to segment `a`->`b` (not the infinite line). */
    fun distanceToSegmentMeters(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double =
        haversineMeters(p, nearestPointOnSegment(p, a, b))

    /**
     * Shortest distance from `p` to a polyline. Returns [Double.MAX_VALUE] for a polyline
     * with fewer than two points.
     */
    fun distanceToPolylineMeters(p: GeoPoint, polyline: List<GeoPoint>): Double {
        if (polyline.isEmpty()) return Double.MAX_VALUE
        if (polyline.size == 1) return haversineMeters(p, polyline[0])
        var best = Double.MAX_VALUE
        for (i in 1 until polyline.size) {
            val d = distanceToSegmentMeters(p, polyline[i - 1], polyline[i])
            if (d < best) best = d
        }
        return best
    }

    /** Linear interpolation between two nearby points; exact enough at campus scale. */
    fun interpolate(a: GeoPoint, b: GeoPoint, t: Double): GeoPoint = GeoPoint(
        lat = a.lat + (b.lat - a.lat) * t,
        lon = a.lon + (b.lon - a.lon) * t,
    )

    // ----------------------------------------------------------------------------------
    // Bearing
    // ----------------------------------------------------------------------------------

    /** Initial bearing from `from` to `to`, in degrees clockwise from true north (`0..360`). */
    fun bearingDegrees(from: GeoPoint, to: GeoPoint): Double {
        val phi1 = from.lat * DEG_TO_RAD
        val phi2 = to.lat * DEG_TO_RAD
        val dLambda = (to.lon - from.lon) * DEG_TO_RAD
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val deg = atan2(y, x) * RAD_TO_DEG
        return (deg + 360.0) % 360.0
    }

    /** Smallest signed difference between two bearings, in `(-180, 180]`. */
    fun bearingDeltaDegrees(from: Double, to: Double): Double {
        var delta = (to - from + 540.0) % 360.0 - 180.0
        if (delta == -180.0) delta = 180.0
        return delta
    }

    // ----------------------------------------------------------------------------------
    // Polyline simplification
    // ----------------------------------------------------------------------------------

    /**
     * Douglas-Peucker simplification with the tolerance expressed in metres.
     *
     * Applied to recorded GPS tracks before export: a 10-minute walk produces ~200 points
     * of which most only encode GPS jitter. Iterative rather than recursive so a long
     * track cannot blow the stack.
     */
    fun simplifyDouglasPeucker(points: List<GeoPoint>, epsilonMeters: Double): List<GeoPoint> {
        if (points.size <= 2 || epsilonMeters <= 0.0) return points

        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true

        val stack = ArrayDeque<Pair<Int, Int>>()
        stack.addLast(0 to points.size - 1)

        while (stack.isNotEmpty()) {
            val (first, last) = stack.removeLast()
            if (last <= first + 1) continue

            var farthestIndex = -1
            var farthestDistance = 0.0
            for (i in (first + 1) until last) {
                val d = distanceToSegmentMeters(points[i], points[first], points[last])
                if (d > farthestDistance) {
                    farthestDistance = d
                    farthestIndex = i
                }
            }

            if (farthestDistance > epsilonMeters && farthestIndex > 0) {
                keep[farthestIndex] = true
                stack.addLast(first to farthestIndex)
                stack.addLast(farthestIndex to last)
            }
        }

        return points.filterIndexed { index, _ -> keep[index] }
    }

    // ----------------------------------------------------------------------------------
    // Bounding box helper
    // ----------------------------------------------------------------------------------

    /** Latitude/longitude deltas that cover [meters] in every direction from [around]. */
    fun degreesForMeters(meters: Double, around: GeoPoint): Pair<Double, Double> {
        val latDelta = meters / METERS_PER_DEGREE_LAT
        val lonDelta = meters / lonScaleAt(around.lat).coerceAtLeast(1.0)
        return latDelta to lonDelta
    }

    /** Metres per degree of longitude at the given latitude. */
    private fun lonScaleAt(latitudeDegrees: Double): Double =
        METERS_PER_DEGREE_LON_AT_EQUATOR * abs(cos(latitudeDegrees * DEG_TO_RAD))

    // ----------------------------------------------------------------------------------
    // Time
    // ----------------------------------------------------------------------------------

    /** Walking duration for a distance, in whole seconds, using [WALKING_SPEED_MPS]. */
    fun walkingSecondsFor(distanceMeters: Double): Int =
        Math.ceil(distanceMeters / WALKING_SPEED_MPS).toInt().coerceAtLeast(0)
}
