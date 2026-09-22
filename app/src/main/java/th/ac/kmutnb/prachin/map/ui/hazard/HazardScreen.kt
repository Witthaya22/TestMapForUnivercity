package th.ac.kmutnb.prachin.map.ui.hazard

import android.graphics.PointF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.config.CampusConfig
import th.ac.kmutnb.prachin.map.data.model.HAZARD_SOUND_NONE
import th.ac.kmutnb.prachin.map.data.model.HazardPoint
import th.ac.kmutnb.prachin.map.data.model.HazardSeverity
import th.ac.kmutnb.prachin.map.data.model.HazardSound
import th.ac.kmutnb.prachin.map.data.model.HazardType
import th.ac.kmutnb.prachin.map.data.model.approachMeters
import th.ac.kmutnb.prachin.map.data.model.defaultSound
import th.ac.kmutnb.prachin.map.data.model.resolveHazardSound
import th.ac.kmutnb.prachin.map.data.repository.HazardSoundImport
import th.ac.kmutnb.prachin.map.map.GpsLogLayerManager
import th.ac.kmutnb.prachin.map.map.HazardLayerManager
import th.ac.kmutnb.prachin.map.map.rememberMapViewWithLifecycle
import th.ac.kmutnb.prachin.map.ui.common.displayName
import th.ac.kmutnb.prachin.map.ui.common.labelRes
import th.ac.kmutnb.prachin.map.ui.common.messageRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Marking dangerous spots by walking to them (F12).
 *
 * A map rather than a form, because a hazard is defined as much by how far it reaches as
 * by where it is: the shaded circle is the distance at which the phone will start warning,
 * and seeing it is the only way to tell that a fifteen-metre radius covers the whole
 * crossing or only half of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HazardScreen(
    onBack: () -> Unit,
    viewModel: HazardViewModel = viewModel(factory = HazardViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hazardLayers = remember { HazardLayerManager() }
    val positionLayers = remember { GpsLogLayerManager() }

    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var showList by rememberSaveable { mutableStateOf(false) }
    // Centred on the first fix only. Marking hazards means panning around to look at
    // radii, and a camera that snapped back every second would make that impossible.
    var hasCentred by remember { mutableStateOf(false) }

    fun toast(message: String) = scope.launch { snackbarHostState.showSnackbar(message) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json"),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.export(uri) { ok ->
            toast(
                if (ok) {
                    context.getString(R.string.export_success)
                } else {
                    context.getString(R.string.export_failed, "")
                },
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.import(uri) { result ->
            toast(
                if (result == null) {
                    context.getString(R.string.import_failed, "")
                } else {
                    context.getString(
                        R.string.hazard_import_result,
                        result.imported,
                        result.skipped,
                    )
                },
            )
        }
    }

    // Narrowed to audio, because the picker is reached from a field that says "warning
    // sound" and offering every file on the phone there helps nobody.
    val soundImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.importSound(uri) { result ->
            toast(
                when (result) {
                    is HazardSoundImport.Added -> context.getString(
                        R.string.hazard_sound_imported,
                        result.sound.name.ifBlank {
                            context.getString(R.string.hazard_sound_unnamed)
                        },
                    )

                    is HazardSoundImport.Rejected -> context.getString(result.reason.messageRes)
                },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hazard_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showList = true }) {
                        Text(stringResource(R.string.hazard_count, state.hazards.size))
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

            HazardMapView(
                config = config,
                styleUri = styleUri,
                hazardLayers = hazardLayers,
                positionLayers = positionLayers,
                onMapReady = { mapLibreMap = it },
                onHazardTapped = viewModel::select,
                onLongPressed = viewModel::markAt,
            )

            LaunchedEffect(state.hazards, hazardLayers.isAttached) {
                // Switched-off hazards are shown here, faded: this is where they are
                // managed, and one that has been dealt with still has to be findable.
                if (hazardLayers.isAttached) hazardLayers.setHazards(state.hazards, showInactive = true)
            }
            LaunchedEffect(state.locationState, positionLayers.isAttached) {
                if (!positionLayers.isAttached) return@LaunchedEffect
                positionLayers.setUserLocation(
                    point = state.currentPoint,
                    accuracyMeters = state.currentAccuracyMeters ?: 0f,
                )
            }
            LaunchedEffect(state.currentPoint, mapLibreMap) {
                if (hasCentred) return@LaunchedEffect
                val point = state.currentPoint ?: return@LaunchedEffect
                val map = mapLibreMap ?: return@LaunchedEffect
                map.animateCamera(CameraUpdateFactory.newLatLng(LatLng(point.lat, point.lon)))
                hasCentred = true
            }

            FloatingActionButton(
                onClick = {
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

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = viewModel::markHere,
                        enabled = state.currentPoint != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.hazard_add_here))
                    }
                    Text(
                        text = if (state.currentPoint == null) {
                            stringResource(R.string.hazard_wait_fix)
                        } else {
                            stringResource(R.string.hazard_add_hint)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    state.draft?.let { draft ->
        HazardEditorSheet(
            draft = draft,
            sounds = state.sounds,
            onChange = viewModel::updateDraft,
            onPreviewSound = viewModel::previewDraftSound,
            onImportSound = { soundImportLauncher.launch(arrayOf("audio/*")) },
            onDeleteSound = { id ->
                viewModel.deleteSound(id) {
                    toast(context.getString(R.string.hazard_sound_deleted))
                }
            },
            onSave = viewModel::saveDraft,
            onDismiss = viewModel::cancelDraft,
        )
    }

    state.selected?.let { hazard ->
        HazardDetailSheet(
            hazard = hazard,
            sounds = state.sounds,
            onDismiss = viewModel::dismissSelection,
            onEdit = viewModel::editSelected,
            onSetActive = { active -> viewModel.setActive(hazard.id, active) },
            onDelete = { viewModel.delete(hazard.id) },
        )
    }

    if (showList) {
        HazardListSheet(
            hazards = state.hazards,
            onDismiss = { showList = false },
            onSelect = { id ->
                showList = false
                viewModel.select(id)
            },
            onExport = {
                showList = false
                exportLauncher.launch("hazards.geojson")
            },
            onImport = {
                showList = false
                importLauncher.launch(arrayOf("*/*"))
            },
        )
    }
}

// --------------------------------------------------------------------------------------
// Map
// --------------------------------------------------------------------------------------

@Composable
private fun HazardMapView(
    config: CampusConfig,
    styleUri: String,
    hazardLayers: HazardLayerManager,
    positionLayers: GpsLogLayerManager,
    onMapReady: (MapLibreMap) -> Unit,
    onHazardTapped: (String) -> Unit,
    onLongPressed: (GeoPoint) -> Unit,
) {
    val mapView = rememberMapViewWithLifecycle()

    DisposableEffect(mapView, styleUri) {
        mapView.getMapAsync { map ->
            onMapReady(map)
            map.setStyle(Style.Builder().fromUri(styleUri)) { style ->
                // Hazards first, so the walker's own dot is never hidden under a warning
                // circle drawn on top of it.
                hazardLayers.attach(style)
                positionLayers.attach(style)
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(config.center.lat, config.center.lon))
                    .zoom(MARKING_ZOOM)
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

            map.addOnMapClickListener { latLng ->
                val screenPoint = map.projection.toScreenLocation(latLng)
                val hits = map.queryRenderedFeatures(
                    PointF(screenPoint.x, screenPoint.y),
                    HazardLayerManager.LAYER_POINTS,
                )
                val id = hits.firstNotNullOfOrNull {
                    it.getStringProperty(HazardLayerManager.PROPERTY_ID)
                }
                if (id != null) {
                    onHazardTapped(id)
                    true
                } else {
                    false
                }
            }

            map.addOnMapLongClickListener { latLng ->
                onLongPressed(GeoPoint(lat = latLng.latitude, lon = latLng.longitude))
                true
            }
        }
        onDispose {
            positionLayers.detach()
            hazardLayers.detach()
        }
    }

    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

// --------------------------------------------------------------------------------------
// Editor
// --------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HazardEditorSheet(
    draft: HazardDraft,
    sounds: List<HazardSound>,
    onChange: ((HazardDraft) -> HazardDraft) -> Unit,
    onPreviewSound: () -> Unit,
    onImportSound: () -> Unit,
    onDeleteSound: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(
                    if (draft.isEditing) R.string.hazard_edit_title else R.string.hazard_new_title,
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = draft.point.format(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(R.string.hazard_field_type),
                style = MaterialTheme.typography.titleSmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HazardType.entries.forEach { type ->
                    FilterChip(
                        selected = type == draft.type,
                        onClick = { onChange { it.copy(type = type) } },
                        label = { Text(stringResource(type.labelRes)) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.hazard_field_severity),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HazardSeverity.entries.forEach { severity ->
                    FilterChip(
                        selected = severity == draft.severity,
                        onClick = { onChange { it.copy(severity = severity) } },
                        label = { Text(stringResource(severity.labelRes)) },
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.hazard_field_radius,
                    draft.radiusMeters.roundToInt(),
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = draft.radiusMeters.toFloat(),
                onValueChange = { value ->
                    onChange { it.copy(radiusMeters = value.toDouble()) }
                },
                valueRange = HazardPoint.MIN_RADIUS_M.toFloat()..HazardPoint.MAX_RADIUS_M.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                // The number that actually matters: not how big the hazard is, but how far
                // out the phone will start warning about it.
                text = stringResource(
                    R.string.hazard_field_radius_hint,
                    alertRadiusOf(draft).roundToInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = { value -> onChange { it.copy(description = value) } },
                label = { Text(stringResource(R.string.hazard_field_description)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            HazardSoundPicker(
                sounds = sounds,
                selectedId = draft.soundId,
                severity = draft.severity,
                onSelect = { id -> onChange { it.copy(soundId = id) } },
                onPreview = onPreviewSound,
                onImport = onImportSound,
                onDelete = onDeleteSound,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_save))
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

/**
 * Chooses the tone that plays just before the spoken warning.
 *
 * Three kinds of choice in one row of chips, because to the person marking a hazard they
 * are one question: leave it to the severity, silence it, or pick a sound. The default
 * comes first and is what a new mark starts on - most hazards want the tone that matches
 * how bad they are, and nobody should have to decide otherwise to get a sensible warning.
 *
 * Preview is not a nicety. A tone chosen in a quiet room is a tone nobody has checked can
 * be heard next to a road, which is where it has to work.
 */
@Composable
private fun HazardSoundPicker(
    sounds: List<HazardSound>,
    selectedId: String?,
    severity: HazardSeverity,
    onSelect: (String?) -> Unit,
    onPreview: () -> Unit,
    onImport: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val selected = sounds.firstOrNull { it.id == selectedId }
    val isSilent = selectedId == HAZARD_SOUND_NONE
    val defaultName = sounds.firstOrNull { it.id == severity.defaultSound.id }?.displayName()
        ?: stringResource(severity.defaultSound.labelRes)

    Text(
        text = stringResource(R.string.hazard_sound_field),
        style = MaterialTheme.typography.titleSmall,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selectedId == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.hazard_sound_default)) },
        )
        FilterChip(
            selected = isSilent,
            onClick = { onSelect(HAZARD_SOUND_NONE) },
            label = { Text(stringResource(R.string.hazard_sound_none)) },
        )
        sounds.forEach { sound ->
            FilterChip(
                selected = sound.id == selectedId,
                onClick = { onSelect(sound.id) },
                label = { Text(sound.displayName()) },
            )
        }
    }
    Text(
        text = stringResource(R.string.hazard_sound_hint, defaultName),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onPreview, enabled = !isSilent) {
            Text(stringResource(R.string.hazard_sound_preview))
        }
        TextButton(onClick = onImport) {
            Text(stringResource(R.string.hazard_sound_import))
        }
        // Only imported sounds can go. The generated tones and anything shipped in
        // assets/sounds/ are part of the build, and a campus with no warning tone left at
        // all is not a state worth reaching.
        if (selected != null && selected.isRemovable) {
            TextButton(onClick = { onDelete(selected.id) }) {
                Text(
                    text = stringResource(R.string.hazard_sound_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// --------------------------------------------------------------------------------------
// Detail and list
// --------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HazardDetailSheet(
    hazard: HazardPoint,
    sounds: List<HazardSound>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSetActive: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by rememberSaveable(hazard.id) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(hazard.type.labelRes),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(hazard.severity.labelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = severityColour(hazard.severity),
                )
            }

            if (!hazard.isActive) {
                Text(
                    text = stringResource(R.string.hazard_inactive),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hazard.description.isNotBlank()) {
                Text(hazard.description, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            DetailRow(
                stringResource(R.string.hazard_field_radius_label),
                stringResource(R.string.hazard_meters, hazard.radiusMeters.roundToInt()),
            )
            DetailRow(
                stringResource(R.string.hazard_alert_radius),
                stringResource(R.string.hazard_meters, hazard.alertRadiusMeters.roundToInt()),
            )
            DetailRow(
                stringResource(R.string.hazard_sound_field),
                resolveHazardSound(hazard, sounds)?.displayName()
                    ?: stringResource(R.string.hazard_sound_none),
            )
            DetailRow(
                stringResource(R.string.hazard_marked_source),
                hazard.gpsAccuracy
                    ?.let { stringResource(R.string.hazard_marked_by_gps, it) }
                    ?: stringResource(R.string.hazard_marked_by_tap),
            )
            DetailRow(
                stringResource(R.string.hazard_marked_at),
                formatMoment(hazard.createdAt),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                Button(onClick = { onEdit() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_edit))
                }
                OutlinedButton(onClick = { onSetActive(!hazard.isActive); onDismiss() }) {
                    Text(
                        stringResource(
                            if (hazard.isActive) {
                                R.string.hazard_turn_off
                            } else {
                                R.string.hazard_turn_on
                            },
                        ),
                    )
                }
            }
            TextButton(onClick = { confirmDelete = true }) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.hazard_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HazardListSheet(
    hazards: List<HazardPoint>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.hazard_list_title),
                style = MaterialTheme.typography.titleMedium,
            )

            if (hazards.isEmpty()) {
                Text(
                    text = stringResource(R.string.hazard_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            hazards.forEach { hazard ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(hazard.type.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (hazard.isActive) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        Text(
                            text = stringResource(
                                R.string.hazard_list_item_summary,
                                stringResource(hazard.severity.labelRes),
                                hazard.alertRadiusMeters.roundToInt(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = severityColour(hazard.severity),
                        )
                    }
                    TextButton(onClick = { onSelect(hazard.id) }) {
                        Text(stringResource(R.string.gpslog_list_open))
                    }
                }
                HorizontalDivider()
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                OutlinedButton(
                    onClick = onExport,
                    enabled = hazards.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.hazard_export))
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.hazard_import))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.55f),
        )
    }
}

/** Mirrors the marker colours in `HazardLayerManager`, so map and sheets agree. */
private fun severityColour(severity: HazardSeverity): Color =
    Color(HazardLayerManager.composeColour(severity))

/** The warning distance a draft would end up with, without building a HazardPoint. */
private fun alertRadiusOf(draft: HazardDraft): Double =
    draft.radiusMeters + draft.severity.approachMeters

private fun formatMoment(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

private const val MARKING_ZOOM = 18.0
