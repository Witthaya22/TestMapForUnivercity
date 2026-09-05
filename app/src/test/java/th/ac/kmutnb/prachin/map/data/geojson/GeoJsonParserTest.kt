package th.ac.kmutnb.prachin.map.data.geojson

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.Poi
import th.ac.kmutnb.prachin.map.data.model.PoiCategory
import th.ac.kmutnb.prachin.map.navigation.model.PathType
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath

class GeoJsonParserTest {

    // ----------------------------------------------------------------------------------
    // POIs
    // ----------------------------------------------------------------------------------

    @Test
    fun `reads a poi with lon lat ordering`() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [{
                "type": "Feature",
                "geometry": { "type": "Point", "coordinates": [101.3800, 14.1100] },
                "properties": {
                  "id": "gate_1",
                  "name": "ประตูหน้ามอ 1",
                  "shortName": "หน้ามอ 1",
                  "category": "gate",
                  "order": 1,
                  "description": "ประตูทางเข้าหลัก",
                  "note": "ช่วงเย็นรถติด",
                  "icon": "ic_gate"
                }
              }]
            }
        """.trimIndent()

        val result = GeoJsonParser.parsePois(json)
        assertTrue(result.issues.isEmpty())
        val poi = result.items.single()

        assertEquals("gate_1", poi.id)
        assertEquals("ประตูหน้ามอ 1", poi.name)
        assertEquals("หน้ามอ 1", poi.label)
        assertEquals(PoiCategory.GATE, poi.category)
        assertEquals(1, poi.order)
        // The whole point of the test: 101 is the longitude, 14 the latitude.
        assertEquals(14.1100, poi.point.lat, 1e-9)
        assertEquals(101.3800, poi.point.lon, 1e-9)
    }

    @Test
    fun `unknown category falls back to custom`() {
        val result = GeoJsonParser.parsePois(poiJson(category = "temple"))
        assertEquals(PoiCategory.CUSTOM, result.items.single().category)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `features without an id or name are skipped and reported`() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [
                { "type": "Feature",
                  "geometry": { "type": "Point", "coordinates": [101.38, 14.11] },
                  "properties": { "name": "ไม่มี id" } },
                { "type": "Feature",
                  "geometry": { "type": "Point", "coordinates": [101.38, 14.11] },
                  "properties": { "id": "no_name" } }
              ]
            }
        """.trimIndent()

        val result = GeoJsonParser.parsePois(json)
        assertTrue(result.items.isEmpty())
        assertEquals(
            listOf(GeoJsonIssue.Reason.MISSING_ID, GeoJsonIssue.Reason.MISSING_NAME),
            result.issues.map { it.reason },
        )
    }

    @Test
    fun `duplicate ids keep the first and report the rest`() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [
                { "type": "Feature",
                  "geometry": { "type": "Point", "coordinates": [101.38, 14.11] },
                  "properties": { "id": "gate_1", "name": "แรก" } },
                { "type": "Feature",
                  "geometry": { "type": "Point", "coordinates": [101.39, 14.12] },
                  "properties": { "id": "gate_1", "name": "ซ้ำ" } }
              ]
            }
        """.trimIndent()

        val result = GeoJsonParser.parsePois(json)
        assertEquals("แรก", result.items.single().name)
        assertEquals(GeoJsonIssue.Reason.DUPLICATE_ID, result.issues.single().reason)
    }

    @Test
    fun `utm coordinates are reported instead of crashing`() {
        // GeoPoint's require() would throw; the parser must catch this first.
        val result = GeoJsonParser.parsePois(poiJson(lon = 731234.5, lat = 1560987.2))
        assertTrue(result.items.isEmpty())
        assertEquals(GeoJsonIssue.Reason.OUT_OF_DEGREE_RANGE, result.issues.single().reason)
    }

    @Test
    fun `a line string where a point is expected is reported`() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [{
                "type": "Feature",
                "geometry": { "type": "LineString", "coordinates": [[101.38, 14.11], [101.39, 14.12]] },
                "properties": { "id": "wrong", "name": "ผิดชนิด" }
              }]
            }
        """.trimIndent()
        val result = GeoJsonParser.parsePois(json)
        assertTrue(result.items.isEmpty())
        assertEquals(GeoJsonIssue.Reason.WRONG_GEOMETRY, result.issues.single().reason)
    }

    @Test
    fun `an empty or malformed document yields nothing rather than throwing`() {
        assertTrue(GeoJsonParser.parsePois("""{"type":"FeatureCollection","features":[]}""").items.isEmpty())
        assertTrue(GeoJsonParser.parsePois("not json").items.isEmpty())
        assertTrue(GeoJsonParser.parsePois("""{"type":"Feature"}""").items.isEmpty())
    }

    // ----------------------------------------------------------------------------------
    // Paths
    // ----------------------------------------------------------------------------------

    @Test
    fun `reads a walking path with its properties`() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [{
                "type": "Feature",
                "geometry": {
                  "type": "LineString",
                  "coordinates": [[101.380, 14.110], [101.381, 14.111], [101.382, 14.112]]
                },
                "properties": {
                  "id": "path_001",
                  "type": "stairs",
                  "name": "บันไดหน้าอาคารเรียน",
                  "oneway": true,
                  "lit": true,
                  "covered": false
                }
              }]
            }
        """.trimIndent()

        val path = GeoJsonParser.parsePaths(json).items.single()
        assertEquals("path_001", path.id)
        assertEquals(PathType.STAIRS, path.type)
        assertEquals(3, path.points.size)
        assertEquals(14.110, path.points.first().lat, 1e-9)
        assertEquals(101.380, path.points.first().lon, 1e-9)
        assertTrue(path.oneway)
        assertTrue(path.lit)
        assertEquals(false, path.covered)
    }

    @Test
    fun `unknown path type is reported`() {
        val json = pathJson(type = "escalator")
        val result = GeoJsonParser.parsePaths(json)
        assertTrue(result.items.isEmpty())
        assertEquals(GeoJsonIssue.Reason.UNKNOWN_PATH_TYPE, result.issues.single().reason)
    }

    @Test
    fun `a line string needs at least two positions`() {
        val json = """
            {
              "type": "FeatureCollection",
              "features": [{
                "type": "Feature",
                "geometry": { "type": "LineString", "coordinates": [[101.38, 14.11]] },
                "properties": { "id": "stub", "type": "footway" }
              }]
            }
        """.trimIndent()
        val result = GeoJsonParser.parsePaths(json)
        assertTrue(result.items.isEmpty())
        assertEquals(GeoJsonIssue.Reason.BAD_COORDINATES, result.issues.single().reason)
    }

    @Test
    fun `defaults apply when optional path properties are absent`() {
        val path = GeoJsonParser.parsePaths(pathJson()).items.single()
        assertEquals(false, path.oneway)
        assertEquals(false, path.lit)
        assertEquals(false, path.covered)
        assertNull(path.name)
    }

    // ----------------------------------------------------------------------------------
    // Round trips
    // ----------------------------------------------------------------------------------

    @Test
    fun `pois survive a write and read round trip`() {
        val original = listOf(
            samplePoi("gate_1", "ประตูหน้ามอ 1", GeoPoint(14.1100, 101.3800), PoiCategory.GATE, order = 1),
            samplePoi("dorm_m", "หอชาย", GeoPoint(14.1150, 101.3830), PoiCategory.DORM, order = 2),
        )
        val restored = GeoJsonParser.parsePois(GeoJsonParser.writePois(original))
        assertTrue(restored.issues.isEmpty())
        assertEquals(original, restored.items)
    }

    @Test
    fun `paths survive a write and read round trip`() {
        val original = listOf(
            WalkPath(
                id = "path_001",
                type = PathType.CROSSING,
                points = listOf(GeoPoint(14.110, 101.380), GeoPoint(14.111, 101.381)),
                name = "ทางม้าลาย",
                oneway = false,
                lit = true,
                covered = false,
            ),
        )
        val restored = GeoJsonParser.parsePaths(GeoJsonParser.writePaths(original))
        assertEquals(original, restored.items)
    }

    @Test
    fun `written geojson stores coordinates as lon lat`() {
        val json = GeoJsonParser.writePois(
            listOf(samplePoi("p", "จุด", GeoPoint(14.11, 101.38), PoiCategory.CUSTOM)),
        )
        assertTrue("expected [lon, lat] in $json", json.contains("[101.38,14.11]"))
    }

    // ----------------------------------------------------------------------------------

    private fun poiJson(
        lon: Double = 101.38,
        lat: Double = 14.11,
        category: String = "gate",
    ) = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": { "type": "Point", "coordinates": [$lon, $lat] },
            "properties": { "id": "p1", "name": "จุดทดสอบ", "category": "$category" }
          }]
        }
    """.trimIndent()

    private fun pathJson(type: String = "footway") = """
        {
          "type": "FeatureCollection",
          "features": [{
            "type": "Feature",
            "geometry": { "type": "LineString", "coordinates": [[101.380, 14.110], [101.381, 14.111]] },
            "properties": { "id": "path_001", "type": "$type" }
          }]
        }
    """.trimIndent()

    private fun samplePoi(
        id: String,
        name: String,
        point: GeoPoint,
        category: PoiCategory,
        order: Int = 0,
    ) = Poi(
        id = id,
        name = name,
        shortName = null,
        category = category,
        order = order,
        description = "รายละเอียด",
        note = "โน้ต",
        icon = null,
        point = point,
        isUserCreated = false,
        gpsAccuracy = null,
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
    )
}
