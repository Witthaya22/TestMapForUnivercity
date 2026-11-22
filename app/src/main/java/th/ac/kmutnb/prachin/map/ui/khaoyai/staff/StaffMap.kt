package th.ac.kmutnb.prachin.map.ui.khaoyai.staff

import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.config.CampusConfig
import th.ac.kmutnb.prachin.map.map.ForestMapLook
import th.ac.kmutnb.prachin.map.map.MapGeoJson
import th.ac.kmutnb.prachin.map.map.rememberMapViewWithLifecycle

/** A coloured dot on a staff map: an emergency call, a tourist's last position. */
data class MapMarker(val id: String, val point: GeoPoint, val colour: Int)

/**
 * The offline basemap with [markers], and [line] (a walked history) under them. Framed on
 * whatever is drawn, or on the campus when nothing is. Tapping a marker reports its id.
 *
 * Positions outside the downloaded pack still show where they are, on plain background:
 * there are no tiles to draw there offline.
 */
@Composable
fun StaffMap(
    markers: List<MapMarker>,
    modifier: Modifier = Modifier,
    line: List<GeoPoint> = emptyList(),
    onMarkerTapped: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val styleUri by produceState<String?>(initialValue = null) {
        value = container.mapStyleProvider.styleUri(container.preferences.tileSourceMode.first())
    }
    val config by produceState<CampusConfig?>(initialValue = null) {
        value = runCatching { container.campusRepository.config() }.getOrNull()
    }
    val mapView = rememberMapViewWithLifecycle()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    val tapped by androidx.compose.runtime.rememberUpdatedState(onMarkerTapped)

    val uri = styleUri
    if (uri != null) {
        DisposableEffect(mapView, uri) {
            mapView.getMapAsync { m ->
                map = m
                m.uiSettings.isRotateGesturesEnabled = false
                m.uiSettings.isTiltGesturesEnabled = false
                m.uiSettings.isLogoEnabled = false
                m.setStyle(Style.Builder().fromUri(uri)) { s ->
                    ForestMapLook.apply(s)
                    s.addSource(GeoJsonSource(SOURCE_LINE, MapGeoJson.EMPTY_COLLECTION))
                    s.addSource(GeoJsonSource(SOURCE_POINTS, MapGeoJson.EMPTY_COLLECTION))
                    s.addLayer(
                        LineLayer(LAYER_LINE, SOURCE_LINE).withProperties(
                            PropertyFactory.lineColor(0xFF1E88E5.toInt()),
                            PropertyFactory.lineWidth(3.5f),
                            PropertyFactory.lineOpacity(0.8f),
                        ),
                    )
                    s.addLayer(
                        CircleLayer(LAYER_POINTS, SOURCE_POINTS).withProperties(
                            PropertyFactory.circleRadius(9f),
                            PropertyFactory.circleStrokeWidth(2.5f),
                            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                            PropertyFactory.circleColor(Expression.toColor(Expression.get(PROPERTY_COLOUR))),
                        ),
                    )
                    style = s
                }
                m.addOnMapClickListener { latLng ->
                    val id = m.queryRenderedFeatures(m.projection.toScreenLocation(latLng), LAYER_POINTS)
                        .firstNotNullOfOrNull { it.getStringProperty(PROPERTY_ID) }
                    if (id != null) tapped(id)
                    id != null
                }
            }
            onDispose { style = null }
        }
    }

    LaunchedEffect(style, markers, line, config) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val features = JsonArray()
        markers.forEach { marker ->
            features.add(
                MapGeoJson.featureOf(
                    MapGeoJson.pointGeometry(marker.point),
                    JsonObject().apply {
                        addProperty(PROPERTY_ID, marker.id)
                        addProperty(PROPERTY_COLOUR, String.format("#%06X", marker.colour and 0xFFFFFF))
                    },
                ),
            )
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_POINTS)?.setGeoJson(MapGeoJson.documentOf(features))
        s.getSourceAs<GeoJsonSource>(SOURCE_LINE)?.setGeoJson(
            if (line.size >= 2) MapGeoJson.line(line) else MapGeoJson.EMPTY_COLLECTION,
        )
        val points = markers.map { it.point } + line
        when {
            points.size >= 2 -> runCatching {
                m.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        LatLngBounds.Builder().apply { points.forEach { include(LatLng(it.lat, it.lon)) } }.build(),
                        90,
                    ),
                )
            }
            points.size == 1 -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].lat, points[0].lon), 16.0))
            else -> config?.let { c -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(c.center.lat, c.center.lon), c.defaultZoom)) }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private const val SOURCE_POINTS = "staff-points"
private const val SOURCE_LINE = "staff-line"
private const val LAYER_POINTS = "staff-point-layer"
private const val LAYER_LINE = "staff-line-layer"
private const val PROPERTY_ID = "id"
private const val PROPERTY_COLOUR = "colour"
