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
import th.ac.kmutnb.prachin.map.data.model.TrackLog

/**
 * The overlays of the walked-path log screen.
 *
 * Three things are drawn, and the screen exists because they have to be seen together:
 * the tracks already walked, the line being walked right now, and the dot doing the
 * walking. Mapping somewhere with no map is mostly the question "have I covered that bit
 * yet", and a list of names cannot answer it.
 *
 * Saved tracks are coloured by the accuracy of the walk that made them, on the same
 * thresholds the GPS point log uses, so green and red mean one thing across the app. A
 * track that was switched off for routing is drawn faded rather than hidden - it is still
 * a record of somewhere someone went.
 */
class PathLogLayerManager {

    private var style: Style? = null

    val isAttached: Boolean get() = style != null

    fun attach(style: Style) {
        this.style = style

        style.addSource(GeoJsonSource(SOURCE_TRACKS, MapGeoJson.EMPTY_COLLECTION))
        style.addSource(GeoJsonSource(SOURCE_RECORDING, MapGeoJson.EMPTY_COLLECTION))
        style.addSource(GeoJsonSource(SOURCE_ACCURACY, MapGeoJson.EMPTY_COLLECTION))
        style.addSource(GeoJsonSource(SOURCE_USER, MapGeoJson.EMPTY_COLLECTION))

        // Order matters: later layers draw on top.
        style.addLayer(
            LineLayer(LAYER_TRACKS, SOURCE_TRACKS).withProperties(
                PropertyFactory.lineColor(qualityColourExpression()),
                PropertyFactory.lineWidth(5f),
                PropertyFactory.lineOpacity(routingOpacityExpression()),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )

        style.addLayer(
            SymbolLayer(LAYER_TRACK_LABELS, SOURCE_TRACKS).withProperties(
                PropertyFactory.textField(Expression.get(PROPERTY_CODE)),
                PropertyFactory.textSize(11f),
                PropertyFactory.textColor(Color.BLACK),
                PropertyFactory.textHaloColor(Color.WHITE),
                PropertyFactory.textHaloWidth(1.4f),
                PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE_CENTER),
                PropertyFactory.textAllowOverlap(false),
            ),
        )

        style.addLayer(
            FillLayer(LAYER_ACCURACY, SOURCE_ACCURACY).withProperties(
                PropertyFactory.fillColor(COLOR_USER),
                PropertyFactory.fillOpacity(0.15f),
            ),
        )

        // The walk in progress, drawn over everything already saved so the walker can see
        // exactly where the new line is going relative to the old ones.
        style.addLayer(
            LineLayer(LAYER_RECORDING, SOURCE_RECORDING).withProperties(
                PropertyFactory.lineColor(COLOR_RECORDING),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )

        style.addLayer(
            CircleLayer(LAYER_USER, SOURCE_USER).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(COLOR_USER),
                PropertyFactory.circleStrokeWidth(2f),
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

    /** The line being walked right now. Empty when nothing is being recorded. */
    fun setRecording(points: List<GeoPoint>) =
        setGeoJson(SOURCE_RECORDING, MapGeoJson.line(points))

    fun setTracks(tracks: List<TrackLog>) = setGeoJson(SOURCE_TRACKS, tracksGeoJson(tracks))

    private fun setGeoJson(sourceId: String, geoJson: String) {
        // A style reload drops every source; ignore updates that arrive in that window,
        // since the next attach() repopulates everything anyway.
        style?.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(geoJson)
    }

    /**
     * Only three properties reach the map: the id to resolve a tap, the code to draw, and
     * the accuracy and routing flag to style by. Everything else about a track belongs in
     * the sheet that opens on tap.
     */
    private fun tracksGeoJson(tracks: List<TrackLog>): String {
        val features = JsonArray()
        tracks.forEach { track ->
            if (track.points.size < 2) return@forEach
            val properties = JsonObject().apply {
                addProperty(PROPERTY_ID, track.id)
                addProperty(PROPERTY_CODE, track.code)
                addProperty(PROPERTY_ACCURACY, track.averageAccuracyMeters)
                addProperty(PROPERTY_ROUTING, track.isUsedForRouting)
            }
            features.add(MapGeoJson.featureOf(MapGeoJson.lineGeometry(track.points), properties))
        }
        return MapGeoJson.documentOf(features)
    }

    private fun qualityColourExpression(): Expression = Expression.step(
        Expression.get(PROPERTY_ACCURACY),
        Expression.color(COLOR_GOOD),
        Expression.stop(GpsFixQuality.GOOD_MAX_METERS, Expression.color(COLOR_FAIR)),
        Expression.stop(GpsFixQuality.FAIR_MAX_METERS, Expression.color(COLOR_POOR)),
    )

    /** Switched off for routing means faded, not gone: it still happened. */
    private fun routingOpacityExpression(): Expression = Expression.switchCase(
        Expression.get(PROPERTY_ROUTING), Expression.literal(0.9f),
        Expression.literal(0.3f),
    )

    companion object {
        const val SOURCE_USER = "pathlog-user"
        const val SOURCE_ACCURACY = "pathlog-accuracy"
        const val SOURCE_RECORDING = "pathlog-recording"
        const val SOURCE_TRACKS = "pathlog-tracks"

        const val LAYER_USER = "pathlog-user-layer"
        const val LAYER_ACCURACY = "pathlog-accuracy-layer"
        const val LAYER_RECORDING = "pathlog-recording-layer"

        /** Queried on tap to work out which saved track was hit. */
        const val LAYER_TRACKS = "pathlog-track-layer"
        const val LAYER_TRACK_LABELS = "pathlog-track-label-layer"

        const val PROPERTY_ID = "id"
        const val PROPERTY_CODE = "code"
        const val PROPERTY_ACCURACY = "accuracyMeters"
        const val PROPERTY_ROUTING = "usedForRouting"

        private const val COLOR_USER = 0xFF2196F3.toInt()
        private const val COLOR_RECORDING = 0xFF1E88E5.toInt()

        /** The same bands as the point log, so a colour means one thing app-wide. */
        private const val COLOR_GOOD = 0xFF2E7D32.toInt()
        private const val COLOR_FAIR = 0xFFEF6C00.toInt()
        private const val COLOR_POOR = 0xFFC62828.toInt()
    }
}
