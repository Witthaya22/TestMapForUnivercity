package th.ac.kmutnb.prachin.map.ui.hazard

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.model.HazardSeverity
import th.ac.kmutnb.prachin.map.data.model.HazardSound
import th.ac.kmutnb.prachin.map.data.model.HazardSoundOrigin
import th.ac.kmutnb.prachin.map.data.repository.HazardSoundImport
import th.ac.kmutnb.prachin.map.data.repository.HazardSoundRepository
import th.ac.kmutnb.prachin.map.ui.common.displayName
import th.ac.kmutnb.prachin.map.ui.common.labelRes
import th.ac.kmutnb.prachin.map.ui.common.messageRes

/**
 * Where warning sounds are added and listened to.
 *
 * A screen of its own, reached from the same settings section as the switch that turns the
 * sounds on, because that is where somebody goes when they want to use their own mp3.
 * Importing used to live at the bottom of the hazard editor, which meant the only way to
 * add a sound was to start marking a hazard you did not want and scroll past everything
 * the editor asks about it.
 *
 * Every sound has a play button on its own row. Two tones described as "two beats" and
 * "three beats" tell nobody what they will hear next to a road; the recording does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HazardSoundScreen(
    onBack: () -> Unit,
    viewModel: HazardSoundViewModel = viewModel(factory = HazardSoundViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }

    fun toast(message: String) = scope.launch { snackbarHostState.showSnackbar(message) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        viewModel.import(uri) { result ->
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
                title = { Text(stringResource(R.string.hazard_sound_title)) },
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The reason anyone opens this screen, so it comes before the list rather
            // than after it.
            Button(
                onClick = { importLauncher.launch(arrayOf("audio/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.hazard_sound_import))
            }
            Text(
                text = stringResource(
                    R.string.hazard_sound_import_hint,
                    HazardSoundRepository.MAX_BYTES / (1024 * 1024),
                    HazardSoundRepository.MAX_DURATION_MS / 1000,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            Text(
                text = stringResource(R.string.hazard_sound_list_title),
                style = MaterialTheme.typography.titleMedium,
            )

            state.sounds.forEach { sound ->
                SoundRow(
                    sound = sound,
                    defaultFor = state.defaults.entries
                        .filter { it.value == sound.id }
                        .map { it.key },
                    onPlay = { viewModel.preview(sound) },
                    onDelete = { confirmDelete = sound.id },
                )
                HorizontalDivider()
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.hazard_sound_where_used),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }

    confirmDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text(stringResource(R.string.hazard_sound_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = null
                        viewModel.delete(id) {
                            toast(context.getString(R.string.hazard_sound_deleted))
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SoundRow(
    sound: HazardSound,
    defaultFor: List<HazardSeverity>,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(sound.displayName(), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(sound.origin.labelRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Says plainly which hazards will play this without anyone choosing it.
            defaultFor.forEach { severity ->
                Text(
                    text = stringResource(
                        R.string.hazard_sound_default_for,
                        stringResource(severity.labelRes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        AssistChip(
            onClick = onPlay,
            label = { Text(stringResource(R.string.hazard_sound_preview)) },
        )
        if (sound.isRemovable) {
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private val HazardSoundOrigin.labelRes: Int
    get() = when (this) {
        HazardSoundOrigin.BUNDLED -> R.string.hazard_sound_origin_bundled
        HazardSoundOrigin.ASSET -> R.string.hazard_sound_origin_asset
        HazardSoundOrigin.IMPORTED -> R.string.hazard_sound_origin_imported
    }
