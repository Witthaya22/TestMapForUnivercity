package th.ac.kmutnb.prachin.map.ui.onboarding

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.core.ext.isOnline
import th.ac.kmutnb.prachin.map.data.config.CampusConfigException
import th.ac.kmutnb.prachin.map.data.config.ConfigProblem
import th.ac.kmutnb.prachin.map.data.repository.OfflineDownloadState
import th.ac.kmutnb.prachin.map.di.AppContainer

enum class OnboardingStep { WELCOME, PERMISSION, DOWNLOAD, DONE }

/** Precise / approximate / none, because approximate is not good enough to navigate with. */
enum class LocationPermissionState { UNKNOWN, GRANTED_PRECISE, GRANTED_APPROXIMATE, DENIED }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val campusName: String = "",
    /** Non-null when `campus_config.json` is unusable; blocks the whole flow. */
    val configProblem: ConfigProblem? = null,
    val permission: LocationPermissionState = LocationPermissionState.UNKNOWN,
    val downloadState: OfflineDownloadState = OfflineDownloadState.Idle,
    val isOnline: Boolean = true,
)

class OnboardingViewModel(
    private val application: Application,
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
        viewModelScope.launch {
            container.offlineMapRepository.state.collect { downloadState ->
                _uiState.update { it.copy(downloadState = downloadState) }
                if (downloadState is OfflineDownloadState.Complete) {
                    container.preferences.setOfflineMapReady(true)
                }
            }
        }
    }

    private fun loadConfig() {
        viewModelScope.launch {
            try {
                val config = container.campusRepository.config()
                _uiState.update { it.copy(campusName = config.campusName, configProblem = null) }
            } catch (e: CampusConfigException) {
                _uiState.update { it.copy(configProblem = e.problem) }
            }
        }
    }

    fun refreshConnectivity() {
        _uiState.update { it.copy(isOnline = application.isOnline()) }
    }

    fun onPermissionResult(precise: Boolean, approximate: Boolean) {
        val state = when {
            precise -> LocationPermissionState.GRANTED_PRECISE
            approximate -> LocationPermissionState.GRANTED_APPROXIMATE
            else -> LocationPermissionState.DENIED
        }
        _uiState.update { it.copy(permission = state) }
        if (state == LocationPermissionState.GRANTED_PRECISE) goToStep(OnboardingStep.DOWNLOAD)
    }

    fun goToStep(step: OnboardingStep) {
        if (step == OnboardingStep.DOWNLOAD) refreshConnectivity()
        _uiState.update { it.copy(step = step) }
    }

    fun startDownload() {
        refreshConnectivity()
        viewModelScope.launch { container.offlineMapRepository.startDownload() }
    }

    /**
     * Lets the user into the app without a pack. The map will be blank until they download
     * one, but the survey tools and the POI list still work, and forcing a download on a
     * phone with no signal would strand them.
     */
    fun skipDownload() {
        goToStep(OnboardingStep.DONE)
    }

    fun retryConfig() = loadConfig()

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MapApplication
                OnboardingViewModel(app, app.container)
            }
        }
    }
}
