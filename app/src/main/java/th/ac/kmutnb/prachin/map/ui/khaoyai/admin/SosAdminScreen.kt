package th.ac.kmutnb.prachin.map.ui.khaoyai.admin

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.model.SosReport
import th.ac.kmutnb.prachin.map.data.model.SosRules
import th.ac.kmutnb.prachin.map.data.model.SosStatus
import th.ac.kmutnb.prachin.map.map.ForestMapLook
import th.ac.kmutnb.prachin.map.map.MapGeoJson
import th.ac.kmutnb.prachin.map.map.rememberMapViewWithLifecycle
import th.ac.kmutnb.prachin.map.ui.khaoyai.KhaoYaiTopBar
import th.ac.kmutnb.prachin.map.ui.khaoyai.Pill
import th.ac.kmutnb.prachin.map.ui.khaoyai.openLink
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Every call for help: where on the map, what was said, and who is handling it. */
@Composable
fun SosAdminScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val reports by container.sosRepository.reports.collectAsStateWithLifecycle(initialValue = emptyList())
    val ordered = SosRules.triage(reports)
    val scope = rememberCoroutineScope()
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        KhaoYaiTopBar(title = stringResource(R.string.sos_admin_title), onBack = onBack)
        SosMap(
            reports = reports,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding(),
        ) {
            item {
                Text(
                    stringResource(R.string.sos_admin_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ordered.isEmpty()) {
                item { Text(stringResource(R.string.sos_admin_empty), modifier = Modifier.padding(vertical = 24.dp)) }
            }
            items(ordered, key = { it.id }) { report ->
                ReportCard(
                    report = report,
                    onAcknowledge = { scope.launch { container.sosRepository.setStatus(report.id, SosStatus.ACKNOWLEDGED) } },
                    onResolve = { scope.launch { container.sosRepository.setStatus(report.id, SosStatus.RESOLVED) } },
                    onDelete = { confirmDelete = report.id },
                )
            }
            if (ordered.any { it.status == SosStatus.RESOLVED }) {
                item {
                    TextButton(onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sos_admin_clear_resolved), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(stringResource(R.string.sos_admin_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch { container.sosRepository.delete(id) }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            text = { Text(stringResource(R.string.sos_admin_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { container.sosRepository.deleteResolved() }
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun ReportCard(
    report: SosReport,
    onAcknowledge: () -> Unit,
    onResolve: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val statusColour = statusColour(report.status)
    val categoryLabel = stringResource(report.category.labelRes)
    val header = stringResource(R.string.sos_share_header)
    val noFix = stringResource(R.string.sos_share_no_fix)
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${report.category.emoji} $categoryLabel", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Pill(stringResource(report.status.labelRes), statusColour)
            }
            Text(
                stringResource(
                    R.string.sos_admin_who_when,
                    report.username.ifBlank { stringResource(R.string.home_guest_name) },
                    TIME_FORMAT.format(Date(report.createdAt)),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (report.message.isNotBlank()) Text(report.message, style = MaterialTheme.typography.bodyMedium)
            if (report.trailName.isNotBlank()) {
                Text(stringResource(R.string.sos_admin_on_trail, report.trailName), style = MaterialTheme.typography.bodySmall)
            }
            val point = report.point
            Text(
                if (point == null) {
                    stringResource(R.string.sos_share_no_fix)
                } else {
                    stringResource(R.string.sos_position_value, point.lat, point.lon, report.accuracyMeters ?: 0f)
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (report.status == SosStatus.NEW) {
                    OutlinedButton(onClick = onAcknowledge) { Text(stringResource(R.string.sos_admin_acknowledge)) }
                }
                if (report.status != SosStatus.RESOLVED) {
                    OutlinedButton(onClick = onResolve) { Text(stringResource(R.string.sos_admin_resolve)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                report.mapLink?.let { link ->
                    TextButton(onClick = { openLink(context, link) }) { Text(stringResource(R.string.sos_admin_open_map)) }
                }
                TextButton(onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, SosRules.shareText(report, categoryLabel, header, noFix))
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, null)) }
                }) { Text(stringResource(R.string.sos_share)) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/**
 * The reports on the offline basemap, red until someone takes them. Framed on the reports
 * when there are any, on the campus otherwise. A report outside the downloaded pack still
 * shows where it is - on plain background, since there are no tiles to draw there.
 */
@Composable
private fun SosMap(reports: List<SosReport>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MapApplication).container }
    val styleUri by produceState<String?>(initialValue = null) {
        value = container.mapStyleProvider.styleUri(container.preferences.tileSourceMode.first())
    }
    val config by produceState(initialValue = null as th.ac.kmutnb.prachin.map.data.config.CampusConfig?) {
        value = runCatching { container.campusRepository.config() }.getOrNull()
    }
    val mapView = rememberMapViewWithLifecycle()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

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
                    s.addSource(GeoJsonSource(SOURCE, MapGeoJson.EMPTY_COLLECTION))
                    s.addLayer(
                        CircleLayer(LAYER, SOURCE).withProperties(
                            PropertyFactory.circleRadius(9f),
                            PropertyFactory.circleStrokeWidth(2.5f),
                            PropertyFactory.circleStrokeColor(AndroidColor.WHITE),
                            PropertyFactory.circleColor(
                                Expression.match(
                                    Expression.get(PROPERTY_STATUS),
                                    Expression.color(COLOUR_NEW),
                                    Expression.stop(SosStatus.ACKNOWLEDGED.id, Expression.color(COLOUR_ACK)),
                                    Expression.stop(SosStatus.RESOLVED.id, Expression.color(COLOUR_RESOLVED)),
                                ),
                            ),
                        ),
                    )
                    style = s
                }
            }
            onDispose { style = null }
        }
    }

    LaunchedEffect(style, reports, config) {
        val s = style ?: return@LaunchedEffect
        val m = map ?: return@LaunchedEffect
        val located = reports.mapNotNull { r -> r.point?.let { r to it } }
        val features = JsonArray()
        located.forEach { (report, point) ->
            features.add(
                MapGeoJson.featureOf(
                    MapGeoJson.pointGeometry(point),
                    JsonObject().apply { addProperty(PROPERTY_STATUS, report.status.id) },
                ),
            )
        }
        s.getSourceAs<GeoJsonSource>(SOURCE)?.setGeoJson(MapGeoJson.documentOf(features))
        val points = located.map { it.second }
        when {
            points.size >= 2 -> m.moveCamera(
                CameraUpdateFactory.newLatLngBounds(
                    LatLngBounds.Builder().apply { points.forEach { include(LatLng(it.lat, it.lon)) } }.build(),
                    80,
                ),
            )
            points.size == 1 -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].lat, points[0].lon), 16.0))
            else -> config?.let { c -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(c.center.lat, c.center.lon), c.defaultZoom)) }
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier.clip(RoundedCornerShape(0.dp)))
}

@Composable
private fun statusColour(status: SosStatus): Color = when (status) {
    SosStatus.NEW -> MaterialTheme.colorScheme.error
    SosStatus.ACKNOWLEDGED -> MaterialTheme.colorScheme.tertiary
    SosStatus.RESOLVED -> MaterialTheme.colorScheme.primary
}

private val TIME_FORMAT = SimpleDateFormat("d/M/yyyy HH:mm", Locale.getDefault())

private const val SOURCE = "sos-reports"
private const val LAYER = "sos-report-layer"
private const val PROPERTY_STATUS = "status"
private const val COLOUR_NEW = 0xFFD32F2F.toInt()
private const val COLOUR_ACK = 0xFFE3A72F.toInt()
private const val COLOUR_RESOLVED = 0xFF2E7D32.toInt()
