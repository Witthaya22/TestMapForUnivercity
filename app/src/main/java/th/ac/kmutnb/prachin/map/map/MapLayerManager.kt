package th.ac.kmutnb.prachin.map.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.Poi
import th.ac.kmutnb.prachin.map.data.model.PoiCategory
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath

/**
 * Owns every source and layer the app adds on top of the base style.
 *
 * All overlays are GeoJSON sources whose contents are swapped wholesale on update. At campus
 * scale the documents are a few kilobytes, so replacing them is simpler and no slower than
 * diffing features, and it keeps the map a pure function of the app's state.
 */
class MapLayerManager(private val context: Context) {

    private var style: Style? = null

    /**
     * The font stack to label POIs with.
     *
     * Taken from a symbol layer already in the style rather than hardcoded: only fonts the
     * style ships glyphs for will render, and a name the offline pack never downloaded makes
     * every label silently vanish once the device is offline.
     */
    private var labelFont: Array<String> = DEFAULT_FONT

    val isAttached: Boolean get() = style != null

    /** Adds the sources, images and layers to a freshly loaded style. */
    fun attach(style: Style) {
        this.style = style
        labelFont = style.layers
            .filterIsInstance<SymbolLayer>()
            .firstNotNullOfOrNull { layer -> layer.textFont?.value?.takeIf { it.isNotEmpty() } }
            ?: DEFAULT_FONT

        addPinImage(style)

        style.addSource(GeoJsonSource(SOURCE_PATHS, MapGeoJson.EMPTY_COLLECTION))
        style.addSource(GeoJsonSource(SOURCE_SUGGESTION, MapGeoJson.EMPTY_COLLECTION))
        style.addSource(GeoJsonSource(SOURCE_ROUTE_TRAVELLED, MapGeoJson.EMPTY_COLLECTION))
        style.addSource(GeoJsonSource(SOURCE_ROUTE_REMAINING, MapGeoJson.EMPTY_COLLECTION))
        style.addSource(GeoJsonSource(SOURCE_ACCURACY, MapGeoJson.EMPTY_COLLECTION))
        style.addSource(GeoJsonSource(SOURCE_USER, MapGeoJson.EMPTY_COLLECTION))
        style.addSource(GeoJsonSource(SOURCE_POIS, MapGeoJson.EMPTY_COLLECTION))

        // Order matters: later layers draw on top.
        style.addLayer(
            LineLayer(LAYER_PATHS, SOURCE_PATHS).withProperties(
                PropertyFactory.lineColor(COLOR_PATH_NETWORK),
                PropertyFactory.lineWidth(2f),
                PropertyFactory.lineOpacity(0.45f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )

        style.addLayer(
            LineLayer(LAYER_SUGGESTION, SOURCE_SUGGESTION).withProperties(
                PropertyFactory.lineColor(COLOR_WARNING),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            ),
        )

        style.addLayer(
            LineLayer(LAYER_ROUTE_TRAVELLED, SOURCE_ROUTE_TRAVELLED).withProperties(
                PropertyFactory.lineColor(COLOR_ROUTE_TRAVELLED),
                PropertyFactory.lineWidth(7f),
                PropertyFactory.lineOpacity(0.7f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )

        style.addLayer(
            LineLayer(LAYER_ROUTE_REMAINING, SOURCE_ROUTE_REMAINING).withProperties(
                PropertyFactory.lineColor(COLOR_ROUTE),
                PropertyFactory.lineWidth(7f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )

        style.addLayer(
            FillLayer(LAYER_ACCURACY, SOURCE_ACCURACY).withProperties(
                PropertyFactory.fillColor(COLOR_USER),
                PropertyFactory.fillOpacity(0.15f),
            ),
        )

        style.addLayer(
            CircleLayer(LAYER_USER, SOURCE_USER).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(COLOR_USER),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                // The dot is symmetrical, so its centre is the coordinate. A pin would need
                // ICON_ANCHOR_BOTTOM instead - see docs/ACCURACY.md A7.
            ),
        )

        style.addLayer(
            SymbolLayer(LAYER_POIS, SOURCE_POIS).withProperties(
                PropertyFactory.iconImage(IMAGE_PIN),
                PropertyFactory.iconSize(1f),
                PropertyFactory.iconAllowOverlap(true),
                // The pin's tip is at the bottom of the drawable, so the icon must hang above
                // its coordinate rather than straddle it.
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconColor(categoryColourExpression()),
                PropertyFactory.textField(Expression.get(MapGeoJson.PROPERTY_LABEL)),
                PropertyFactory.textFont(labelFont),
                PropertyFactory.textSize(12f),
                PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                PropertyFactory.textOffset(arrayOf(0f, 0.4f)),
                PropertyFactory.textColor(COLOR_LABEL),
                PropertyFactory.textHaloColor(Color.WHITE),
                PropertyFactory.textHaloWidth(1.4f),
                PropertyFactory.textAllowOverlap(false),
                // Drop the label rather than the pin when space runs out.
                PropertyFactory.textOptional(true),
            ),
        )
    }

    fun detach() {
        style = null
    }

    // ----------------------------------------------------------------------------------
    // Content
    // ----------------------------------------------------------------------------------

    fun setPois(pois: List<Poi>, unroutableIds: Set<String>) =
        setGeoJson(SOURCE_POIS, MapGeoJson.pois(pois, unroutableIds))

    fun setWalkingNetwork(paths: List<WalkPath>, visible: Boolean) {
        setGeoJson(SOURCE_PATHS, if (visible) MapGeoJson.paths(paths) else MapGeoJson.EMPTY_COLLECTION)
    }

    /**
     * Splits the route into what is already walked and what remains, so progress is visible
     * at a glance without a separate indicator.
     */
    fun setRoute(travelled: List<GeoPoint>, remaining: List<GeoPoint>) {
        setGeoJson(SOURCE_ROUTE_TRAVELLED, MapGeoJson.line(travelled))
        setGeoJson(SOURCE_ROUTE_REMAINING, MapGeoJson.line(remaining))
    }

    fun clearRoute() {
        setGeoJson(SOURCE_ROUTE_TRAVELLED, MapGeoJson.EMPTY_COLLECTION)
        setGeoJson(SOURCE_ROUTE_REMAINING, MapGeoJson.EMPTY_COLLECTION)
    }

    fun setUserLocation(point: GeoPoint?, accuracyMeters: Float) {
        setGeoJson(SOURCE_USER, MapGeoJson.singlePoint(point))
        setGeoJson(SOURCE_ACCURACY, MapGeoJson.accuracyCircle(point, accuracyMeters))
    }

    /** The dashed line from a rejected tap to the nearest point on a path. */
    fun setSuggestionLine(from: GeoPoint?, to: GeoPoint?) {
        val geoJson = if (from == null || to == null) {
            MapGeoJson.EMPTY_COLLECTION
        } else {
            MapGeoJson.line(listOf(from, to))
        }
        setGeoJson(SOURCE_SUGGESTION, geoJson)
    }

    private fun setGeoJson(sourceId: String, geoJson: String) {
        // A style reload drops every source; ignore updates that arrive in that window rather
        // than crashing, since the next attach() repopulates everything anyway.
        style?.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(geoJson)
    }

    private fun addPinImage(style: Style) {
        val drawable = ContextCompat.getDrawable(context, R.drawable.ic_map_pin) ?: return
        val density = context.resources.displayMetrics.density
        val bitmap: Bitmap = drawable.toBitmap(
            width = (PIN_WIDTH_DP * density).toInt(),
            height = (PIN_HEIGHT_DP * density).toInt(),
        )
        // sdf = true makes the pin tintable, so one drawable covers all eight categories.
        style.addImage(IMAGE_PIN, bitmap, true)
    }

    private fun categoryColourExpression(): Expression = Expression.match(
        Expression.get(MapGeoJson.PROPERTY_CATEGORY),
        Expression.color(COLOR_CATEGORY_DEFAULT),
        *PoiCategory.entries.map { category ->
            Expression.stop(category.id, Expression.color(colourFor(category)))
        }.toTypedArray(),
    )

    private fun colourFor(category: PoiCategory): Int = when (category) {
        PoiCategory.GATE -> 0xFFC62828.toInt()
        PoiCategory.DORM -> 0xFF6A1B9A.toInt()
        PoiCategory.ACADEMIC -> 0xFF1B3A6B.toInt()
        PoiCategory.CANTEEN -> 0xFFEF6C00.toInt()
        PoiCategory.SPORT -> 0xFF2E7D32.toInt()
        PoiCategory.PARKING -> 0xFF455A64.toInt()
        PoiCategory.SERVICE -> 0xFF00838F.toInt()
        PoiCategory.CUSTOM -> COLOR_CATEGORY_DEFAULT
    }

    companion object {
        const val SOURCE_POIS = "kmutnb-pois"
        const val SOURCE_PATHS = "kmutnb-paths"
        const val SOURCE_ROUTE_REMAINING = "kmutnb-route-remaining"
        const val SOURCE_ROUTE_TRAVELLED = "kmutnb-route-travelled"
        const val SOURCE_USER = "kmutnb-user"
        const val SOURCE_ACCURACY = "kmutnb-accuracy"
        const val SOURCE_SUGGESTION = "kmutnb-suggestion"

        /** Queried on tap to work out which POI was hit. */
        const val LAYER_POIS = "kmutnb-poi-layer"
        const val LAYER_PATHS = "kmutnb-path-layer"
        const val LAYER_ROUTE_REMAINING = "kmutnb-route-remaining-layer"
        const val LAYER_ROUTE_TRAVELLED = "kmutnb-route-travelled-layer"
        const val LAYER_USER = "kmutnb-user-layer"
        const val LAYER_ACCURACY = "kmutnb-accuracy-layer"
        const val LAYER_SUGGESTION = "kmutnb-suggestion-layer"

        private const val IMAGE_PIN = "kmutnb-pin"
        private const val PIN_WIDTH_DP = 24
        private const val PIN_HEIGHT_DP = 32

        private val DEFAULT_FONT = arrayOf("Noto Sans Regular")

        private const val COLOR_ROUTE = 0xFF1E88E5.toInt()
        private const val COLOR_ROUTE_TRAVELLED = 0xFF9E9E9E.toInt()
        private const val COLOR_USER = 0xFF2196F3.toInt()
        private const val COLOR_WARNING = 0xFFF57C00.toInt()
        private const val COLOR_PATH_NETWORK = 0xFF7E57C2.toInt()
        private const val COLOR_LABEL = 0xFF1A1C1E.toInt()
        private const val COLOR_CATEGORY_DEFAULT = 0xFF5D4037.toInt()
    }
}
