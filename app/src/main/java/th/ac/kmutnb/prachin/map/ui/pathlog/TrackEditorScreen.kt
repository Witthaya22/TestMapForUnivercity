package th.ac.kmutnb.prachin.map.ui.pathlog

import th.ac.kmutnb.prachin.map.map.ForestMapLook
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import android.annotation.SuppressLint
import android.graphics.PointF
import android.view.MotionEvent
import android.view.ViewConfiguration
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
import th.ac.kmutnb.prachin.map.data.model.TrackEdits
import th.ac.kmutnb.prachin.map.data.model.TrackLog
import th.ac.kmutnb.prachin.map.data.model.TrackVertexInfo
import th.ac.kmutnb.prachin.map.data.model.VertexSource
import th.ac.kmutnb.prachin.map.map.TrackEditLayerManager
import th.ac.kmutnb.prachin.map.map.rememberMapViewWithLifecycle
import th.ac.kmutnb.prachin.map.ui.common.BottomSheetCardMaxHeight
import th.ac.kmutnb.prachin.map.ui.common.Formats
import th.ac.kmutnb.prachin.map.ui.common.messageRes
import kotlin.math.hypot

/**
 * One saved track over the real basemap, point by point, with the means to correct it.
 *
 * The recording screens can say how good a walk was on average; only here can the walker
 * see *which* point was bad. Every vertex carries the circle its own fix accuracy draws,
 * over the same offline OpenStreetMap basemap the navigation uses, so a point whose circle
 * misses the path it was walked along stands out on sight.
 *
 * Corrections are made by hand, the way any map editor works: tap a vertex to select it,
 * drag it to where it belongs, tap the map to place new ones. The vertex keeps the offset
 * from the fingertip it was picked up with, so it does not jump under the finger. Every
 * edit keeps its provenance (see `TrackEdits`), so a corrected line never passes for a
 * measured one in the exported file.
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
    // Where the selected vertex is being dragged to; null when no drag is under way.
    var dragPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var fitted by remember { mutableStateOf(false) }
    var showList by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    // Metres from the track's end when walking on was asked for too far away from it.
    var confirmWalkGap by rememberSaveable { mutableStateOf<Double?>(null) }

    fun leave() {
        if (state.isDirty) confirmLeave = true else onBack()
    }

    fun flyTo(point: GeoPoint) {
        mapLibreMap?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(point.lat, point.lon)))
    }

    fun say(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Back leaves adding first; walking is only left by its own buttons, so a stray back
    // gesture cannot throw away a walk.
    BackHandler(enabled = state.mode != TrackEditMode.WALK) {
        when (state.mode) {
            TrackEditMode.ADD -> viewModel.endMode()
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
                dragAnchor = state.selected
                    ?.takeIf { state.canDragSelected }
                    ?.let { draft.points.getOrNull(it) },
                onMapReady = { mapLibreMap = it },
                onTap = viewModel::onMapTap,
                onDrag = { dragPoint = it },
                onDrop = { to ->
                    to?.let(viewModel::moveSelected)
                    dragPoint = null
                },
            )

            // --- overlays pushed into the map ------------------------------------------
            // While dragging, the line is drawn as it will be once dropped - the neighbours'
            // segments, the link back to the GPS position and all - so what is let go is
            // what was seen.
            val selectedIndex = state.selected
            val shown = remember(draft, selectedIndex, dragPoint) {
                val to = dragPoint
                if (to != null && selectedIndex != null && selectedIndex in draft.points.indices) {
                    TrackEdits.moveVertex(draft, selectedIndex, to)
                } else {
                    draft
                }
            }
            LaunchedEffect(shown, state.selected, state.showAccuracy) {
                layerManager.setTrack(shown, state.selected, state.showAccuracy)
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
                // The padding stays on the camera, so later fly-tos also land clear of
                // the card rather than behind it.
                map.moveCamera(framing(draft.points, padding))
            }

            // Adding starts on the point the new one will follow.
            LaunchedEffect(state.mode) {
                if (state.mode != TrackEditMode.ADD) return@LaunchedEffect
                (draft.points.getOrNull(state.insertIndex - 1)
                    ?: draft.points.getOrNull(state.insertIndex))?.let(::flyTo)
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
                            state.mode == TrackEditMode.ADD ->
                                AddPanel(
                                    insertIndex = state.insertIndex,
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
                                    dragPoint = dragPoint,
                                    canDelete = state.canDeleteVertex,
                                    onClose = { viewModel.select(null) },
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
                                    onWalkOn = {
                                        // Starting away from the end would join the two
                                        // with a straight line on the very first fix, before
                                        // anyone could read a warning - so ask first.
                                        val gap = walkGapMeters(draft, state.currentPoint)
                                        if (gap != null && gap > WALK_GAP_WARNING_METERS) {
                                            confirmWalkGap = gap
                                        } else {
                                            viewModel.startWalk()
                                        }
                                    },
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

    confirmWalkGap?.let { gap ->
        AlertDialog(
            onDismissRequest = { confirmWalkGap = null },
            text = { Text(stringResource(R.string.trackedit_walk_gap_confirm, gap)) },
            confirmButton = {
                TextButton(onClick = { confirmWalkGap = null; viewModel.startWalk() }) {
                    Text(stringResource(R.string.trackedit_walk_start_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWalkGap = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
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

// --------------------------------------------------------------------------------------
// Map
// --------------------------------------------------------------------------------------

@Composable
private fun TrackEditMapView(
    config: CampusConfig,
    styleUri: String,
    layerManager: TrackEditLayerManager,
    dragAnchor: GeoPoint?,
    onMapReady: (MapLibreMap) -> Unit,
    onTap: (GeoPoint, Int?) -> Unit,
    onDrag: (GeoPoint) -> Unit,
    onDrop: (GeoPoint?) -> Unit,
) {
    val mapView = rememberMapViewWithLifecycle()
    val tapped by rememberUpdatedState(onTap)
    val anchor by rememberUpdatedState(dragAnchor)
    val dragged by rememberUpdatedState(onDrag)
    val dropped by rememberUpdatedState(onDrop)
    val density = LocalDensity.current
    val tapSlopPx = with(density) { TAP_SLOP.toPx() }
    val grabRadiusPx = with(density) { GRAB_RADIUS.toPx() }

    DisposableEffect(mapView, styleUri) {
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
                ForestMapLook.apply(style)
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

            // One tap path for both the map's own clicks and the taps the drag listener
            // keeps from the map, so a tap beside the selected vertex still selects or adds.
            fun handleTap(screenPoint: PointF) {
                val latLng = map.projection.fromScreenLocation(screenPoint)
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
            }

            mapView.setOnTouchListener(
                VertexDragListener(
                    map = map,
                    anchor = { anchor },
                    grabRadiusPx = grabRadiusPx,
                    touchSlopPx = ViewConfiguration.get(mapView.context).scaledTouchSlop.toFloat(),
                    onDrag = { dragged(it) },
                    onDrop = { dropped(it) },
                    onTap = ::handleTap,
                ),
            )

            map.addOnMapClickListener { latLng ->
                handleTap(map.projection.toScreenLocation(latLng))
                true
            }
        }
        onDispose {
            mapView.setOnTouchListener(null)
            layerManager.detach()
        }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

/**
 * Lets a finger pick up the selected vertex and drag it, ahead of the map's own gestures.
 *
 * A touch that lands on the selected vertex is taken from the map entirely - otherwise the
 * map would pan along with the drag - and every other touch is passed through untouched,
 * so panning, zooming and tapping work as before. Only the selected vertex can be grabbed:
 * on a track of two hundred points nearly every pan would otherwise start on one.
 *
 * The vertex keeps the offset from the fingertip it was picked up with, so it does not jump
 * under the finger. A touch that never travels past the system touch slop moves nothing and
 * is handed to [onTap] instead: the map never saw it, so without this a tap on a neighbour
 * that sits inside the grab radius - three metres away at street zoom - would do nothing.
 * Only the finger that picked the vertex up moves it.
 */
private class VertexDragListener(
    private val map: MapLibreMap,
    private val anchor: () -> GeoPoint?,
    private val grabRadiusPx: Float,
    private val touchSlopPx: Float,
    private val onDrag: (GeoPoint) -> Unit,
    private val onDrop: (GeoPoint?) -> Unit,
    private val onTap: (PointF) -> Unit,
) : android.view.View.OnTouchListener {

    /** Vertex minus finger, in pixels, while a vertex is held; null when none is. */
    private var grab: PointF? = null
    private val down = PointF()
    private var dragging = false
    private var last: GeoPoint? = null
    private var pointerId = MotionEvent.INVALID_POINTER_ID

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: android.view.View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val vertex = anchor() ?: return false
                val at = map.projection.toScreenLocation(LatLng(vertex.lat, vertex.lon))
                if (hypot(at.x - event.x, at.y - event.y) > grabRadiusPx) return false
                grab = PointF(at.x - event.x, at.y - event.y)
                pointerId = event.getPointerId(0)
                down.set(event.x, event.y)
                dragging = false
                last = null
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val offset = grab ?: return false
                val index = event.findPointerIndex(pointerId).takeIf { it >= 0 } ?: return true
                val x = event.getX(index)
                val y = event.getY(index)
                if (!dragging && hypot(x - down.x, y - down.y) < touchSlopPx) {
                    return true
                }
                dragging = true
                val latLng = map.projection.fromScreenLocation(PointF(x + offset.x, y + offset.y))
                val point = GeoPoint(lat = latLng.latitude, lon = latLng.longitude)
                last = point
                onDrag(point)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (grab == null) return false
                val released = event.actionMasked == MotionEvent.ACTION_UP
                if (released && !dragging) onTap(PointF(event.x, event.y))
                finish(dropAt = last.takeIf { dragging && released })
                return true
            }

            // Lifting the finger that holds the vertex ends the drag where it was; another
            // finger left on the glass must not take the vertex over and make it jump.
            MotionEvent.ACTION_POINTER_UP -> {
                if (grab == null) return false
                if (event.getPointerId(event.actionIndex) == pointerId) finish(dropAt = last.takeIf { dragging })
                return true
            }

            // A second finger while a vertex is held is ignored, not handed to the map:
            // the map never saw the first finger, so it could not make sense of the pair.
            else -> return grab != null
        }
    }

    private fun finish(dropAt: GeoPoint?) {
        val wasHeld = grab != null
        grab = null
        dragging = false
        last = null
        pointerId = MotionEvent.INVALID_POINTER_ID
        if (wasHeld) onDrop(dropAt)
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
    dragPoint: GeoPoint?,
    canDelete: Boolean,
    onClose: () -> Unit,
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
    // Measured from where the receiver put the vertex, not from where it was a moment
    // ago, so a second correction still reports the whole distance from the measurement.
    val origin = info.originalPoint ?: track.points[index]
    val moved = when {
        info.source == VertexSource.PLACED -> null
        dragPoint != null -> GeoUtils.haversineMeters(origin, dragPoint)
        else -> track.movedMetersAt(index)
    }
    moved?.let {
        Text(
            text = stringResource(R.string.trackedit_vertex_moved_short, it),
            style = if (dragPoint != null) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodySmall
            },
        )
    }
    Text(
        text = stringResource(R.string.trackedit_drag_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

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
private fun AddPanel(insertIndex: Int, onDone: () -> Unit) {
    Text(
        text = stringResource(R.string.trackedit_add_position, insertIndex + 1),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(
        text = stringResource(R.string.trackedit_add_hint),
        style = MaterialTheme.typography.bodyMedium,
    )
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.trackedit_done))
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
    // From the old end to where the walk actually began once it has, so the straight
    // join stays on screen for the whole walk rather than vanishing with the first fix.
    val gap = walkGapMeters(track, state.walkPoints.firstOrNull() ?: state.currentPoint)
    if (gap != null) {
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

/** Straight-line metres from the track's end to [from]; null when either is missing. */
private fun walkGapMeters(track: TrackLog, from: GeoPoint?): Double? {
    val end = track.points.lastOrNull() ?: return null
    return from?.let { GeoUtils.haversineMeters(end, it) }
}

private val TAP_SLOP = 18.dp

/**
 * How far from the selected vertex a touch still picks it up. Wider than the dot on
 * purpose: a fingertip is about this size, and missing the dot pans the map instead.
 */
private val GRAB_RADIUS = 28.dp

/** Matches the placed-vertex colour in `TrackEditLayerManager`. */
private val PLACED_COLOUR = Color(0xFF8E24AA)
