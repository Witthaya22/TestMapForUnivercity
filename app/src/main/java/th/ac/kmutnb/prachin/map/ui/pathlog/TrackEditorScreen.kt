package th.ac.kmutnb.prachin.map.ui.pathlog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import android.graphics.PointF
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdate
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.core.geo.GeoUtils
import th.ac.kmutnb.prachin.map.data.config.CampusConfig
import th.ac.kmutnb.prachin.map.data.model.GpsFixQuality
import th.ac.kmutnb.prachin.map.data.model.TrackLog
import th.ac.kmutnb.prachin.map.data.model.TrackVertexInfo
import th.ac.kmutnb.prachin.map.data.model.VertexSource
import th.ac.kmutnb.prachin.map.map.TrackEditLayerManager
import th.ac.kmutnb.prachin.map.map.rememberMapViewWithLifecycle
import th.ac.kmutnb.prachin.map.ui.common.BottomSheetCardMaxHeight
import th.ac.kmutnb.prachin.map.ui.common.Formats
import th.ac.kmutnb.prachin.map.ui.common.messageRes

/**
 * One saved track over the real basemap, point by point, with the means to correct it.
 *
 * The recording screens can say how good a walk was on average; only here can the walker
 * see *which* point was bad. Every vertex carries the circle its own fix accuracy draws,
 * over the same offline OpenStreetMap basemap the navigation uses, so a point whose circle
 * misses the path it was walked along stands out on sight.
 *
 * Corrections are made against that basemap with a fixed crosshair rather than by dragging
 * a dot: a fingertip hides exactly the spot being placed, a crosshair does not. Every edit
 * keeps its provenance (see `TrackEdits`), so a corrected line never passes for a measured
 * one in the exported file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackEditorScreen(
    trackId: String,
    onBack: () -> Unit,
    viewModel: TrackEditorViewModel = viewModel(
        key = "track_editor_$trackId",
        factory = TrackEditorViewModel.factory(trackId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val layerManager = remember { TrackEditLayerManager() }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    // Where the crosshair points: the camera target, which is the centre of the map.
    var crosshair by remember { mutableStateOf<GeoPoint?>(null) }
    var fitted by remember { mutableStateOf(false) }
    var showList by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }

    fun leave() {
        if (state.isDirty) confirmLeave = true else onBack()
    }

    fun flyTo(point: GeoPoint) {
        mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(point.lat, point.lon)))
    }

    fun say(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Back leaves a half-done move or add first; walking is only left by its own buttons,
    // so a stray back gesture cannot throw away a walk.
    BackHandler(enabled = state.mode != TrackEditMode.WALK) {
        when (state.mode) {
            TrackEditMode.MOVE, TrackEditMode.ADD -> viewModel.endMode()
            else -> leave()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.trackedit_title, state.draft?.code.orEmpty()))
                },
                navigationIcon = {
                    IconButton(onClick = ::leave) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::undo, enabled = state.canUndo) {
                        Text(stringResource(R.string.trackedit_undo))
                    }
                    TextButton(
                        onClick = {
                            viewModel.save { say(context.getString(R.string.trackedit_saved)) }
                        },
                        enabled = state.isDirty && !state.isSaving &&
                            state.mode == TrackEditMode.VIEW,
                    ) {
                        Text(stringResource(R.string.action_save))
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
            val draft = state.draft

            when {
                problem != null -> {
                    Text(
                        text = stringResource(problem.messageRes),
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    return@Box
                }

                state.notFound -> {
                    Text(
                        text = stringResource(R.string.trackedit_not_found),
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    return@Box
                }
            }
            if (config == null || styleUri == null || draft == null) return@Box

            TrackEditMapView(
                config = config,
                styleUri = styleUri,
                layerManager = layerManager,
                onMapReady = { mapLibreMap = it },
                onCameraMoved = { crosshair = it },
                onTap = viewModel::onMapTap,
            )

            // --- overlays pushed into the map ------------------------------------------
            LaunchedEffect(draft, state.selected, state.showAccuracy) {
                layerManager.setTrack(draft, state.selected, state.showAccuracy)
            }
            LaunchedEffect(state.locationState) {
                layerManager.setUserLocation(state.currentPoint, state.currentAccuracyMeters ?: 0f)
            }
            LaunchedEffect(state.walkPoints, draft) {
                val end = draft.points.lastOrNull()
                layerManager.setWalk(
                    if (state.walkPoints.isEmpty() || end == null) {
                        emptyList()
                    } else {
                        listOf(end) + state.walkPoints
                    },
                )
            }
            LaunchedEffect(crosshair, state.mode, state.selected, state.insertIndex, draft) {
                layerManager.setPreview(previewSegments(state, draft, crosshair))
            }

            // Frame the whole track once, when both the map and the track are there, and
            // clear of the card: the bottom padding is the card's share of the screen.
            val density = LocalDensity.current
            val screenHeightDp = LocalConfiguration.current.screenHeightDp
            LaunchedEffect(mapLibreMap) {
                val map = mapLibreMap ?: return@LaunchedEffect
                if (fitted) return@LaunchedEffect
                fitted = true
                val padding = with(density) {
                    FramePadding(
                        side = 40.dp.roundToPx(),
                        top = 72.dp.roundToPx(),
                        bottom = (screenHeightDp * 0.45f).dp.roundToPx(),
                    )
                }
                map.moveCamera(framing(draft.points, padding))
                map.dropPadding()
            }

            // Moving starts with the crosshair on the vertex, so "0 m" means untouched;
            // adding starts on the point the new one will follow.
            LaunchedEffect(state.mode) {
                val anchor = when (state.mode) {
                    TrackEditMode.MOVE -> state.selected?.let { draft.points.getOrNull(it) }
                    TrackEditMode.ADD -> draft.points.getOrNull(state.insertIndex - 1)
                        ?: draft.points.getOrNull(state.insertIndex)
                    else -> null
                }
                anchor?.let(::flyTo)
            }

            if (state.mode == TrackEditMode.MOVE || state.mode == TrackEditMode.ADD) {
                Crosshair(Modifier.align(Alignment.Center))
            }

            SummaryChip(
                track = draft,
                isDirty = state.isDirty,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 12.dp, end = 12.dp),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FloatingActionButton(onClick = { state.currentPoint?.let(::flyTo) }) {
                    Icon(
                        painterResource(R.drawable.ic_my_location),
                        stringResource(R.string.map_recenter),
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = BottomSheetCardMaxHeight),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                ) {
                    Column(
                        Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val selected = state.selected
                        when {
                            state.mode == TrackEditMode.MOVE && selected != null ->
                                MovePanel(
                                    track = draft,
                                    index = selected,
                                    crosshair = crosshair,
                                    onConfirm = { crosshair?.let(viewModel::confirmMove) },
                                    onCancel = viewModel::endMode,
                                )

                            state.mode == TrackEditMode.ADD ->
                                AddPanel(
                                    track = draft,
                                    insertIndex = state.insertIndex,
                                    crosshair = crosshair,
                                    onAdd = { crosshair?.let(viewModel::addAt) },
                                    onDone = viewModel::endMode,
                                )

                            state.mode == TrackEditMode.WALK ->
                                WalkPanel(
                                    state = state,
                                    track = draft,
                                    onStop = {
                                        if (!viewModel.stopWalk()) {
                                            say(context.getString(R.string.trackedit_walk_nothing))
                                        }
                                    },
                                    onCancel = viewModel::cancelWalk,
                                )

                            selected != null && selected in draft.points.indices ->
                                VertexPanel(
                                    track = draft,
                                    index = selected,
                                    canDelete = state.canDeleteVertex,
                                    onClose = { viewModel.select(null) },
                                    onMove = viewModel::startMove,
                                    onInsertBefore = { viewModel.startAdd(selected) },
                                    onInsertAfter = { viewModel.startAdd(selected + 1) },
                                    onDelete = viewModel::deleteSelected,
                                )

                            else ->
                                OverviewPanel(
                                    state = state,
                                    track = draft,
                                    onToggleAccuracy = viewModel::setShowAccuracy,
                                    onAddEnd = { viewModel.startAdd() },
                                    onWalkOn = viewModel::startWalk,
                                    onOpenList = { showList = true },
                                )
                        }
                    }
                }
            }
        }
    }

    val draft = state.draft
    if (showList && draft != null) {
        VertexListSheet(
            track = draft,
            onDismiss = { showList = false },
            onSelect = { index ->
                showList = false
                viewModel.select(index)
                draft.points.getOrNull(index)?.let(::flyTo)
            },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            text = { Text(stringResource(R.string.trackedit_discard_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onBack() }) {
                    Text(stringResource(R.string.trackedit_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** What confirming the edit in progress would draw, as dashed segments. */
private fun previewSegments(
    state: TrackEditorUiState,
    track: TrackLog,
    crosshair: GeoPoint?,
): List<List<GeoPoint>> {
    val target = crosshair ?: return emptyList()
    val points = track.points
    return when (state.mode) {
        TrackEditMode.MOVE -> {
            val index = state.selected ?: return emptyList()
            listOfNotNull(
                points.getOrNull(index - 1)?.let { listOf(it, target) },
                points.getOrNull(index + 1)?.let { listOf(target, it) },
            )
        }

        TrackEditMode.ADD -> listOfNotNull(
            points.getOrNull(state.insertIndex - 1)?.let { listOf(it, target) },
            points.getOrNull(state.insertIndex)?.let { listOf(target, it) },
        )

        else -> emptyList()
    }
}

private data class FramePadding(val side: Int, val top: Int, val bottom: Int)

/** The whole track in view, clear of the card at the bottom. */
private fun framing(points: List<GeoPoint>, padding: FramePadding): CameraUpdate {
    val latLngs = points.map { LatLng(it.lat, it.lon) }
    val single = CameraUpdateFactory.newLatLngZoom(latLngs.first(), EDIT_ZOOM)
    if (latLngs.size < 2) return single
    return runCatching {
        CameraUpdateFactory.newLatLngBounds(
            LatLngBounds.Builder().includes(latLngs).build(),
            padding.side,
            padding.top,
            padding.side,
            padding.bottom,
        )
    }.getOrDefault(single)
}

/**
 * Keeps the current view but takes the framing padding off the camera.
 *
 * `newLatLngBounds` with padding leaves that padding on the camera, which moves the
 * camera's centre up into the padded area - away from the crosshair, which is drawn at the
 * centre of the view. Every later fly-to then parked the vertex above the crosshair and the
 * move distance read from the wrong spot. With no padding the two centres are one point.
 */
private fun MapLibreMap.dropPadding() {
    if (width <= 0f || height <= 0f) return
    val centre = projection.fromScreenLocation(PointF(width / 2f, height / 2f))
    moveCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(centre)
                .zoom(cameraPosition.zoom)
                .padding(0.0, 0.0, 0.0, 0.0)
                .build(),
        ),
    )
}

/**
 * The point under the middle of the map view - exactly where the crosshair is drawn.
 *
 * Read from the pixel rather than from the camera target, so it stays under the crosshair
 * whatever padding the camera carries.
 */
private fun MapLibreMap.viewCentre(): GeoPoint? {
    if (width <= 0f || height <= 0f) return null
    val latLng = projection.fromScreenLocation(PointF(width / 2f, height / 2f))
    return GeoPoint(lat = latLng.latitude, lon = latLng.longitude)
}

// --------------------------------------------------------------------------------------
// Map
// --------------------------------------------------------------------------------------

@Composable
private fun TrackEditMapView(
    config: CampusConfig,
    styleUri: String,
    layerManager: TrackEditLayerManager,
    onMapReady: (MapLibreMap) -> Unit,
    onCameraMoved: (GeoPoint) -> Unit,
    onTap: (GeoPoint, Int?) -> Unit,
) {
    val mapView = rememberMapViewWithLifecycle()
    val cameraMoved by rememberUpdatedState(onCameraMoved)
    val tapped by rememberUpdatedState(onTap)
    val density = LocalDensity.current
    val tapSlopPx = with(density) { TAP_SLOP.toPx() }

    DisposableEffect(mapView, styleUri) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
                layerManager.attach(style)
                onMapReady(map)
            }

            // Closer than the campus maximum: a vertex is corrected a metre at a time, and
            // the vector basemap over-zooms cleanly from the tiles already downloaded. Not
            // clamped to the campus box either - a track may run where there is no campus.
            map.setMinZoomPreference(config.minZoom.toDouble())
            map.setMaxZoomPreference(MAX_EDIT_ZOOM)
            map.uiSettings.isTiltGesturesEnabled = false
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = true

            fun reportCentre() {
                map.viewCentre()?.let { cameraMoved(it) }
            }
            map.addOnCameraMoveListener { reportCentre() }
            map.addOnCameraIdleListener { reportCentre() }

            map.addOnMapClickListener { latLng ->
                val screenPoint = map.projection.toScreenLocation(latLng)
                val hit = map.queryRenderedFeatures(
                    android.graphics.RectF(
                        screenPoint.x - tapSlopPx,
                        screenPoint.y - tapSlopPx,
                        screenPoint.x + tapSlopPx,
                        screenPoint.y + tapSlopPx,
                    ),
                    TrackEditLayerManager.LAYER_VERTICES,
                ).firstNotNullOfOrNull {
                    it.getNumberProperty(TrackEditLayerManager.PROPERTY_INDEX)?.toInt()
                }
                tapped(GeoPoint(lat = latLng.latitude, lon = latLng.longitude), hit)
                true
            }
        }
        onDispose { layerManager.detach() }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

/** A fixed cross at the centre of the map; what it covers is what gets placed. */
@Composable
private fun Crosshair(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.trackedit_crosshair)
    Canvas(
        modifier
            .size(44.dp)
            .semantics { contentDescription = description },
    ) {
        val c = center
        val arm = size.minDimension / 2f
        val gap = 5.dp.toPx()
        listOf(Color.White to 5.dp.toPx(), Color(0xFF6A1B9A) to 2.dp.toPx()).forEach { (colour, width) ->
            drawLine(colour, Offset(c.x - arm, c.y), Offset(c.x - gap, c.y), width)
            drawLine(colour, Offset(c.x + gap, c.y), Offset(c.x + arm, c.y), width)
            drawLine(colour, Offset(c.x, c.y - arm), Offset(c.x, c.y - gap), width)
            drawLine(colour, Offset(c.x, c.y + gap), Offset(c.x, c.y + arm), width)
        }
    }
}

// --------------------------------------------------------------------------------------
// Panels
// --------------------------------------------------------------------------------------

@Composable
private fun SummaryChip(track: TrackLog, isDirty: Boolean, modifier: Modifier = Modifier) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = stringResource(
                    R.string.trackedit_summary,
                    track.vertexCount,
                    track.editedVertexCount,
                ),
                style = MaterialTheme.typography.labelMedium,
            )
            if (isDirty) {
                Text(
                    text = stringResource(R.string.trackedit_unsaved),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun OverviewPanel(
    state: TrackEditorUiState,
    track: TrackLog,
    onToggleAccuracy: (Boolean) -> Unit,
    onAddEnd: () -> Unit,
    onWalkOn: () -> Unit,
    onOpenList: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = track.note.ifBlank { stringResource(R.string.pathlog_unnamed) },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenList) { Text(stringResource(R.string.trackedit_list)) }
    }
    // Only said when it matters: a track from before per-vertex accuracy is all grey,
    // and without this line the grey would read as "something is broken".
    if (track.knownAccuracyCount == 0) {
        Text(
            text = stringResource(R.string.trackedit_legacy_short),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LegendDot(colourOf(GpsFixQuality.GOOD), stringResource(R.string.trackedit_legend_good))
        LegendDot(colourOf(GpsFixQuality.FAIR), stringResource(R.string.trackedit_legend_fair))
        LegendDot(colourOf(GpsFixQuality.POOR), stringResource(R.string.trackedit_legend_poor))
        LegendDot(PLACED_COLOUR, stringResource(R.string.trackedit_source_placed))
        Box(Modifier.weight(1f))
        Switch(checked = state.showAccuracy, onCheckedChange = onToggleAccuracy)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onAddEnd, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.trackedit_add))
        }
        OutlinedButton(
            onClick = onWalkOn,
            enabled = state.canWalk,
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.trackedit_walk_on))
        }
    }
}

@Composable
private fun LegendDot(colour: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("●", color = colour, style = MaterialTheme.typography.labelMedium)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun VertexPanel(
    track: TrackLog,
    index: Int,
    canDelete: Boolean,
    onClose: () -> Unit,
    onMove: () -> Unit,
    onInsertBefore: () -> Unit,
    onInsertAfter: () -> Unit,
    onDelete: () -> Unit,
) {
    val info = track.vertexInfoAt(index)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.trackedit_vertex_title, index + 1, track.vertexCount),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close))
        }
    }
    AccuracyText(info)
    track.movedMetersAt(index)?.let {
        Text(
            text = stringResource(R.string.trackedit_vertex_moved_short, it),
            style = MaterialTheme.typography.bodySmall,
        )
    }

    Button(onClick = onMove, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.trackedit_move))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onInsertBefore, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.trackedit_insert_before))
        }
        OutlinedButton(onClick = onInsertAfter, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.trackedit_insert_after))
        }
        // Disabled rather than explained: a line of two points has nothing to spare.
        OutlinedButton(onClick = onDelete, enabled = canDelete) {
            Text(
                text = stringResource(R.string.action_delete),
                color = if (canDelete) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
        }
    }
}

@Composable
private fun MovePanel(
    track: TrackLog,
    index: Int,
    crosshair: GeoPoint?,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val info = track.vertexInfoAt(index)
    Text(
        text = stringResource(R.string.trackedit_move_hint, index + 1),
        style = MaterialTheme.typography.bodyMedium,
    )
    // Measured from where the receiver put the vertex, not from where it was a moment
    // ago, so a second correction still reports the whole distance from the measurement.
    if (info.source != VertexSource.PLACED && crosshair != null) {
        val origin = info.originalPoint ?: track.points[index]
        Text(
            text = stringResource(
                R.string.trackedit_move_distance,
                GeoUtils.haversineMeters(origin, crosshair),
            ),
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onConfirm, enabled = crosshair != null, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.trackedit_move_confirm))
        }
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun AddPanel(
    track: TrackLog,
    insertIndex: Int,
    crosshair: GeoPoint?,
    onAdd: () -> Unit,
    onDone: () -> Unit,
) {
    Text(
        text = stringResource(R.string.trackedit_add_position, insertIndex + 1),
        style = MaterialTheme.typography.titleMedium,
    )
    val previous = track.points.getOrNull(insertIndex - 1)
    if (previous != null && crosshair != null) {
        Text(
            text = stringResource(
                R.string.trackedit_add_distance,
                GeoUtils.haversineMeters(previous, crosshair),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onAdd, enabled = crosshair != null, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.trackedit_add_here))
        }
        OutlinedButton(onClick = onDone) {
            Text(stringResource(R.string.trackedit_done))
        }
    }
}

@Composable
private fun WalkPanel(
    state: TrackEditorUiState,
    track: TrackLog,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val end = track.points.lastOrNull()
    val here = state.currentPoint
    if (state.walkPoints.isEmpty() && end != null && here != null) {
        val gap = GeoUtils.haversineMeters(end, here)
        if (gap > WALK_GAP_WARNING_METERS) {
            Text(
                text = stringResource(R.string.trackedit_walk_gap, gap),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    Text(
        text = stringResource(
            R.string.trackedit_walk_status,
            Formats.distance(context, state.walkLengthMeters),
            state.walkPoints.size,
            state.walkAccuracyMeters,
        ),
        style = MaterialTheme.typography.titleMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onStop, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.trackedit_walk_stop))
        }
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

// --------------------------------------------------------------------------------------
// Every vertex, as a list
// --------------------------------------------------------------------------------------

/**
 * The per-point answer to "how far off is each one", in walking order.
 *
 * The map shows it as circles; this says it in numbers, and it is the only way to find the
 * one bad point on a track of two hundred without zooming along the whole line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VertexListSheet(
    track: TrackLog,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        LazyColumn(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.trackedit_list_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            itemsIndexed(track.vertexInfoOrUnknown) { index, info ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.trackedit_list_item, index + 1),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(0.3f),
                    )
                    Column(Modifier.weight(0.7f)) {
                        AccuracyText(info)
                        val moved = track.movedMetersAt(index)
                        Text(
                            text = if (moved != null) {
                                stringResource(R.string.trackedit_source_moved) + " · " +
                                    stringResource(R.string.trackedit_meters_value, moved)
                            } else {
                                stringResource(info.source.labelRes)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
            item { Box(Modifier.padding(bottom = 24.dp)) }
        }
    }
}

// --------------------------------------------------------------------------------------
// Small pieces
// --------------------------------------------------------------------------------------

@Composable
private fun AccuracyText(info: TrackVertexInfo) {
    val accuracy = info.accuracyMeters
    when {
        info.source == VertexSource.PLACED -> Text(
            text = stringResource(R.string.trackedit_vertex_accuracy_placed),
            style = MaterialTheme.typography.bodyMedium,
            color = PLACED_COLOUR,
        )

        accuracy == null -> Text(
            text = stringResource(R.string.trackedit_vertex_accuracy_unknown),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        else -> {
            val quality = GpsFixQuality.of(accuracy)
            Text(
                text = stringResource(R.string.gpslog_accuracy_value, accuracy) + " · " +
                    stringResource(quality.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                color = colourOf(quality),
            )
        }
    }
}

private val VertexSource.labelRes: Int
    get() = when (this) {
        VertexSource.WALKED -> R.string.trackedit_source_walked
        VertexSource.PLACED -> R.string.trackedit_source_placed
        VertexSource.MOVED -> R.string.trackedit_source_moved
    }

/** Close enough to see a path's edge on the basemap. */
private const val EDIT_ZOOM = 18.5

/** Beyond the campus maximum on purpose; see [TrackEditMapView]. */
private const val MAX_EDIT_ZOOM = 20.5

/** A gap bigger than a couple of paces is worth pointing out before it becomes a line. */
private const val WALK_GAP_WARNING_METERS = 10.0

private val TAP_SLOP = 18.dp

/** Matches the placed-vertex colour in `TrackEditLayerManager`. */
private val PLACED_COLOUR = Color(0xFF8E24AA)
