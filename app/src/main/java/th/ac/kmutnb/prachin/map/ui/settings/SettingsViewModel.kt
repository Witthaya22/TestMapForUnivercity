package th.ac.kmutnb.prachin.map.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.data.model.TileSourceMode
import th.ac.kmutnb.prachin.map.di.AppContainer

data class SettingsUiState(
    val tileSourceMode: TileSourceMode = TileSourceMode.OFFLINE_PACK,
    val bundledTilesAvailable: Boolean = false,
    val autoRecalculate: Boolean = true,
    val keepScreenOn: Boolean = true,
    val surveyedPathsOnly: Boolean = false,
    /** How many paths the user has walked; zero makes [surveyedPathsOnly] a trap. */
    val surveyedPathCount: Int = 0,
    val debugUnlocked: Boolean = false,
    val versionName: String = "",
)

class SettingsViewModel(
    private val application: Application,
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var logoTapCount = 0

    init {
        viewModelScope.launch {
            val versionName = runCatching {
                application.packageManager
                    .getPackageInfo(application.packageName, 0)
                    .versionName
                    .orEmpty()
            }.getOrDefault("")
            val bundled = container.campusRepository.hasBundledTiles()
            _uiState.update { it.copy(versionName = versionName, bundledTilesAvailable = bundled) }
        }

        viewModelScope.launch {
            combine(
                container.preferences.tileSourceMode,
                container.preferences.autoRecalculate,
                container.preferences.keepScreenOn,
                container.preferences.debugUnlocked,
                container.preferences.surveyedPathsOnly,
            ) { mode, autoRecalculate, keepScreenOn, debugUnlocked, surveyedOnly ->
                Preferences(mode, autoRecalculate, keepScreenOn, debugUnlocked, surveyedOnly)
            }.collect { preferences ->
                _uiState.update {
                    it.copy(
                        tileSourceMode = preferences.tileSourceMode,
                        autoRecalculate = preferences.autoRecalculate,
                        keepScreenOn = preferences.keepScreenOn,
                        debugUnlocked = preferences.debugUnlocked,
                        surveyedPathsOnly = preferences.surveyedPathsOnly,
                    )
                }
            }
        }

        viewModelScope.launch {
            container.walkPathRepository.paths.collect { paths ->
                _uiState.update { it.copy(surveyedPathCount = paths.size) }
            }
        }
    }

    private data class Preferences(
        val tileSourceMode: TileSourceMode,
        val autoRecalculate: Boolean,
        val keepScreenOn: Boolean,
        val debugUnlocked: Boolean,
        val surveyedPathsOnly: Boolean,
    )

    fun setTileSourceMode(mode: TileSourceMode) {
        viewModelScope.launch { container.preferences.setTileSourceMode(mode) }
    }

    /** Settings -> restore the shipped places the user has deleted. */
    fun restoreSeededPois(onResult: (Int) -> Unit) {
        viewModelScope.launch { onResult(container.poiRepository.restoreSeededPois()) }
    }

    fun setSurveyedPathsOnly(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setSurveyedPathsOnly(enabled) }
    }

    fun setAutoRecalculate(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setAutoRecalculate(enabled) }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch { container.preferences.setKeepScreenOn(enabled) }
    }

    /** Seven taps on the logo reveals the GPS debug screen, as the brief specifies. */
    fun onLogoTapped() {
        logoTapCount++
        if (logoTapCount >= TAPS_TO_UNLOCK_DEBUG) {
            logoTapCount = 0
            viewModelScope.launch { container.preferences.setDebugUnlocked(true) }
        }
    }

    // ----------------------------------------------------------------------------------
    // Import / export
    // ----------------------------------------------------------------------------------

    /**
     * Writes every stored POI to a document the user picked.
     *
     * Uses the Storage Access Framework rather than MediaStore or a public directory, so no
     * storage permission is needed on any supported Android version.
     */
    fun exportPois(target: Uri, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val json = container.poiRepository.exportGeoJson()
                withContext(Dispatchers.IO) {
                    application.contentResolver.openOutputStream(target)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("cannot open the chosen file for writing")
                }
                json.length
            }
            onResult(
                result.fold(
                    onSuccess = { EXPORT_OK },
                    onFailure = { it.message ?: EXPORT_FAILED },
                ),
            )
        }
    }

    fun importPois(source: Uri, replaceExisting: Boolean, onResult: (Int, Int, String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.IO) {
                    application.contentResolver.openInputStream(source)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    } ?: error("cannot open the chosen file for reading")
                }
                container.poiRepository.importGeoJson(json, replaceExisting)
            }.fold(
                onSuccess = { onResult(it.imported, it.skipped, null) },
                onFailure = { onResult(0, 0, it.message ?: EXPORT_FAILED) },
            )
        }
    }

    companion object {
        const val TAPS_TO_UNLOCK_DEBUG = 7
        const val EXPORT_OK = "ok"
        private const val EXPORT_FAILED = "failed"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MapApplication
                SettingsViewModel(app, app.container)
            }
        }
    }
}
