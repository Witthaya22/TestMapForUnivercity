package th.ac.kmutnb.prachin.map.ui.gpslog

import android.graphics.PointF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.config.CampusConfig
import th.ac.kmutnb.prachin.map.data.geojson.GpsPointExporter
import th.ac.kmutnb.prachin.map.data.model.GpsCaptureMode
import th.ac.kmutnb.prachin.map.data.model.GpsFixQuality
import th.ac.kmutnb.prachin.map.data.model.GpsPoint
import th.ac.kmutnb.prachin.map.map.GpsLogLayerManager
import th.ac.kmutnb.prachin.map.map.rememberMapViewWithLifecycle
import th.ac.kmutnb.prachin.map.survey.PointSurveySession
import th.ac.kmutnb.prachin.map.ui.common.messageRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The GPS point log (F11b): walk the campus on the map, and record what the receiver says.
 *
 * This is a map rather than a page of numbers on purpose. A surveyor has to know three
 * things at once - where the device thinks it is, how far it could be out, and which spots
 * have already been measured - and only the map answers all three without being read. The
 * numbers are still there, above the map, because the one thing a map cannot show is the
 * metre count that decides whether a reading is worth keeping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsLogScreen(
    onBack: () -> Unit,
    viewModel: GpsLogViewModel = viewModel(factory = GpsLogViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val layerManager = remember { GpsLogLayerManager() }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    // Both screens write to one log, so an export has to be able to say which readings it
    // wants. Null means everything.
    var exportFilter by rememberSaveable { mutableStateOf<GpsCaptureMode?>(null) }
    var showList by rememberSaveable { mutableStateOf(false) }
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }

    fun report(success: Boolean) {
        val message = if (success) {
            context.getString(R.string.export_success)
        } else {
            context.getString(R.string.export_failed, "")
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val exportGeoJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json"),
    ) { uri ->
        uri?.let { viewModel.export(it, GpsLogExportFormat.GEOJSON, exportFilter, ::report) }
    }

    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        uri?.let { viewModel.export(it, GpsLogExportFormat.CSV, exportFilter, ::report) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gpslog_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (state.points.isEmpty()) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.gpslog_export_empty),
                                    )
                                }
                            } else {
                                showExportDialog = true
                            }
                        },
                    ) {
                        Text(stringResource(R.string.gpslog_menu_export))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            val problem = state.configProblem
            val config = state.config
            val styleUri = state.styleUri

            if (problem != null) {
                Text(
                    text = stringResource(problem.messageRes),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Box
            }
            if (config == null || styleUri == null) return@Box

            GpsLogMapView(
                config = config,
                styleUri = styleUri,
                layerManager = layerManager,
                onMapReady = { mapLibreMap = it },
                onPointTapped = viewModel::selectPoint,
                onUserPanned = { viewModel.setFollowUser(false) },
            )

            // --- overlays pushed into the map ------------------------------------------
            LaunchedEffect(state.locationState, layerManager.isAttached) {
                if (!layerManager.isAttached) return@LaunchedEffect
                layerManager.setUserLocation(
                    point = state.currentPoint,
                    accuracyMeters = state.currentAccuracyMeters ?: 0f,
                )
            }
            LaunchedEffect(state.trail, layerManager.isAttached) {
                if (layerManager.isAttached) layerManager.setTrail(state.trail)
            }
            LaunchedEffect(state.points, layerManager.isAttached) {
                if (layerManager.isAttached) layerManager.setPoints(state.points)
            }

            // Keeping the dot on screen is what makes the map usable one-handed while
            // walking; any pan hands control back, so the surveyor can look ahead.
            LaunchedEffect(state.currentPoint, state.followUser) {
                val point = state.currentPoint ?: return@LaunchedEffect
                if (!state.followUser) return@LaunchedEffect
                mapLibreMap?.animateCamera(
                    CameraUpdateFactory.newLatLng(LatLng(point.lat, point.lon)),
                )
            }

            // --- chrome ----------------------------------------------------------------
            LiveStatusCard(
                state = state,
                onOpenList = { showList = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
            )

            FloatingActionButton(
                onClick = {
                    viewModel.setFollowUser(true)
                    state.currentPoint?.let { point ->
                        mapLibreMap?.animateCamera(
                            CameraUpdateFactory.newLatLng(LatLng(point.lat, point.lon)),
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(12.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_my_location),
                    stringResource(R.string.map_recenter),
                )
            }

            CaptureCard(
                state = state,
                onStart = viewModel::startCapture,
                onStop = viewModel::stopCapture,
                onCancel = viewModel::cancelCapture,
                onSave = viewModel::savePending,
                onDiscard = viewModel::discardPending,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
            )
        }
    }

    state.selectedPoint?.let { point ->
        RecordedPointSheet(
            point = point,
            onDismiss = viewModel::dismissSelection,
            onSaveNote = { note -> viewModel.updateNote(point.id, note) },
            onDelete = { viewModel.deletePoint(point.id) },
        )
    }

    if (showList) {
        PointListSheet(
            points = state.points,
            onDismiss = { showList = false },
            onSelect = { id ->
                showList = false
                viewModel.selectPoint(id)
            },
            onDeleteAll = { confirmDeleteAll = true },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            text = {
                Text(stringResource(R.string.gpslog_delete_all_confirm, state.points.size))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteAll = false
                        showList = false
                        viewModel.deleteAllPoints()
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showExportDialog) {
        GpsExportDialog(
            pointCount = state.points.size,
            filter = exportFilter,
            onFilterChange = { exportFilter = it },
            onGeoJson = {
                showExportDialog = false
                exportGeoJsonLauncher.launch(GpsPointExporter.defaultFileName("geojson"))
            },
            onCsv = {
                showExportDialog = false
                exportCsvLauncher.launch(GpsPointExporter.defaultFileName("csv"))
            },
            onDismiss = { showExportDialog = false },
        )
    }
}

// --------------------------------------------------------------------------------------
// Map
// --------------------------------------------------------------------------------------

@Composable
private fun GpsLogMapView(
    config: CampusConfig,
    styleUri: String,
    layerManager: GpsLogLayerManager,
    onMapReady: (MapLibreMap) -> Unit,
    onPointTapped: (String) -> Unit,
    onUserPanned: () -> Unit,
) {
    val mapView = rememberMapViewWithLifecycle()

    DisposableEffect(mapView, styleUri) {
        mapView.getMapAsync { map ->
            onMapReady(map)
            map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
                layerManager.attach(style)
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(config.center.lat, config.center.lon))
                    // Closer than the navigation default: surveying is done a few metres
                    // at a time, and the whole campus in one view hides that detail.
                    .zoom(SURVEY_ZOOM)
                    .build()
            }

            map.setMinZoomPreference(config.minZoom.toDouble())
            map.setMaxZoomPreference(config.maxZoom.toDouble())
            map.setLatLngBoundsForCameraTarget(
                LatLngBounds.from(
                    config.bbox.maxLat,
                    config.bbox.maxLon,
                    config.bbox.minLat,
                    config.bbox.minLon,
                ),
            )
            map.uiSettings.isTiltGesturesEnabled = false
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = true

            map.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                    onUserPanned()
                }
            }

            map.addOnMapClickListener { latLng ->
                val screenPoint = map.projection.toScreenLocation(latLng)
                val hits = map.queryRenderedFeatures(
                    PointF(screenPoint.x, screenPoint.y),
                    GpsLogLayerManager.LAYER_POINTS,
                )
                val id = hits.firstNotNullOfOrNull {
                    it.getStringProperty(GpsLogLayerManager.PROPERTY_ID)
                }
                if (id != null) {
                    onPointTapped(id)
                    true
                } else {
                    false
                }
            }
        }
        onDispose { layerManager.detach() }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

// --------------------------------------------------------------------------------------
// Live readout
// --------------------------------------------------------------------------------------

@Composable
private fun LiveStatusCard(
    state: GpsLogUiState,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accuracy = state.currentAccuracyMeters
    val quality = accuracy?.let { GpsFixQuality.of(it) }

    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = accuracy
                        ?.let { stringResource(R.string.gpslog_accuracy_value, it) }
                        ?: stringResource(R.string.gpslog_status_searching),
                    style = MaterialTheme.typography.titleMedium,
                    color = quality?.let { colourOf(it) } ?: MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                quality?.let {
                    Text(
                        text = stringResource(it.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = colourOf(it),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ReadoutColumn(
                    label = stringResource(R.string.gpslog_satellites),
                    value = stringResource(
                        R.string.gpslog_satellites_value,
                        state.satellites.inUse,
                        state.satellites.visible,
                    ),
                )
                ReadoutColumn(
                    label = stringResource(R.string.gpslog_elevation),
                    value = state.fix?.altitudeMeters
                        ?.let { stringResource(R.string.gpslog_elevation_value, it) }
                        ?: stringResource(R.string.gpslog_value_none),
                )
            }

            Text(
                text = state.currentPoint?.format()
                    ?: stringResource(R.string.gpslog_value_none),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )

            AssistChip(
                onClick = onOpenList,
                label = { Text(stringResource(R.string.gpslog_points_count, state.points.size)) },
            )
        }
    }
}

private const val SURVEY_ZOOM = 18.0
