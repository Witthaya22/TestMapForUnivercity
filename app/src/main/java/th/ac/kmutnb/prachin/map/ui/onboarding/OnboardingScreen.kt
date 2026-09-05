package th.ac.kmutnb.prachin.map.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import th.ac.kmutnb.prachin.map.R
import th.ac.kmutnb.prachin.map.core.ext.hasCoarseLocationPermission
import th.ac.kmutnb.prachin.map.core.ext.hasFineLocationPermission
import th.ac.kmutnb.prachin.map.core.ext.openAppSettings
import th.ac.kmutnb.prachin.map.data.repository.OfflineDownloadError
import th.ac.kmutnb.prachin.map.data.repository.OfflineDownloadState
import th.ac.kmutnb.prachin.map.ui.common.Formats
import th.ac.kmutnb.prachin.map.ui.common.messageRes

/**
 * First-run flow: explain the app, obtain precise location, then download the offline pack.
 *
 * A broken `campus_config.json` short-circuits everything, since neither the bbox for the
 * download nor the map's starting position can be derived without it.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = OnboardingViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshConnectivity()
        // Re-entering onboarding after the user granted permission in system settings should
        // not ask again.
        viewModel.onPermissionResult(
            precise = context.hasFineLocationPermission(),
            approximate = context.hasCoarseLocationPermission(),
        )
    }

    LaunchedEffect(state.step) {
        if (state.step == OnboardingStep.DONE) onFinished()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        viewModel.onPermissionResult(
            precise = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true,
            approximate = granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true,
        )
    }

    Scaffold { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val problem = state.configProblem
            if (problem != null) {
                ConfigProblemCard(
                    message = stringResource(problem.messageRes),
                    onRetry = viewModel::retryConfig,
                )
                return@Column
            }

            when (state.step) {
                OnboardingStep.WELCOME -> WelcomeStep(
                    onContinue = { viewModel.goToStep(OnboardingStep.PERMISSION) },
                )

                OnboardingStep.PERMISSION -> PermissionStep(
                    permission = state.permission,
                    onRequest = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    onOpenSettings = context::openAppSettings,
                )

                OnboardingStep.DOWNLOAD -> DownloadStep(
                    campusName = state.campusName,
                    isOnline = state.isOnline,
                    downloadState = state.downloadState,
                    onStart = viewModel::startDownload,
                    onSkip = viewModel::skipDownload,
                )

                OnboardingStep.DONE -> Unit
            }
        }
    }

    val downloadState = state.downloadState
    if (downloadState is OfflineDownloadState.Complete) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.download_success_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.download_success_body,
                        Formats.fileSize(downloadState.sizeBytes),
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.goToStep(OnboardingStep.DONE) }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
        )
    }
}

@Composable
private fun ConfigProblemCard(message: String, onRetry: () -> Unit) {
    Text(
        text = stringResource(R.string.setup_required_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(message, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
    }
    Text(stringResource(R.string.setup_how_to), style = MaterialTheme.typography.bodySmall)
    Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Text(
        text = stringResource(R.string.onboarding_welcome_title),
        style = MaterialTheme.typography.headlineMedium,
    )
    Text(
        text = stringResource(R.string.onboarding_welcome_body),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(8.dp))
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_continue))
    }
}

@Composable
private fun PermissionStep(
    permission: LocationPermissionState,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Text(
        text = stringResource(R.string.permission_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = stringResource(R.string.permission_rationale),
        style = MaterialTheme.typography.bodyLarge,
    )

    when (permission) {
        LocationPermissionState.GRANTED_APPROXIMATE -> WarningCard(
            title = stringResource(R.string.permission_coarse_only_title),
            body = stringResource(R.string.permission_coarse_only_body),
        )

        LocationPermissionState.DENIED -> WarningCard(
            title = stringResource(R.string.permission_denied_title),
            body = stringResource(R.string.permission_denied_body),
        )

        else -> Unit
    }

    Spacer(Modifier.height(8.dp))
    Button(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.permission_grant))
    }
    if (permission == LocationPermissionState.DENIED ||
        permission == LocationPermissionState.GRANTED_APPROXIMATE
    ) {
        TextButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_open_app_settings))
        }
    }
}

@Composable
private fun DownloadStep(
    campusName: String,
    isOnline: Boolean,
    downloadState: OfflineDownloadState,
    onStart: () -> Unit,
    onSkip: () -> Unit,
) {
    Text(
        text = stringResource(R.string.download_title),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(
        text = stringResource(R.string.download_body, campusName),
        style = MaterialTheme.typography.bodyLarge,
    )

    if (!isOnline) {
        WarningCard(
            title = stringResource(R.string.download_title),
            body = stringResource(R.string.download_offline_warning),
        )
    }

    when (downloadState) {
        is OfflineDownloadState.InProgress -> {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { downloadState.percent / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.download_progress, downloadState.percent),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.download_progress_size,
                    Formats.fileSize(downloadState.completedBytes),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        is OfflineDownloadState.Failed -> {
            WarningCard(
                title = stringResource(R.string.download_title),
                body = when (downloadState.error) {
                    OfflineDownloadError.NETWORK -> stringResource(R.string.download_error_network)
                    OfflineDownloadError.TILE_LIMIT -> stringResource(R.string.download_error_tile_limit)
                    OfflineDownloadError.OTHER -> stringResource(
                        R.string.download_error_generic,
                        downloadState.detail.orEmpty(),
                    )
                },
            )
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_retry))
            }
        }

        else -> {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStart,
                enabled = isOnline,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.download_start))
            }
        }
    }

    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.download_skip))
    }
}

@Composable
private fun WarningCard(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
