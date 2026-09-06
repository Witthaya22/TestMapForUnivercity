package th.ac.kmutnb.prachin.map.ui.survey

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.model.PoiCategory
import th.ac.kmutnb.prachin.map.navigation.model.PathType
import th.ac.kmutnb.prachin.map.survey.PointSurveySession
import th.ac.kmutnb.prachin.map.ui.common.Formats
import th.ac.kmutnb.prachin.map.ui.common.labelRes
import th.ac.kmutnb.prachin.map.ui.map.CategoryPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyorScreen(
    onBack: () -> Unit,
    viewModel: SurveyorViewModel = viewModel(factory = SurveyorViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun report(success: Boolean) {
        val message = if (success) {
            context.getString(R.string.export_success)
        } else {
            context.getString(R.string.export_failed, "")
        }
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val exportPoisLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json"),
    ) { uri -> uri?.let { viewModel.exportPois(it, ::report) } }

    val exportTracksLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/geo+json"),
    ) { uri -> uri?.let { viewModel.exportTracks(it, ::report) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.survey_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { insets ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState()),
        ) {
            TabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(
                    selected = state.tab == SurveyTab.POINT,
                    onClick = { viewModel.selectTab(SurveyTab.POINT) },
                    text = { Text(stringResource(R.string.survey_tab_point)) },
                )
                Tab(
                    selected = state.tab == SurveyTab.TRACK,
                    onClick = { viewModel.selectTab(SurveyTab.TRACK) },
                    text = { Text(stringResource(R.string.survey_tab_track)) },
                )
            }

            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccuracyCard(state)

                when (state.tab) {
                    SurveyTab.POINT -> PointSurveySection(
                        state = state,
                        onStart = viewModel::startPointSurvey,
                        onStop = viewModel::stopPointSurvey,
                        onDiscard = viewModel::discardPointResult,
                        onSave = viewModel::savePoint,
                        onExport = { exportPoisLauncher.launch("pois_surveyed.geojson") },
                    )

                    SurveyTab.TRACK -> TrackSection(
                        state = state,
                        onStart = viewModel::startTrackRecording,
                        onStop = viewModel::stopTrackRecording,
                        onCancel = viewModel::cancelTrackRecording,
                        onExport = { exportTracksLauncher.launch("paths_surveyed.geojson") },
                        onTooShort = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.track_too_short),
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AccuracyCard(state: SurveyUiState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.survey_intro), style = MaterialTheme.typography.bodyMedium)
            val accuracy = state.currentAccuracy
            Text(
                text = if (accuracy != null) {
                    stringResource(R.string.survey_accuracy_current, accuracy)
                } else {
                    stringResource(R.string.map_searching_gps)
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun PointSurveySection(
    state: SurveyUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onDiscard: () -> Unit,
    onSave: (String, PoiCategory, String) -> Unit,
    onExport: () -> Unit,
) {
    val result = state.pointResult

    if (state.isCollectingPoint) {
        LinearProgressIndicator(
            progress = { state.collectedSamples.toFloat() / state.targetSamples },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(
                R.string.survey_collecting,
                state.collectedSamples,
                state.targetSamples,
            ),
        )
        Text(stringResource(R.string.survey_accuracy_average, state.averageAccuracyMeters))
        if (state.rejectedSamples > 0) {
            Text(
                text = stringResource(R.string.survey_rejected, state.rejectedSamples),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (state.collectedSamples == 0) {
            Text(
                text = stringResource(
                    R.string.survey_waiting_for_fix,
                    PointSurveySession.DEFAULT_MAX_ACCURACY_M.toInt(),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.survey_stop))
        }
        return
    }

    if (result != null) {
        SaveSurveyedPointForm(
            latLonText = result.point.format(),
            averageAccuracy = result.averageAccuracyMeters,
            spreadMeters = result.spreadMeters,
            sampleCount = result.sampleCount,
            onSave = onSave,
            onDiscard = onDiscard,
        )
        return
    }

    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.survey_start))
    }
    OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.survey_export_pois))
    }
}

@Composable
private fun SaveSurveyedPointForm(
    latLonText: String,
    averageAccuracy: Float,
    spreadMeters: Double,
    sampleCount: Int,
    onSave: (String, PoiCategory, String) -> Unit,
    onDiscard: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var category by remember { mutableStateOf(PoiCategory.CUSTOM) }

    Text(
        stringResource(R.string.survey_done_title),
        style = MaterialTheme.typography.titleMedium,
    )
    Text(stringResource(R.string.survey_result_point, latLonText))
    Text(stringResource(R.string.survey_accuracy_average, averageAccuracy))
    Text(stringResource(R.string.survey_spread, spreadMeters))
    Text(
        text = stringResource(R.string.survey_collecting, sampleCount, sampleCount),
        style = MaterialTheme.typography.bodySmall,
    )

    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text(stringResource(R.string.poi_edit_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    CategoryPicker(selected = category, onSelected = { category = it })
    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text(stringResource(R.string.poi_edit_description)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { onSave(name.trim(), category, description.trim()) },
            enabled = name.isNotBlank(),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.survey_save_point))
        }
        OutlinedButton(onClick = onDiscard, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.survey_discard))
        }
    }
}

@Composable
private fun TrackSection(
    state: SurveyUiState,
    onStart: () -> Unit,
    onStop: (String, PathType) -> Boolean,
    onCancel: () -> Unit,
    onExport: () -> Unit,
    onTooShort: () -> Unit,
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var type by remember { mutableStateOf(PathType.FOOTWAY) }

    if (state.isRecordingTrack) {
        Text(
            text = stringResource(
                R.string.track_recording,
                state.trackPointCount,
                Formats.distance(context, state.trackLengthMeters),
            ),
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.track_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PathTypePicker(selected = type, onSelected = { type = it })

        Button(
            onClick = { if (!onStop(name.trim(), type)) onTooShort() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.track_stop))
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_cancel))
        }
        return
    }

    if (state.simplifiedCount > 0) {
        Text(
            text = stringResource(
                R.string.track_simplified,
                state.trackPointCount,
                state.simplifiedCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(
        text = stringResource(R.string.track_saved_count, state.recordedTracks.size),
        style = MaterialTheme.typography.bodySmall,
    )

    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.track_start))
    }
    OutlinedButton(
        onClick = onExport,
        enabled = state.recordedTracks.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.survey_export_paths))
    }
}

@Composable
private fun PathTypePicker(selected: PathType, onSelected: (PathType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.track_type),
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PathType.entries.forEach { pathType ->
                FilterChip(
                    selected = pathType == selected,
                    onClick = { onSelected(pathType) },
                    label = { Text(stringResource(pathType.labelRes)) },
                )
            }
        }
    }
}
