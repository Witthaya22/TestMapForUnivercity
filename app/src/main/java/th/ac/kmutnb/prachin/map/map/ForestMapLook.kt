package th.ac.kmutnb.prachin.map.map

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory

/**
 * Recolours the OpenFreeMap "liberty" basemap for a nature trail app: deeper greens for
 * woods and parks, clearer water, a warm paper background and footpaths drawn as brown
 * trails rather than near-invisible white dashes.
 *
 * Only paint properties of layers the style already has are changed, at runtime, so the
 * offline pack, its tiles and glyphs stay exactly as downloaded and nothing is fetched.
 * Layer ids are those of the liberty style; one that is missing - another style, or a
 * future liberty - is simply left as it is.
 */
object ForestMapLook {

    private const val PAPER = "#F4F1E6"
    private const val PARK = "#BFDDAE"
    private const val WOOD = "#7DB875"
    private const val GRASS = "#C8E2A6"
    private const val WATER = "#8EC5EC"
    private const val WATERWAY = "#6FAEDD"
    private const val TRAIL = "#9C6B3F"
    private const val BUILDING = "#E2DACB"

    fun apply(style: Style) {
        style.getLayerAs<BackgroundLayer>("background")
            ?.setProperties(PropertyFactory.backgroundColor(PAPER))

        fill(style, "park", PARK, 0.85f)
        fill(style, "landcover_wood", WOOD, 0.55f)
        fill(style, "landcover_grass", GRASS, 0.6f)
        fill(style, "water", WATER, 1f)
        fill(style, "building", BUILDING, 1f)
        style.getLayerAs<FillExtrusionLayer>("building-3d")
            ?.setProperties(PropertyFactory.fillExtrusionColor(BUILDING))

        listOf("waterway_river", "waterway_other", "waterway_tunnel").forEach { id ->
            style.getLayerAs<LineLayer>(id)?.setProperties(PropertyFactory.lineColor(WATERWAY))
        }
        // Footways are what this app walks on; make them read as trails on the paper.
        listOf("road_path_pedestrian", "bridge_path_pedestrian", "tunnel_path_pedestrian").forEach { id ->
            style.getLayerAs<LineLayer>(id)?.setProperties(
                PropertyFactory.lineColor(TRAIL),
                PropertyFactory.lineOpacity(0.85f),
            )
        }
    }

    private fun fill(style: Style, id: String, colour: String, opacity: Float) {
        style.getLayerAs<FillLayer>(id)?.setProperties(
            PropertyFactory.fillColor(colour),
            PropertyFactory.fillOpacity(opacity),
        )
    }
}
