package th.ac.kmutnb.prachin.map.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CampusConfigParserTest {

    private fun config(
        minLon: Double = 101.3600,
        minLat: Double = 14.0950,
        maxLon: Double = 101.4000,
        maxLat: Double = 14.1300,
        centerLon: Double = 101.3800,
        centerLat: Double = 14.1100,
        minZoom: Int = 13,
        maxZoom: Int = 18,
        defaultZoom: Double = 16.5,
        styleUrl: String = "https://tiles.openfreemap.org/styles/liberty",
    ) = """
        {
          "campusName": "มจพ. วิทยาเขตปราจีนบุรี",
          "bbox": { "minLon": $minLon, "minLat": $minLat, "maxLon": $maxLon, "maxLat": $maxLat },
          "center": { "lon": $centerLon, "lat": $centerLat },
          "defaultZoom": $defaultZoom,
          "minZoom": $minZoom,
          "maxZoom": $maxZoom,
          "styleUrl": "$styleUrl"
        }
    """.trimIndent()

    private fun expectProblem(json: String, expected: ConfigProblem) {
        try {
            CampusConfigParser.parse(json)
            fail("expected $expected but parsing succeeded")
        } catch (e: CampusConfigException) {
            assertEquals(expected, e.problem)
        }
    }

    @Test
    fun `parses a well formed config`() {
        val parsed = CampusConfigParser.parse(config())
        assertEquals("มจพ. วิทยาเขตปราจีนบุรี", parsed.campusName)
        assertEquals(14.1100, parsed.center.lat, 1e-9)
        assertEquals(101.3800, parsed.center.lon, 1e-9)
        assertEquals(13, parsed.minZoom)
        assertEquals(18, parsed.maxZoom)
        assertEquals(16.5, parsed.defaultZoom, 1e-9)
        assertTrue(parsed.center in parsed.bbox)
    }

    @Test
    fun `the shipped placeholder file is rejected`() {
        val shipped = """
            {
              "campusName": "มจพ. วิทยาเขตปราจีนบุรี",
              "bbox": { "minLon": 0.0, "minLat": 0.0, "maxLon": 0.0, "maxLat": 0.0 },
              "center": { "lon": 0.0, "lat": 0.0 },
              "defaultZoom": 16.5,
              "minZoom": 13,
              "maxZoom": 18,
              "styleUrl": "https://tiles.openfreemap.org/styles/liberty"
            }
        """.trimIndent()
        expectProblem(shipped, ConfigProblem.PLACEHOLDER_COORDINATES)
    }

    @Test
    fun `utm coordinates are reported as out of degree range`() {
        // A UTM Zone 47N easting/northing pair, the classic mix-up with Thai survey files.
        expectProblem(
            config(minLon = 731234.5, minLat = 1560987.2, maxLon = 732000.0, maxLat = 1561500.0),
            ConfigProblem.OUT_OF_DEGREE_RANGE,
        )
    }

    @Test
    fun `swapped lat lon is caught as out of degree range`() {
        // lat 101 is past the pole, so the swap cannot pass validation.
        expectProblem(
            config(minLon = 14.0950, minLat = 101.3600, maxLon = 14.1300, maxLat = 101.4000),
            ConfigProblem.OUT_OF_DEGREE_RANGE,
        )
    }

    @Test
    fun `inverted bbox is rejected`() {
        expectProblem(config(minLon = 101.4000, maxLon = 101.3600), ConfigProblem.BBOX_INVERTED)
        expectProblem(config(minLat = 14.1300, maxLat = 14.0950), ConfigProblem.BBOX_INVERTED)
    }

    @Test
    fun `center outside the bbox is rejected`() {
        expectProblem(config(centerLon = 101.5000), ConfigProblem.CENTER_OUTSIDE_BBOX)
        expectProblem(config(centerLat = 14.5000), ConfigProblem.CENTER_OUTSIDE_BBOX)
    }

    @Test
    fun `zoom range must be increasing and capped at 18`() {
        expectProblem(config(minZoom = 18, maxZoom = 13), ConfigProblem.ZOOM_RANGE_INVALID)
        expectProblem(config(minZoom = 13, maxZoom = 20), ConfigProblem.MAX_ZOOM_TOO_HIGH)
    }

    @Test
    fun `style urls that need a key are rejected`() {
        expectProblem(
            config(styleUrl = "https://example.com/style.json?access_token=pk.abc"),
            ConfigProblem.STYLE_NEEDS_API_KEY,
        )
        expectProblem(
            config(styleUrl = "https://example.com/style.json?api_key=123"),
            ConfigProblem.STYLE_NEEDS_API_KEY,
        )
    }

    @Test
    fun `malformed json and missing fields are reported`() {
        expectProblem("not json at all", ConfigProblem.MALFORMED)
        expectProblem("""{"campusName":"x"}""", ConfigProblem.MALFORMED)
    }

    @Test
    fun `default zoom is clamped into the downloaded range`() {
        val tooDeep = CampusConfigParser.parse(config(defaultZoom = 21.0))
        assertEquals(18.0, tooDeep.defaultZoom, 1e-9)
        val tooShallow = CampusConfigParser.parse(config(defaultZoom = 2.0))
        assertEquals(13.0, tooShallow.defaultZoom, 1e-9)
    }

    @Test
    fun `bounding box reports a plausible campus area`() {
        val parsed = CampusConfigParser.parse(config())
        // ~4.3 x 3.9 km for the test box.
        assertTrue(parsed.bbox.areaSquareKilometers > 1.0)
        assertTrue(parsed.bbox.widthMeters > 0.0)
        assertTrue(parsed.bbox.heightMeters > 0.0)
    }
}
