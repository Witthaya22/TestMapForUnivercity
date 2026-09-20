package th.ac.kmutnb.prachin.map.map

import android.graphics.Color
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import th.ac.kmutnb.prachin.map.data.model.HazardPoint
import th.ac.kmutnb.prachin.map.data.model.HazardSeverity

/**
 * Draws marked hazards: the spot, and how far around it the warning applies.
 *
 * The radius is drawn as a real polygon on the ground rather than a fixed-size dot,
 * because the radius is the part someone marking a hazard has to get right and cannot
 * otherwise see. Sizing it in pixels would make a hazard appear to grow as the map is
 * zoomed, which is exactly the judgement being made here.
 *
 * Reused by both the hazard screen, where the hazards are being edited, and the main map,
 * where they are being avoided; the difference is only which sources get populated.
 */
class HazardLayerManager {

    private var style: Style? = null

    val isAttached: Boolean get() = style != null

    fun attach(style: Style) {
        this.style = style

        style.addSource(GeoJsonSource(SOURCE_AREAS, MapGeoJson.EMPTY_COLLECTION))
        style.addSource(GeoJsonSource(SOURCE_POINTS, MapGeoJson.EMPTY_COLLECTION))

        style.addLayer(
            FillLayer(LAYER_AREAS, SOURCE_AREAS).withProperties(
                PropertyFactory.fillColor(severityColourExpression()),
                // Faint enough that several overlapping hazards stay readable, and that
                // the paths underneath them can still be followed.
                PropertyFactory.fillOpacity(whenActive(0.18f, 0.06f)),
            ),
        )

        style.addLayer(
            LineLayer(LAYER_AREA_OUTLINES, SOURCE_AREAS).withProperties(
                PropertyFactory.lineColor(severityColourExpression()),
                PropertyFactory.lineWidth(1.5f),
                // Faded, not hidden, once warnings are switched off: still on the map as a
                // record of what used to be here, visibly not in force.
                PropertyFactory.lineOpacity(whenActive(0.9f, 0.35f)),
            ),
        )

        style.addLayer(
            CircleLayer(LAYER_POINTS, SOURCE_POINTS).withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor(severityColourExpression()),
                PropertyFactory.circleStrokeWidth(2f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleOpacity(whenActive(1.0f, 0.4f)),
            ),
        )
    }

    fun detach() {
        style = null
    }

    /**
     * @param showInactive true on the management screen, where a hazard that has been
     * dealt with still has to be findable; false on the main map, where it would only be
     * a warning nobody is meant to act on.
     */
    fun setHazards(hazards: List<HazardPoint>, showInactive: Boolean = false) {
        val visible = if (showInactive) hazards else hazards.filter { it.isActive }
        setGeoJson(SOURCE_AREAS, areasGeoJson(visible))
        setGeoJson(SOURCE_POINTS, pointsGeoJson(visible))
    }

    fun clear() {
        setGeoJson(SOURCE_AREAS, MapGeoJson.EMPTY_COLLECTION)
        setGeoJson(SOURCE_POINTS, MapGeoJson.EMPTY_COLLECTION)
    }

    private fun setGeoJson(sourceId: String, geoJson: String) {
        // A style reload drops every source; the next attach() repopulates them.
        style?.getSourceAs<GeoJsonSource>(sourceId)?.setGeoJson(geoJson)
    }

    /**
     * The warning radius, not the hazard's own radius.
     *
     * What matters on screen is where the phone will start talking - that is the number
     * someone tuning a hazard is actually choosing, even though they set it indirectly
     * through the radius and the severity.
     */
    private fun areasGeoJson(hazards: List<HazardPoint>): String {
        val features = JsonArray()
        hazards.forEach { hazard ->
            features.add(
                MapGeoJson.featureOf(
                    MapGeoJson.circleGeometry(hazard.point, hazard.alertRadiusMeters),
                    propertiesOf(hazard),
                ),
            )
        }
        return MapGeoJson.documentOf(features)
    }

    private fun pointsGeoJson(hazards: List<HazardPoint>): String {
        val features = JsonArray()
        hazards.forEach { hazard ->
            features.add(
                MapGeoJson.featureOf(
                    MapGeoJson.pointGeometry(hazard.point),
                    propertiesOf(hazard),
                ),
            )
        }
        return MapGeoJson.documentOf(features)
    }

    private fun propertiesOf(hazard: HazardPoint) = JsonObject().apply {
        addProperty(PROPERTY_ID, hazard.id)
        addProperty(PROPERTY_SEVERITY, hazard.severity.id)
        addProperty(PROPERTY_ACTIVE, hazard.isActive)
    }

    /** Data-driven opacity: full strength in force, ghosted once switched off. */
    private fun whenActive(inForce: Float, switchedOff: Float): Expression =
        Expression.switchCase(
            Expression.get(PROPERTY_ACTIVE),
            Expression.literal(inForce),
            Expression.literal(switchedOff),
        )

    private fun severityColourExpression(): Expression = Expression.match(
        Expression.get(PROPERTY_SEVERITY),
        Expression.color(COLOR_WARNING),
        *HazardSeverity.entries.map { severity ->
            Expression.stop(severity.id, Expression.color(colourFor(severity)))
        }.toTypedArray(),
    )

    private fun colourFor(severity: HazardSeverity): Int = when (severity) {
        HazardSeverity.CAUTION -> COLOR_CAUTION
        HazardSeverity.WARNING -> COLOR_WARNING
        HazardSeverity.DANGER -> COLOR_DANGER
    }

    companion object {
        const val SOURCE_AREAS = "kmutnb-hazard-areas"
        const val SOURCE_POINTS = "kmutnb-hazard-points"

        const val LAYER_AREAS = "kmutnb-hazard-area-layer"
        const val LAYER_AREA_OUTLINES = "kmutnb-hazard-outline-layer"

        /** Queried on tap to work out which hazard was hit. */
        const val LAYER_POINTS = "kmutnb-hazard-point-layer"

        const val PROPERTY_ID = "id"
        const val PROPERTY_SEVERITY = "severity"
        const val PROPERTY_ACTIVE = "active"

        private const val COLOR_CAUTION = 0xFFF9A825.toInt()
        private const val COLOR_WARNING = 0xFFEF6C00.toInt()
        private const val COLOR_DANGER = 0xFFC62828.toInt()

        /** Matching Compose colours, so the map and the sheets agree. */
        fun composeColour(severity: HazardSeverity): Int = when (severity) {
            HazardSeverity.CAUTION -> COLOR_CAUTION
            HazardSeverity.WARNING -> COLOR_WARNING
            HazardSeverity.DANGER -> COLOR_DANGER
        }
    }
}