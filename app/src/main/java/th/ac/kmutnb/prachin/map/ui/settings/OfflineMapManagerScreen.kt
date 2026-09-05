package th.ac.kmutnb.prachin.map.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.data.repository.OfflineDownloadError
import th.ac.kmutnb.prachin.map.data.repository.OfflineDownloadState
import th.ac.kmutnb.prachin.map.ui.common.Formats
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapManagerScreen(
    onBack: () -> Unit,
    viewModel: OfflineMapManagerViewModel = viewModel(factory = OfflineMapManagerViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmingDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.offline_manager_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (state.status.isComplete) {
                                R.string.offline_manager_ready
                            } else {
                                R.string.offline_manager_not_ready
                            },
                        ),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.status.exists) {
                        Text(
                            text = stringResource(
                                R.string.offline_manager_size,
                                Formats.fileSize(state.status.sizeBytes),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (state.status.downloadedAt > 0) {
                        Text(
                            text = stringResource(
                                R.string.offline_manager_downloaded_at,
                                DateFormat.getDateTimeInstance()
                                    .format(Date(state.status.downloadedAt)),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            when (val download = state.downloadState) {
                is OfflineDownloadState.InProgress -> {
                    LinearProgressIndicator(
                        progress = { download.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.download_progress, download.percent))
                    Text(
                        text = stringResource(
                            R.string.download_progress_size,
                            Formats.fileSize(download.completedBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = stringResource(
                            R.string.download_tiles_counted,
                            download.completedTiles.toInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                is OfflineDownloadState.Failed -> Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Text(
                        text = when (download.error) {
                            OfflineDownloadError.NETWORK ->
                                stringResource(R.string.download_error_network)

                            OfflineDownloadError.TILE_LIMIT ->
                                stringResource(R.string.download_error_tile_limit)

                            OfflineDownloadError.OTHER -> stringResource(
                                R.string.download_error_generic,
                                download.detail.orEmpty(),
                            )
                        },
                        modifier = Modifier.padding(16.dp),
                    )
                }

                is OfflineDownloadState.Complete -> Text(
                    text = stringResource(
                        R.string.download_success_body,
                        Formats.fileSize(download.sizeBytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )

                OfflineDownloadState.Idle -> Unit
            }

            Button(
                onClick = viewModel::download,
                enabled = state.downloadState !is OfflineDownloadState.InProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.offline_manager_redownload))
            }

            OutlinedButton(
                onClick = { confirmingDelete = true },
                enabled = state.status.exists,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.offline_manager_delete))
            }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            text = { Text(stringResource(R.string.offline_manager_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.delete()
                    },
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
