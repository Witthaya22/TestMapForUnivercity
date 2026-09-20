package th.ac.kmutnb.prachin.map.data.geojson

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.HazardPoint
import th.ac.kmutnb.prachin.map.data.model.HazardSeverity
import th.ac.kmutnb.prachin.map.data.model.HazardType

/**
 * The hazard file is how one person's survey reaches everyone else's phone, so a round trip
 * has to be lossless and a damaged file has to degrade into a warning rather than silence.
 */
class HazardGeoJsonTest {

    private fun hazard(
        id: String = "hazard_1",
        type: HazardType = HazardType.DOG,
        severity: HazardSeverity = HazardSeverity.DANGER,
        radius: Double = 25.0,
        active: Boolean = true,
    ) = HazardPoint(
        id = id,
        type = type,
        severity = severity,
        point = GeoPoint(lat = 14.1610243, lon = 101.3529617),
        radiusMeters = radius,
        description = "หมาเฝ้าอยู่ 3 ตัว",
        isActive = active,
        gpsAccuracy = 4.5f,
        createdAt = 1_789_889_525_000L,
        updatedAt = 1_789_889_600_000L,
    )

    @Test
    fun `a hazard survives a round trip unchanged`() {
        val original = hazard()
        val parsed = HazardGeoJson.parse(HazardGeoJson.write(listOf(original)))

        assertEquals(emptyList<GeoJsonIssue>(), parsed.issues)
        assertEquals(listOf(original), parsed.items)
    }

    @Test
    fun `coordinates are lon lat, in that order`() {
        val geometry = JsonParser.parseString(HazardGeoJson.write(listOf(hazard())))
            .asJsonObject
            .getAsJsonArray("features")[0].asJsonObject
            .getAsJsonObject("geometry")

        val coordinates = geometry.getAsJsonArray("coordinates")
        assertEquals(101.3529617, coordinates[0].asDouble, 1e-9)
        assertEquals(14.1610243, coordinates[1].asDouble, 1e-9)
    }

    @Test
    fun `a hazard switched off keeps its off state through the file`() {
        val parsed = HazardGeoJson.parse(HazardGeoJson.write(listOf(hazard(active = false))))
        assertEquals(false, parsed.items.single().isActive)
    }

    @Test
    fun `a type this build has never heard of still warns, as OTHER`() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature",
               "geometry":{"type":"Point","coordinates":[101.35,14.16]},
               "properties":{"id":"h1","type":"falling_coconuts","severity":"danger",
                             "radiusMeters":20.0,"description":"","isActive":true}}
            ]}
        """.trimIndent()

        val hazard = HazardGeoJson.parse(json).items.single()
        // Dropping it would be the one outcome that could get somebody hurt.
        assertEquals(HazardType.OTHER, hazard.type)
        assertEquals(HazardSeverity.DANGER, hazard.severity)
    }

    @Test
    fun `an absurd radius is clamped rather than trusted`() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature",
               "geometry":{"type":"Point","coordinates":[101.35,14.16]},
               "properties":{"id":"h1","type":"dog","severity":"warning",
                             "radiusMeters":5000.0,"description":"","isActive":true}}
            ]}
        """.trimIndent()

        assertEquals(HazardPoint.MAX_RADIUS_M, HazardGeoJson.parse(json).items.single().radiusMeters, 1e-9)
    }

    @Test
    fun `a feature with no id is reported, and the rest of the file still loads`() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature",
               "geometry":{"type":"Point","coordinates":[101.35,14.16]},
               "properties":{"type":"dog","severity":"warning"}},
              {"type":"Feature",
               "geometry":{"type":"Point","coordinates":[101.36,14.17]},
               "properties":{"id":"h2","type":"traffic","severity":"danger"}}
            ]}
        """.trimIndent()

        val parsed = HazardGeoJson.parse(json)
        assertEquals(1, parsed.items.size)
        assertEquals("h2", parsed.items.single().id)
        assertEquals(GeoJsonIssue.Reason.MISSING_ID, parsed.issues.single().reason)
    }

    @Test
    fun `UTM pasted in place of degrees is rejected, not crashed on`() {
        val json = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature",
               "geometry":{"type":"Point","coordinates":[753000.0,1566000.0]},
               "properties":{"id":"h1","type":"dog","severity":"warning"}}
            ]}
        """.trimIndent()

        val parsed = HazardGeoJson.parse(json)
        assertTrue(parsed.items.isEmpty())
        assertEquals(GeoJsonIssue.Reason.OUT_OF_DEGREE_RANGE, parsed.issues.single().reason)
    }

    @Test
    fun `an empty list writes a valid, empty FeatureCollection`() {
        val root = JsonParser.parseString(HazardGeoJson.write(emptyList())).asJsonObject
        assertEquals("FeatureCollection", root["type"].asString)
        assertEquals(0, root.getAsJsonArray("features").size())
    }
}
