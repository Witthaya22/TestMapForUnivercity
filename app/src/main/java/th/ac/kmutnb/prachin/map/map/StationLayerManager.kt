package th.ac.kmutnb.prachin.map.map

import android.graphics.Color
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import th.ac.kmutnb.prachin.map.data.khaoyai.Trail

/**
 * The trail being walked on the main map: its line, when known, and a numbered marker for
 * each interpretation station that has a point. Tapping a marker opens that station.
 *
 * Content pushed before the style is ready is kept and replayed on [attach], the same way
 * `HazardLayerManager` does it.
 */
class StationLayerManager {

    private var style: Style? = null
    private val pending = HashMap<String, String>()

    fun attach(style: Style) {
        this.style = style
        style.addSource(GeoJsonSource(SOURCE_LINE, MapGeoJson.EMPTY_COLLECTION))
        style.addSource(GeoJsonSource(SOURCE_STATIONS, MapGeoJson.EMPTY_COLLECTION))

        style.addLayer(
            LineLayer(LAYER_LINE, SOURCE_LINE).withProperties(
                PropertyFactory.lineColor(COLOR_TRAIL),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineOpacity(0.75f),
                PropertyFactory.lineDasharray(arrayOf(2f, 1.2f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            ),
        )
        style.addLayer(
            CircleLayer(LAYER_STATIONS, SOURCE_STATIONS).withProperties(
                PropertyFactory.circleRadius(11f),
                PropertyFactory.circleColor(COLOR_STATION),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
            ),
        )
        style.addLayer(
            SymbolLayer(LAYER_LABELS, SOURCE_STATIONS).withProperties(
                PropertyFactory.textField("{$PROPERTY_LABEL}"),
                // Digits only, in a font stack the offline pack carries: no emoji glyphs.
                PropertyFactory.textFont(style.labelFontStack()),
                PropertyFactory.textSize(12f),
                PropertyFactory.textColor(Color.WHITE),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
            ),
        )
        pending.forEach { (sourceId, geoJson) -> style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(geoJson) }
    }

    fun detach() {
        style = null
    }

    /** Draws [trail]'s line and stations; null clears the map of any trail. */
    fun setTrail(trail: Trail?) {
        val line = trail?.path?.takeIf { it.size >= 2 }
        setGeoJson(SOURCE_LINE, line?.let(MapGeoJson::line) ?: MapGeoJson.EMPTY_COLLECTION)

        val features = JsonArray()
        trail?.stations?.forEachIndexed { index, station ->
            val point = station.point ?: return@forEachIndexed
            features.add(
                MapGeoJson.featureOf(
                    MapGeoJson.pointGeometry(point),
                    JsonObject().apply {
                        addProperty(PROPERTY_ID, station.id)
                        addProperty(PROPERTY_LABEL, (index + 1).toString())
                    },
                ),
            )
        }
        setGeoJson(SOURCE_STATIONS, MapGeoJson.documentOf(features))
    }

    private fun setGeoJson(sourceId: String, geoJson: String) {
        pending[sourceId] = geoJson
        style?.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(geoJson)
    }

    companion object {
        private const val SOURCE_LINE = "khaoyai-trail-line"
        private const val SOURCE_STATIONS = "khaoyai-trail-stations"
        private const val LAYER_LINE = "khaoyai-trail-line-layer"
        const val LAYER_STATIONS = "khaoyai-trail-station-layer"
        private const val LAYER_LABELS = "khaoyai-trail-station-label-layer"
        const val PROPERTY_ID = "id"
        private const val PROPERTY_LABEL = "label"

        private const val COLOR_TRAIL = 0xFF8D5A2B.toInt()
        private const val COLOR_STATION = 0xFFE3A72F.toInt()
    }
}
