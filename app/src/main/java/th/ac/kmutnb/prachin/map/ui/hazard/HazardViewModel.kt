package th.ac.kmutnb.prachin.map.ui.hazard

import android.app.Application
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import th.ac.kmutnb.prachin.map.MapApplication
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.config.CampusConfig
import th.ac.kmutnb.prachin.map.data.config.CampusConfigException
import th.ac.kmutnb.prachin.map.data.config.ConfigProblem
import th.ac.kmutnb.prachin.map.data.model.HazardPoint
import th.ac.kmutnb.prachin.map.data.model.HazardSeverity
import th.ac.kmutnb.prachin.map.data.model.HazardSound
import th.ac.kmutnb.prachin.map.data.model.HazardType
import th.ac.kmutnb.prachin.map.data.model.resolveHazardSound
import th.ac.kmutnb.prachin.map.data.repository.HazardImportResult
import th.ac.kmutnb.prachin.map.data.repository.HazardSoundImport
import th.ac.kmutnb.prachin.map.di.AppContainer
import th.ac.kmutnb.prachin.map.location.LocationState
import th.ac.kmutnb.prachin.map.location.SatelliteInfo

/**
 * A hazard being written, either new or opened for editing.
 *
 * Held as one object rather than as loose fields so that opening the editor, changing the
 * type twice and cancelling cannot leave half of a hazard behind in the screen state.
 */
data class HazardDraft(
    /** Null for a new mark; the existing id when editing. */
    val id: String? = null,
    val point: GeoPoint,
    val type: HazardType = HazardType.DOG,
    val severity: HazardSeverity = HazardSeverity.WARNING,
    val radiusMeters: Double = HazardPoint.DEFAULT_RADIUS_M,
    val description: String = "",
    /**
     * The chosen tone. Null is not "silent" but "whatever suits the severity", which is
     * what the picker shows as the default and what a draft starts as.
     */
    val soundId: String? = null,
    /** Accuracy of the fix it was marked from; null when placed by tapping the map. */
    val gpsAccuracy: Float? = null,
) {
    val isEditing: Boolean get() = id != null
}

data class HazardUiState(
    val config: CampusConfig? = null,
    val configProblem: ConfigProblem? = null,
    val styleUri: String? = null,

    val locationState: LocationState = LocationState.Searching(null, SatelliteInfo.UNKNOWN, null),
    val hazards: List<HazardPoint> = emptyList(),
    /** Bundled tones plus whatever has been imported, for the editor's sound picker. */
    val sounds: List<HazardSound> = HazardSound.bundled,

    /** Open editor, or null when just looking at the map. */
    val draft: HazardDraft? = null,
    /** The hazard whose detail sheet is open. */
    val selected: HazardPoint? = null,
) {
    val currentPoint: GeoPoint?
        get() = (locationState as? LocationState.Available)?.fix?.point

    val currentAccuracyMeters: Float?
        get() = when (val location = locationState) {
            is LocationState.Available -> location.fix.accuracyMeters
            is LocationState.Searching -> location.lastAccuracyMeters
            else -> null
        }

    val activeCount: Int get() = hazards.count { it.isActive }
}

/**
 * Marking hazards by walking to them.
 *
 * Standing at the spot is the point: the coordinate then comes from the same receiver that
 * will later decide whether to warn, so whatever offset that receiver has cancels out and
 * the warning fires where the hazard is rather than where a satellite photo put it. See
 * `docs/ACCURACY.md` A1.
 *
 * Marking by long-pressing the map is allowed too, and deliberately records no accuracy -
 * a tapped hazard is somebody's estimate, and the detail sheet says so.
 */
class HazardViewModel(
    private val application: Application,
    private val container: AppContainer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HazardUiState())
    val uiState: StateFlow<HazardUiState> = _uiState.asStateFlow()

    private var locationJob: Job? = null

    init {
        loadConfig()
        observeHazards()
        observeSounds()
        startLocationUpdates()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            try {
                val config = container.campusRepository.config()
                _uiState.update { it.copy(config = config, configProblem = null) }
            } catch (e: CampusConfigException) {
                _uiState.update { it.copy(configProblem = e.problem) }
                return@launch
            }
            container.preferences.tileSourceMode.collect { mode ->
                val uri = container.mapStyleProvider.styleUri(mode)
                _uiState.update { it.copy(styleUri = uri) }
            }
        }
    }

    private fun observeHazards() {
        viewModelScope.launch {
            // The full list, including hazards switched off: this is the screen where they
            // are managed, and one that has been dealt with still has to be findable.
            container.hazardRepository.hazards.collect { hazards ->
                _uiState.update { state ->
                    state.copy(
                        hazards = hazards,
                        selected = hazards.firstOrNull { it.id == state.selected?.id },
                    )
                }
            }
        }
    }

    private fun observeSounds() {
        viewModelScope.launch {
            container.hazardSoundRepository.sounds.collect { sounds ->
                _uiState.update { it.copy(sounds = sounds) }
            }
        }
    }

    private fun startLocationUpdates() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            container.locationSource.updates(minIntervalMs = FIX_INTERVAL_MS).collect { location ->
                _uiState.update { it.copy(locationState = location) }
            }
        }
    }

    // ----------------------------------------------------------------------------------
    // Marking
    // ----------------------------------------------------------------------------------

    /** Starts a mark at the walker's own position. Does nothing without a fix. */
    fun markHere() {
        val state = _uiState.value
        val point = state.currentPoint ?: return
        _uiState.update {
            it.copy(
                draft = HazardDraft(point = point, gpsAccuracy = state.currentAccuracyMeters),
                selected = null,
            )
        }
    }

    /**
     * Starts a mark at a spot on the map.
     *
     * Needed for the hazards a surveyor cannot stand in - the middle of the road they are
     * warning about, the ditch behind a fence.
     */
    fun markAt(point: GeoPoint) {
        _uiState.update { it.copy(draft = HazardDraft(point = point), selected = null) }
    }

    fun editSelected() {
        val hazard = _uiState.value.selected ?: return
        _uiState.update {
            it.copy(
                draft = HazardDraft(
                    id = hazard.id,
                    point = hazard.point,
                    type = hazard.type,
                    severity = hazard.severity,
                    radiusMeters = hazard.radiusMeters,
                    description = hazard.description,
                    soundId = hazard.soundId,
                    gpsAccuracy = hazard.gpsAccuracy,
                ),
                selected = null,
            )
        }
    }

    fun updateDraft(transform: (HazardDraft) -> HazardDraft) =
        _uiState.update { state -> state.copy(draft = state.draft?.let(transform)) }

    fun cancelDraft() = _uiState.update { it.copy(draft = null) }

    fun saveDraft() {
        val draft = _uiState.value.draft ?: return
        viewModelScope.launch {
            val id = draft.id
            if (id == null) {
                container.hazardRepository.create(
                    type = draft.type,
                    severity = draft.severity,
                    point = draft.point,
                    radiusMeters = draft.radiusMeters,
                    description = draft.description.trim(),
                    soundId = draft.soundId,
                    gpsAccuracy = draft.gpsAccuracy,
                )
            } else {
                container.hazardRepository.update(
                    id = id,
                    type = draft.type,
                    severity = draft.severity,
                    radiusMeters = draft.radiusMeters,
                    description = draft.description.trim(),
                    soundId = draft.soundId,
                )
            }
            _uiState.update { it.copy(draft = null) }
        }
    }

    // ----------------------------------------------------------------------------------
    // Existing hazards
    // ----------------------------------------------------------------------------------

    fun select(id: String) = _uiState.update { state ->
        state.copy(selected = state.hazards.firstOrNull { it.id == id })
    }

    fun dismissSelection() = _uiState.update { it.copy(selected = null) }

    fun setActive(id: String, active: Boolean) {
        viewModelScope.launch { container.hazardRepository.setActive(id, active) }
    }

    fun delete(id: String) {
        viewModelScope.launch { container.hazardRepository.delete(id) }
        _uiState.update { it.copy(selected = null) }
    }

    // ----------------------------------------------------------------------------------
    // Alert sounds
    // ----------------------------------------------------------------------------------

    /**
     * Plays what the draft would sound like on approach.
     *
     * Worth a button of its own: a tone chosen in a quiet room and never heard again is a
     * tone nobody has actually checked can be heard next to a road.
     */
    fun previewDraftSound() {
        val draft = _uiState.value.draft ?: return
        val sounds = _uiState.value.sounds
        // Resolved through the same function the warning uses, so what is previewed is
        // what will play - including the fall back to the severity's tone.
        val sound = resolveHazardSound(draft.toPreviewHazard(), sounds) ?: return
        container.hazardSoundPlayer.play(sound)
    }

    fun importSound(source: Uri, onResult: (HazardSoundImport) -> Unit) {
        viewModelScope.launch {
            val result = container.hazardSoundRepository.import(source)
            // Selecting it straight away saves the one step everybody would take next.
            if (result is HazardSoundImport.Added) {
                _uiState.update { state ->
                    state.copy(draft = state.draft?.copy(soundId = result.sound.id))
                }
            }
            onResult(result)
        }
    }

    /** Removes an imported sound. Bundled tones are part of the build and cannot go. */
    fun deleteSound(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            container.hazardSoundRepository.delete(id)
            _uiState.update { state ->
                val draft = state.draft
                state.copy(
                    draft = if (draft?.soundId == id) draft.copy(soundId = null) else draft,
                )
            }
            onDeleted()
        }
    }

    // ----------------------------------------------------------------------------------
    // Sharing a survey
    // ----------------------------------------------------------------------------------

    fun export(target: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                val json = container.hazardRepository.exportGeoJson()
                withContext(Dispatchers.IO) {
                    application.contentResolver.openOutputStream(target)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("cannot open the chosen file for writing")
                }
            }.isSuccess
            onResult(ok)
        }
    }

    fun import(source: Uri, onResult: (HazardImportResult?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val json = withContext(Dispatchers.IO) {
                    application.contentResolver.openInputStream(source)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    } ?: error("cannot open the chosen file for reading")
                }
                container.hazardRepository.importGeoJson(json)
            }.getOrNull()
            onResult(result)
        }
    }

    override fun onCleared() {
        locationJob?.cancel()
        // A preview still playing when the screen closes has nobody left to hear it.
        container.hazardSoundPlayer.release()
        super.onCleared()
    }

    companion object {
        private const val FIX_INTERVAL_MS = 1_000L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MapApplication
                HazardViewModel(app, app.container)
            }
        }
    }
}

/** A throwaway hazard, so a draft can be resolved by the same rules a real one is. */
private fun HazardDraft.toPreviewHazard(): HazardPoint = HazardPoint(
    id = id ?: "draft",
    type = type,
    severity = severity,
    point = point,
    radiusMeters = radiusMeters,
    description = description,
    soundId = soundId,
    isActive = true,
    gpsAccuracy = gpsAccuracy,
    createdAt = 0L,
    updatedAt = 0L,
)
