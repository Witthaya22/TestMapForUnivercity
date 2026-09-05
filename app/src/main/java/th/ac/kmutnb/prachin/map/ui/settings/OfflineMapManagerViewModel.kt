package th.ac.kmutnb.prachin.map.ui.settings

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
import th.ac.kmutnb.prachin.map.data.repository.OfflineDownloadState
import th.ac.kmutnb.prachin.map.data.repository.OfflinePackStatus
import th.ac.kmutnb.prachin.map.di.AppContainer

data class OfflineManagerUiState(
    val status: OfflinePackStatus = OfflinePackStatus(false, false, 0, 0),
    val downloadState: OfflineDownloadState = OfflineDownloadState.Idle,
)

class OfflineMapManagerViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(OfflineManagerUiState())
    val uiState: StateFlow<OfflineManagerUiState> = _uiState.asStateFlow()

    init {
        refreshStatus()
        viewModelScope.launch {
            container.offlineMapRepository.state.collect { downloadState ->
                _uiState.update { it.copy(downloadState = downloadState) }
                if (downloadState is OfflineDownloadState.Complete) {
                    container.preferences.setOfflineMapReady(true)
                    refreshStatus()
                }
            }
        }
    }

    private fun refreshStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(status = container.offlineMapRepository.status()) }
        }
    }

    fun download() {
        viewModelScope.launch { container.offlineMapRepository.startDownload() }
    }

    fun delete() {
        viewModelScope.launch {
            container.offlineMapRepository.deletePack()
            refreshStatus()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MapApplication
                OfflineMapManagerViewModel(app.container)
            }
        }
    }
}
