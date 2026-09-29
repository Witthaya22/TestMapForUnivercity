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
 * The overlays of the track editor: one track, vertex by vertex, over the real basemap.
 *
 * What the walker needs to judge is "is this point where the path is", so every vertex is
 * drawn with the circle its own fix accuracy describes - a point whose circle covers the
 * path could be right, one whose circle misses it by metres was not. A moved vertex also
 * shows where the receiver put it, joined by a dashed line, so a correction is never
 * mistaken for a measurement.
 *
 * Colours use the same 5/10 m bands as everywhere else in the app. Grey means the accuracy
 * was never recorded (a track from before it was); purple means placed by hand.
 */
class TrackEditLayerManager {

    private var style: Style? = null

    /** Replayed when the style arrives, as in [PathLogLayerManager]. */
    private val pending = HashMap<String, String>()

    val isAttached: Boolean get() = style != null

    fun attach(style: Style) {
        this.style = style
        listOf(
            SOURCE_ACCURACY, SOURCE_ORIGINAL_LINKS, SOURCE_ORIGINALS, SOURCE_LINE,
            SOURCE_WALK, SOURCE_PREVIEW, SOURCE_VERTICES, SOURCE_USER_ACCURACY, SOURCE_USER,
        ).forEach { style.addSource(GeoJsonSource(it, MapGeoJson.EMPTY_COLLECTION)) }

        // Later layers draw on top.
        style.addLayer(
            FillLayer(LAYER_ACCURACY, SOURCE_ACCURACY).withProperties(
                PropertyFactory.fillColor(vertexColourExpression()),
                PropertyFactory.fillOpacity(0.12f),
            ),
        )
        style.addLayer(
            LineLayer(LAYER_ACCURACY_OUTLINE, SOURCE_ACCURACY).withProperties(
                PropertyFactory.lineColor(vertexColourExpression()),
                PropertyFactory.lineWidth(1f),
                PropertyFactory.lineOpacity(0.6f),
            ),
        )
        style.addLayer(
            LineLayer(LAYER_LINE, SOURCE_LINE).withProperties(
                PropertyFactory.lineColor(COLOR_LINE),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineOpacity(0.85f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        style.addLayer(
            LineLayer(LAYER_ORIGINAL_LINKS, SOURCE_ORIGINAL_LINKS).withProperties(
                PropertyFactory.lineColor(COLOR_ORIGINAL),
                PropertyFactory.lineWidth(2f),
                PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
            ),
        )
        style.addLayer(
            CircleLayer(LAYER_ORIGINALS, SOURCE_ORIGINALS).withProperties(
                PropertyFactory.circleRadius(5f),
                PropertyFactory.circleColor(Color.TRANSPARENT),
                PropertyFactory.circleStrokeColor(COLOR_ORIGINAL),
                PropertyFactory.circleStrokeWidth(2f),
            ),
        )
        style.addLayer(
            LineLayer(LAYER_WALK, SOURCE_WALK).withProperties(
                PropertyFactory.lineColor(COLOR_WALK),
                PropertyFactory.lineWidth(4f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
        style.addLayer(
            LineLayer(LAYER_PREVIEW, SOURCE_PREVIEW).withProperties(
                PropertyFactory.lineColor(COLOR_PREVIEW),
                PropertyFactory.lineWidth(3f),
                PropertyFactory.lineDasharray(arrayOf(1.5f, 1.5f)),
            ),
        )
        style.addLayer(
            CircleLayer(LAYER_VERTICES, SOURCE_VERTICES).withProperties(
                PropertyFactory.circleRadius(
                    Expression.switchCase(
                        Expression.get(PROPERTY_SELECTED), Expression.literal(10f),
                        Expression.literal(6.5f),
                    ),
                ),
                PropertyFactory.circleColor(vertexColourExpression()),
                PropertyFactory.circleStrokeColor(
                    Expression.switchCase(
                        Expression.get(PROPERTY_SELECTED), Expression.color(Color.BLACK),
                        Expression.color(Color.WHITE),
                    ),
                ),
                PropertyFactory.circleStrokeWidth(
                    Expression.switchCase(
                        Expression.get(PROPERTY_SELECTED), Expression.literal(3f),
                        Expression.literal(1.5f),
                    ),
                ),
            ),
        )
        style.addLayer(
            SymbolLayer(LAYER_VERTEX_LABELS, SOURCE_VERTICES).withProperties(
                PropertyFactory.textField(Expression.get(PROPERTY_LABEL)),
                PropertyFactory.textFont(style.labelFontStack()),
                PropertyFactory.textSize(12f),
                PropertyFactory.textOffset(arrayOf(0f, -1.4f)),
                PropertyFactory.textColor(Color.BLACK),
                PropertyFactory.textHaloColor(Color.WHITE),
                PropertyFactory.textHaloWidth(1.6f),
                PropertyFactory.textAllowOverlap(false),
            ),
        )
        style.addLayer(
            FillLayer(LAYER_USER_ACCURACY, SOURCE_USER_ACCURACY).withProperties(
                PropertyFactory.fillColor(COLOR_USER),
                PropertyFactory.fillOpacity(0.15f),
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

        pending.forEach { (sourceId, geoJson) ->
            style.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(geoJson)
        }
    }

    fun detach() {
        style = null
    }

    // ----------------------------------------------------------------------------------
    // Content
    // ----------------------------------------------------------------------------------

    /** The track being edited, with [selected] (an index, or null) drawn larger. */
    fun setTrack(track: TrackLog, selected: Int?, showAccuracy: Boolean) {
        setGeoJson(SOURCE_LINE, MapGeoJson.line(track.points))

        val vertices = JsonArray()
        val circles = JsonArray()
        val originals = JsonArray()
        val links = JsonArray()
        track.points.forEachIndexed { index, point ->
            val info = track.vertexInfoAt(index)
            val properties = JsonObject().apply {
                addProperty(PROPERTY_INDEX, index)
                addProperty(PROPERTY_LABEL, (index + 1).toString())
                addProperty(PROPERTY_ACCURACY, info.accuracyMeters ?: UNKNOWN_ACCURACY)
                addProperty(PROPERTY_SOURCE, info.source.id)
                addProperty(PROPERTY_SELECTED, index == selected)
            }
            vertices.add(MapGeoJson.featureOf(MapGeoJson.pointGeometry(point), properties))

            val accuracy = info.accuracyMeters
            if (showAccuracy && accuracy != null && accuracy > 0f) {
                circles.add(
                    MapGeoJson.featureOf(
                        MapGeoJson.circleGeometry(point, accuracy.toDouble()),
                        properties,
                    ),
                )
            }
            info.originalPoint?.let { original ->
                originals.add(
                    MapGeoJson.featureOf(MapGeoJson.pointGeometry(original), JsonObject()),
                )
                links.add(
                    MapGeoJson.featureOf(
                        MapGeoJson.lineGeometry(listOf(original, point)),
                        JsonObject(),
                    ),
                )
            }
        }
        setGeoJson(SOURCE_VERTICES, MapGeoJson.documentOf(vertices))
        setGeoJson(SOURCE_ACCURACY, MapGeoJson.documentOf(circles))
        setGeoJson(SOURCE_ORIGINALS, MapGeoJson.documentOf(originals))
        setGeoJson(SOURCE_ORIGINAL_LINKS, MapGeoJson.documentOf(links))
    }

    /** Dashed segments showing what confirming the current edit would draw. */
    fun setPreview(segments: List<List<GeoPoint>>) {
        val features = JsonArray()
        segments.filter { it.size >= 2 }.forEach {
            features.add(MapGeoJson.featureOf(MapGeoJson.lineGeometry(it), JsonObject()))
        }
        setGeoJson(SOURCE_PREVIEW, MapGeoJson.documentOf(features))
    }

    /** The continuation being walked, joined to the old end. */
    fun setWalk(points: List<GeoPoint>) = setGeoJson(SOURCE_WALK, MapGeoJson.line(points))

    fun setUserLocation(point: GeoPoint?, accuracyMeters: Float) {
        setGeoJson(SOURCE_USER, MapGeoJson.singlePoint(point))
        setGeoJson(SOURCE_USER_ACCURACY, MapGeoJson.accuracyCircle(point, accuracyMeters))
    }

    private fun setGeoJson(sourceId: String, geoJson: String) {
        pending[sourceId] = geoJson
        style?.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(geoJson)
    }

    /** Placed = purple, unknown = grey, otherwise the app-wide 5/10 m bands. */
    private fun vertexColourExpression(): Expression = Expression.switchCase(
        Expression.eq(Expression.get(PROPERTY_SOURCE), Expression.literal("placed")),
        Expression.color(COLOR_PLACED),
        Expression.lt(Expression.get(PROPERTY_ACCURACY), Expression.literal(0f)),
        Expression.color(COLOR_UNKNOWN),
        Expression.step(
            Expression.get(PROPERTY_ACCURACY),
            Expression.color(COLOR_GOOD),
            Expression.stop(GpsFixQuality.GOOD_MAX_METERS, Expression.color(COLOR_FAIR)),
            Expression.stop(GpsFixQuality.FAIR_MAX_METERS, Expression.color(COLOR_POOR)),
        ),
    )

    companion object {
        private const val SOURCE_ACCURACY = "trackedit-accuracy"
        private const val SOURCE_ORIGINALS = "trackedit-originals"
        private const val SOURCE_ORIGINAL_LINKS = "trackedit-original-links"
        private const val SOURCE_LINE = "trackedit-line"
        private const val SOURCE_WALK = "trackedit-walk"
        private const val SOURCE_PREVIEW = "trackedit-preview"
        private const val SOURCE_VERTICES = "trackedit-vertices"
        private const val SOURCE_USER = "trackedit-user"
        private const val SOURCE_USER_ACCURACY = "trackedit-user-accuracy"

        private const val LAYER_ACCURACY = "trackedit-accuracy-layer"
        private const val LAYER_ACCURACY_OUTLINE = "trackedit-accuracy-outline-layer"
        private const val LAYER_ORIGINALS = "trackedit-originals-layer"
        private const val LAYER_ORIGINAL_LINKS = "trackedit-original-links-layer"
        private const val LAYER_LINE = "trackedit-line-layer"
        private const val LAYER_WALK = "trackedit-walk-layer"
        private const val LAYER_PREVIEW = "trackedit-preview-layer"
        private const val LAYER_VERTEX_LABELS = "trackedit-vertex-label-layer"
        private const val LAYER_USER = "trackedit-user-layer"
        private const val LAYER_USER_ACCURACY = "trackedit-user-accuracy-layer"

        /** Queried on tap to work out which vertex was hit. */
        const val LAYER_VERTICES = "trackedit-vertex-layer"
        const val PROPERTY_INDEX = "index"

        private const val PROPERTY_LABEL = "label"
        private const val PROPERTY_ACCURACY = "accuracyMeters"
        private const val PROPERTY_SOURCE = "source"
        private const val PROPERTY_SELECTED = "selected"

        /** Stands in for "not recorded" so the colour expression can test for it. */
        private const val UNKNOWN_ACCURACY = -1f

        private const val COLOR_LINE = 0xFF1565C0.toInt()
        private const val COLOR_WALK = 0xFF00ACC1.toInt()
        private const val COLOR_PREVIEW = 0xFF6A1B9A.toInt()
        private const val COLOR_ORIGINAL = 0xFF616161.toInt()
        private const val COLOR_USER = 0xFF2196F3.toInt()
        private const val COLOR_PLACED = 0xFF8E24AA.toInt()
        private const val COLOR_UNKNOWN = 0xFF9E9E9E.toInt()

        /** The same bands as the point log, so a colour means one thing app-wide. */
        private const val COLOR_GOOD = 0xFF2E7D32.toInt()
        private const val COLOR_FAIR = 0xFFEF6C00.toInt()
        private const val COLOR_POOR = 0xFFC62828.toInt()
    }
}
