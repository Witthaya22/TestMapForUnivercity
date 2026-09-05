package th.ac.kmutnb.prachin.map.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    private val bangkok = GeoPoint(lat = 13.7563, lon = 100.5018)
    private val chiangMai = GeoPoint(lat = 18.7883, lon = 98.9853)

    /** Degrees of latitude per metre on the sphere GeoUtils uses. */
    private val degreesPerMeter = 180.0 / (Math.PI * GeoUtils.EARTH_RADIUS_METERS)

    // ----------------------------------------------------------------------------------
    // Distance
    // ----------------------------------------------------------------------------------

    @Test
    fun `bangkok to chiang mai is about 585 km`() {
        val meters = GeoUtils.haversineMeters(bangkok, chiangMai)
        val expected = 585_000.0
        assertTrue(
            "expected ~585 km +/- 1%, got ${meters / 1000.0} km",
            Math.abs(meters - expected) <= expected * 0.01,
        )
    }

    @Test
    fun `two points 100 m apart measure 100 m`() {
        val a = GeoPoint(lat = 14.1100, lon = 101.3800)
        val b = a.copy(lat = a.lat + 100.0 * degreesPerMeter)
        assertEquals(100.0, GeoUtils.haversineMeters(a, b), 0.5)
    }

    @Test
    fun `distance is symmetric and zero for identical points`() {
        assertEquals(0.0, GeoUtils.haversineMeters(bangkok, bangkok), 1e-9)
        assertEquals(
            GeoUtils.haversineMeters(bangkok, chiangMai),
            GeoUtils.haversineMeters(chiangMai, bangkok),
            1e-6,
        )
    }

    @Test
    fun `equirectangular approximation matches haversine at campus scale`() {
        val a = GeoPoint(lat = 14.1100, lon = 101.3800)
        val b = GeoPoint(lat = 14.1180, lon = 101.3890)
        val exact = GeoUtils.haversineMeters(a, b)
        val approx = GeoUtils.approxDistanceMeters(a, b)
        // Well inside GPS noise over the ~1.3 km this spans.
        assertEquals(exact, approx, 1.0)
    }

    @Test
    fun `path length sums the segments`() {
        val step = 50.0 * degreesPerMeter
        val points = (0..4).map { GeoPoint(lat = 14.11 + it * step, lon = 101.38) }
        assertEquals(200.0, GeoUtils.pathLengthMeters(points), 1.0)
        assertEquals(0.0, GeoUtils.pathLengthMeters(points.take(1)), 0.0)
        assertEquals(0.0, GeoUtils.pathLengthMeters(emptyList()), 0.0)
    }

    // ----------------------------------------------------------------------------------
    // Point to segment
    // ----------------------------------------------------------------------------------

    @Test
    fun `point exactly on the segment has zero distance`() {
        val a = GeoPoint(lat = 14.1100, lon = 101.3800)
        val b = GeoPoint(lat = 14.1120, lon = 101.3800)
        val midpoint = GeoUtils.interpolate(a, b, 0.5)
        assertEquals(0.0, GeoUtils.distanceToSegmentMeters(midpoint, a, b), 0.01)
        assertEquals(0.0, GeoUtils.distanceToSegmentMeters(a, a, b), 0.01)
        assertEquals(0.0, GeoUtils.distanceToSegmentMeters(b, a, b), 0.01)
    }

    @Test
    fun `perpendicular offset from a segment measures that offset`() {
        val a = GeoPoint(lat = 14.1100, lon = 101.3800)
        val b = GeoPoint(lat = 14.1100 + 200.0 * degreesPerMeter, lon = 101.3800)
        // 30 m east of the midpoint of a north-south segment.
        val (_, lonDelta) = GeoUtils.degreesForMeters(30.0, a)
        val offset = GeoPoint(lat = 14.1100 + 100.0 * degreesPerMeter, lon = 101.3800 + lonDelta)
        assertEquals(30.0, GeoUtils.distanceToSegmentMeters(offset, a, b), 0.5)
    }

    @Test
    fun `distance clamps to the segment ends rather than the infinite line`() {
        val a = GeoPoint(lat = 14.1100, lon = 101.3800)
        val b = GeoPoint(lat = 14.1100 + 100.0 * degreesPerMeter, lon = 101.3800)
        // 50 m beyond b, still on the same bearing: nearest point must be b itself.
        val beyond = GeoPoint(lat = 14.1100 + 150.0 * degreesPerMeter, lon = 101.3800)
        assertEquals(50.0, GeoUtils.distanceToSegmentMeters(beyond, a, b), 0.5)
        assertEquals(b, GeoUtils.nearestPointOnSegment(beyond, a, b))
    }

    @Test
    fun `zero length segment falls back to point distance`() {
        val a = GeoPoint(lat = 14.1100, lon = 101.3800)
        val p = GeoPoint(lat = 14.1100 + 20.0 * degreesPerMeter, lon = 101.3800)
        assertEquals(20.0, GeoUtils.distanceToSegmentMeters(p, a, a), 0.5)
    }

    @Test
    fun `projection factor spans the segment`() {
        val a = GeoPoint(lat = 14.1100, lon = 101.3800)
        val b = GeoPoint(lat = 14.1100 + 100.0 * degreesPerMeter, lon = 101.3800)
        assertEquals(0.0, GeoUtils.segmentProjectionFactor(a, a, b), 1e-6)
        assertEquals(1.0, GeoUtils.segmentProjectionFactor(b, a, b), 1e-6)
        assertEquals(
            0.5,
            GeoUtils.segmentProjectionFactor(GeoUtils.interpolate(a, b, 0.5), a, b),
            1e-4,
        )
    }

    @Test
    fun `distance to polyline picks the closest segment`() {
        val line = listOf(
            GeoPoint(lat = 14.1100, lon = 101.3800),
            GeoPoint(lat = 14.1100 + 100.0 * degreesPerMeter, lon = 101.3800),
            GeoPoint(lat = 14.1100 + 100.0 * degreesPerMeter, lon = 101.3850),
        )
        val onSecondSegment = GeoPoint(lat = 14.1100 + 100.0 * degreesPerMeter, lon = 101.3820)
        assertEquals(0.0, GeoUtils.distanceToPolylineMeters(onSecondSegment, line), 0.5)
        assertEquals(Double.MAX_VALUE, GeoUtils.distanceToPolylineMeters(onSecondSegment, emptyList()), 0.0)
    }

    // ----------------------------------------------------------------------------------
    // Bearing
    // ----------------------------------------------------------------------------------

    @Test
    fun `bearing points north east south and west`() {
        val origin = GeoPoint(lat = 14.1100, lon = 101.3800)
        val delta = 100.0 * degreesPerMeter
        assertEquals(0.0, GeoUtils.bearingDegrees(origin, origin.copy(lat = origin.lat + delta)), 0.5)
        assertEquals(90.0, GeoUtils.bearingDegrees(origin, origin.copy(lon = origin.lon + delta)), 0.5)
        assertEquals(180.0, GeoUtils.bearingDegrees(origin, origin.copy(lat = origin.lat - delta)), 0.5)
        assertEquals(270.0, GeoUtils.bearingDegrees(origin, origin.copy(lon = origin.lon - delta)), 0.5)
    }

    @Test
    fun `bearing delta takes the short way around`() {
        assertEquals(20.0, GeoUtils.bearingDeltaDegrees(350.0, 10.0), 1e-9)
        assertEquals(-20.0, GeoUtils.bearingDeltaDegrees(10.0, 350.0), 1e-9)
        assertEquals(0.0, GeoUtils.bearingDeltaDegrees(90.0, 90.0), 1e-9)
    }

    // ----------------------------------------------------------------------------------
    // GeoJSON conversion - the lat/lon swap guard
    // ----------------------------------------------------------------------------------

    @Test
    fun `geojson coordinates are lon then lat`() {
        val point = GeoUtils.fromGeoJsonCoordinates(listOf(101.3800, 14.1100))
        assertEquals(14.1100, point.lat, 1e-9)
        assertEquals(101.3800, point.lon, 1e-9)
        assertEquals(listOf(101.3800, 14.1100), GeoUtils.toGeoJsonCoordinates(point))
        assertNotEquals(point.lat, point.lon, 1e-9)
    }

    @Test
    fun `geojson conversion ignores elevation`() {
        val point = GeoUtils.fromGeoJsonCoordinates(listOf(101.38, 14.11, 42.0))
        assertEquals(14.11, point.lat, 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `geojson conversion rejects a short coordinate array`() {
        GeoUtils.fromGeoJsonCoordinates(listOf(101.38))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `swapped lat lon in Thailand is rejected by GeoPoint`() {
        // Thai coordinates are lat ~14, lon ~101. Swapping them puts latitude past the pole.
        GeoPoint(lat = 101.38, lon = 14.11)
    }

    // ----------------------------------------------------------------------------------
    // Simplification
    // ----------------------------------------------------------------------------------

    @Test
    fun `douglas peucker drops collinear points and keeps the ends`() {
        val step = 10.0 * degreesPerMeter
        val straight = (0..10).map { GeoPoint(lat = 14.11 + it * step, lon = 101.38) }
        val simplified = GeoUtils.simplifyDouglasPeucker(straight, epsilonMeters = 2.0)
        assertEquals(2, simplified.size)
        assertEquals(straight.first(), simplified.first())
        assertEquals(straight.last(), simplified.last())
    }

    @Test
    fun `douglas peucker keeps a corner that exceeds the tolerance`() {
        val step = 50.0 * degreesPerMeter
        val (_, lonDelta) = GeoUtils.degreesForMeters(50.0, GeoPoint(14.11, 101.38))
        val corner = listOf(
            GeoPoint(lat = 14.11, lon = 101.38),
            GeoPoint(lat = 14.11 + step, lon = 101.38 + lonDelta),
            GeoPoint(lat = 14.11 + 2 * step, lon = 101.38),
        )
        val simplified = GeoUtils.simplifyDouglasPeucker(corner, epsilonMeters = 2.0)
        assertEquals(3, simplified.size)
    }

    @Test
    fun `douglas peucker leaves short inputs alone`() {
        val two = listOf(GeoPoint(14.11, 101.38), GeoPoint(14.12, 101.38))
        assertEquals(two, GeoUtils.simplifyDouglasPeucker(two, epsilonMeters = 2.0))
        assertEquals(two, GeoUtils.simplifyDouglasPeucker(two, epsilonMeters = 0.0))
    }

    @Test
    fun `douglas peucker removes gps jitter from a straight walk`() {
        // 100 fixes along a straight line with sub-metre wobble, as a real track looks.
        val random = java.util.Random(42)
        val step = 3.0 * degreesPerMeter
        val jittery = (0..99).map { i ->
            GeoPoint(
                lat = 14.11 + i * step + (random.nextDouble() - 0.5) * degreesPerMeter,
                lon = 101.38 + (random.nextDouble() - 0.5) * degreesPerMeter,
            )
        }
        val simplified = GeoUtils.simplifyDouglasPeucker(jittery, epsilonMeters = 2.0)
        assertTrue("expected heavy reduction, got ${simplified.size}", simplified.size < 10)
    }

    // ----------------------------------------------------------------------------------
    // Time
    // ----------------------------------------------------------------------------------

    @Test
    fun `walking time uses 1_3 metres per second and rounds up`() {
        assertEquals(0, GeoUtils.walkingSecondsFor(0.0))
        assertEquals(1, GeoUtils.walkingSecondsFor(0.1))
        assertEquals(100, GeoUtils.walkingSecondsFor(130.0))
        // 1000 / 1.3 = 769.23..., rounded up to a whole second.
        assertEquals(770, GeoUtils.walkingSecondsFor(1000.0))
    }
}
