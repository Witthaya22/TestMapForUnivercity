package th.ac.kmutnb.prachin.map.map

import android.graphics.Color
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.model.GpsFixQuality
import th.ac.kmutnb.prachin.map.data.model.GpsPoint

/**
 * The overlays of the GPS point log screen.
 *
 * Separate from [MapLayerManager], which carries POIs, the walking network and the active
 * route. None of that belongs on this screen: the surveyor is reading their own position
 * and their own measurements, and a campus full of pins would hide both. What is here is
 * only the four things that matter while walking - where the device thinks it is, how sure
 * it is, where it has been, and what has already been recorded.
 *
 * Marker colour is driven by the fix accuracy stored on each feature, so the quality of a
 * measurement is visible on the map rather than only in a list. That is the whole reason
 * this screen is a map at all: a row of numbers cannot show that every poor reading came
 * from the same corner behind a building.
 */
class GpsLogLayerManager {

    private var style: Style? = null

    val isAttached: Boolean get() = style != null

    fun attach(style: Style) {
        this.style = style

        style.addSource(GeoJsonSource(SOURCE_TRAIL, MapGeoJson.EMPTY_COLLECTION))
        style.addSource(GeoJsonSource(SOURCE_ACCURACY, MapGeoJson.EMPTY_COLLECTION))
        style.addSource(GeoJsonSource(SOURCE_POINTS, MapGeoJson.EMPTY_COLLECTION))
        style.addSource(GeoJsonSource(SOURCE_USER, MapGeoJson.EMPTY_COLLECTION))

        // Order matters: later layers draw on top.
        style.addLayer(
            FillLayer(LAYER_ACCURACY, SOURCE_ACCURACY).withProperties(
                PropertyFactory.fillColor(COLOR_USER),
                PropertyFactory.fillOpacity(0.15f),
            ),
        )

        style.addLayer(
            LineLayer(LAYER_TRAIL, SOURCE_TRAIL).withProperties(
                PropertyFactory.lineColor(COLOR_TRAIL),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineOpacity(0.8f),
                PropertyFactory.lineDasharray(arrayOf(1f, 1.6f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )

        style.addLayer(
            CircleLayer(LAYER_POINTS, SOURCE_POINTS).withProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor(qualityColourExpression()),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                // A recorded point is a coordinate, not a place, so a symmetrical dot sits
                // on it - no pin anchor to get wrong (docs/ACCURACY.md A7).
            ),
        )

        style.addLayer(
            SymbolLayer(LAYER_POINT_LABELS, SOURCE_POINTS).withProperties(
                PropertyFactory.textField(Expression.get(PROPERTY_CODE)),
                PropertyFactory.textFont(style.labelFontStack()),
                PropertyFactory.textSize(11f),
                PropertyFactory.textOffset(arrayOf(0f, 1.2f)),
                PropertyFactory.textColor(COLOR_LABEL),
                PropertyFactory.textHaloColor(Color.WHITE),
                PropertyFactory.textHaloWidth(1.4f),
                PropertyFactory.textAllowOverlap(false),
            ),
        )

        style.addLayer(
            CircleLayer(LAYER_USER, SOURCE_USER).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(COLOR_USER),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
            ),
        )
    }

    fun detach() {
        style = null
    }

    // ----------------------------------------------------------------------------------
    // Content
    // ----------------------------------------------------------------------------------

    fun setUserLocation(point: GeoPoint?, accuracyMeters: Float) {
        setGeoJson(SOURCE_USER, MapGeoJson.singlePoint(point))
        setGeoJson(SOURCE_ACCURACY, MapGeoJson.accuracyCircle(point, accuracyMeters))
    }

    /** The breadcrumb of where the device has walked since the screen was opened. */
    fun setTrail(points: List<GeoPoint>) = setGeoJson(SOURCE_TRAIL, MapGeoJson.line(points))

    fun setPoints(points: List<GpsPoint>) = setGeoJson(SOURCE_POINTS, pointsGeoJson(points))

    private fun setGeoJson(sourceId: String, geoJson: String) {
        // A style reload drops every source; ignore updates that arrive in that window,
        // since the next attach() repopulates everything anyway.
        style?.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(geoJson)
    }

    /**
     * Only three properties reach the map: the id to resolve a tap, the code to draw, and
     * the accuracy to colour by. Everything else about a measurement belongs in the sheet
     * that opens on tap, not in a document rebuilt on every fix.
     */
    private fun pointsGeoJson(points: List<GpsPoint>): String {
        val features = JsonArray()
        points.forEach { gpsPoint ->
            val properties = JsonObject().apply {
                addProperty(PROPERTY_ID, gpsPoint.id)
                addProperty(PROPERTY_CODE, gpsPoint.code)
                addProperty(PROPERTY_ACCURACY, gpsPoint.accuracyMeters)
            }
            features.add(
                MapGeoJson.featureOf(MapGeoJson.pointGeometry(gpsPoint.point), properties),
            )
        }
        return MapGeoJson.documentOf(features)
    }

    private fun qualityColourExpression(): Expression = Expression.step(
        Expression.get(PROPERTY_ACCURACY),
        Expression.color(COLOR_GOOD),
        Expression.stop(GpsFixQuality.GOOD_MAX_METERS, Expression.color(COLOR_FAIR)),
        Expression.stop(GpsFixQuality.FAIR_MAX_METERS, Expression.color(COLOR_POOR)),
    )

    companion object {
        const val SOURCE_USER = "gpslog-user"
        const val SOURCE_ACCURACY = "gpslog-accuracy"
        const val SOURCE_TRAIL = "gpslog-trail"
        const val SOURCE_POINTS = "gpslog-points"

        const val LAYER_USER = "gpslog-user-layer"
        const val LAYER_ACCURACY = "gpslog-accuracy-layer"
        const val LAYER_TRAIL = "gpslog-trail-layer"

        /** Queried on tap to work out which recorded point was hit. */
        const val LAYER_POINTS = "gpslog-point-layer"
        const val LAYER_POINT_LABELS = "gpslog-point-label-layer"

        const val PROPERTY_ID = "id"
        const val PROPERTY_CODE = "code"
        const val PROPERTY_ACCURACY = "accuracyMeters"

        private const val COLOR_USER = 0xFF2196F3.toInt()
        private const val COLOR_TRAIL = 0xFF1E88E5.toInt()
        private const val COLOR_LABEL = 0xFF1A1C1E.toInt()

        /** Matches [GpsFixQuality]: green under 5 m, amber under 10 m, red beyond. */
        private const val COLOR_GOOD = 0xFF2E7D32.toInt()
        private const val COLOR_FAIR = 0xFFEF6C00.toInt()
        private const val COLOR_POOR = 0xFFC62828.toInt()
    }
}
