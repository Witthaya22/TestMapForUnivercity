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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
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
import th.ac.kmutnb.prachin.map.navigation.model.WalkPath
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
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AccuracyCard(state)

                PointSurveySection(
                    state = state,
                    onStart = viewModel::startPointSurvey,
                    onStop = viewModel::stopPointSurvey,
                    onDiscard = viewModel::discardPointResult,
                    onSave = viewModel::savePoint,
                    onExport = { exportPoisLauncher.launch("pois_surveyed.geojson") },
                )

                Text(
                    text = stringResource(R.string.survey_paths_moved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
