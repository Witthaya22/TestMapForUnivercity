package th.ac.kmutnb.prachin.map.ui.pathlog

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
import th.ac.kmutnb.prachin.map.core.geo.GeoPoint
import th.ac.kmutnb.prachin.map.data.config.CampusConfig
import th.ac.kmutnb.prachin.map.data.config.CampusConfigException
import th.ac.kmutnb.prachin.map.data.config.ConfigProblem
import th.ac.kmutnb.prachin.map.data.model.TrackEdits
import th.ac.kmutnb.prachin.map.data.model.TrackLog
import th.ac.kmutnb.prachin.map.di.AppContainer
import th.ac.kmutnb.prachin.map.location.LocationState
import th.ac.kmutnb.prachin.map.location.SatelliteInfo
import th.ac.kmutnb.prachin.map.survey.TrackRecorder

/** What a tap or the crosshair currently means in the editor. */
enum class TrackEditMode {
    /** Looking: a tap selects a vertex. */
    VIEW,

    /** The selected vertex follows the crosshair until confirmed. */
    MOVE,

    /** Each confirm (or tap) adds a hand-placed vertex at [TrackEditorUiState.insertIndex]. */
    ADD,

    /** Walking on from the end; fixes become new vertices. */
    WALK,
}

data class TrackEditorUiState(
    val config: CampusConfig? = null,
    val configProblem: ConfigProblem? = null,
    val styleUri: String? = null,

    val notFound: Boolean = false,
    /** The track as it is in the database. */
    val saved: TrackLog? = null,
    /** The track with this session's edits applied; what is drawn and what Save writes. */
    val draft: TrackLog? = null,
    val undoDepth: Int = 0,
    val isSaving: Boolean = false,

    val mode: TrackEditMode = TrackEditMode.VIEW,
    val selected: Int? = null,
    /** Where the next vertex goes in [TrackEditMode.ADD]; `draft.points.size` = the end. */
    val insertIndex: Int = 0,
    val showAccuracy: Boolean = true,

    val locationState: LocationState = LocationState.Searching(null, SatelliteInfo.UNKNOWN, null),
    /** The continuation walked so far, not yet joined to the draft. */
    val walkPoints: List<GeoPoint> = emptyList(),
    val walkLengthMeters: Double = 0.0,
    val walkFixCount: Int = 0,
    val walkRejectedCount: Int = 0,
    val walkAccuracyMeters: Float = 0f,
) {
    val isDirty: Boolean get() = draft != null && draft != saved

    val canUndo: Boolean get() = undoDepth > 0

    val currentPoint: GeoPoint? get() = (locationState as? LocationState.Available)?.fix?.point

    val currentAccuracyMeters: Float?
        get() = when (val location = locationState) {
            is LocationState.Available -> location.fix.accuracyMeters
            is LocationState.Searching -> location.lastAccuracyMeters
            else -> null
        }

    val canWalk: Boolean get() = currentPoint != null && mode == TrackEditMode.VIEW

    val canDeleteVertex: Boolean get() = draft?.let { TrackEdits.canDelete(it) } == true
}

/**
 * Drives the track editor: one saved track, edited vertex by vertex over the basemap.
 *
 * Edits are applied to a draft and written only on Save, with an undo stack in between, so
 * a slip of the thumb on a survey that took an afternoon costs one tap to take back. The
 * editing rules themselves live in [TrackEdits], which is pure and unit tested.
 */
class TrackEditorViewModel(
    private val container: AppContainer,
    private val trackId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackEditorUiState())
    val uiState: StateFlow<TrackEditorUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<TrackLog>()

    private var recorder: TrackRecorder? = null

    init {
        loadConfig()
        loadTrack()
        observeLocation()
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

    private fun loadTrack() {
        viewModelScope.launch {
            val track = container.trackLogRepository.find(trackId)
            _uiState.update {
                if (track == null) {
                    it.copy(notFound = true)
                } else {
                    it.copy(saved = track, draft = track, insertIndex = track.points.size)
                }
            }
        }
    }

    /**
     * The dot is shown the whole time, not only while walking: standing on the real path
     * and seeing the dot beside a vertex is the quickest check there is of which one is off.
     */
    private fun observeLocation() {
        viewModelScope.launch {
            container.locationSource.updates(minIntervalMs = FIX_INTERVAL_MS).collect { location ->
                _uiState.update { it.copy(locationState = location) }
                if (location is LocationState.Available) onFix(location)
            }
        }
    }

    private fun onFix(location: LocationState.Available) {
        val active = recorder ?: return
        active.offer(
            point = location.fix.point,
            accuracyMeters = location.fix.accuracyMeters,
            satellites = location.satellites,
        )
        _uiState.update {
            it.copy(
                walkPoints = active.points.toList(),
                walkLengthMeters = active.lengthMeters,
                walkFixCount = active.fixCount,
                walkRejectedCount = active.rejectedCount,
                walkAccuracyMeters = active.averageAccuracyMeters,
            )
        }
    }

    // ----------------------------------------------------------------------------------
    // Selection and modes
    // ----------------------------------------------------------------------------------

    fun select(index: Int?) = _uiState.update {
        if (it.mode != TrackEditMode.VIEW) it else it.copy(selected = index)
    }

    /**
     * A tap on the map: adds a vertex there while adding, otherwise selects the vertex
     * that was hit (or clears the selection when none was).
     */
    fun onMapTap(point: GeoPoint, vertexIndex: Int?) {
        when (_uiState.value.mode) {
            TrackEditMode.ADD -> addAt(point)
            TrackEditMode.VIEW -> select(vertexIndex)
            TrackEditMode.MOVE, TrackEditMode.WALK -> Unit
        }
    }

    fun setShowAccuracy(show: Boolean) = _uiState.update { it.copy(showAccuracy = show) }

    fun startMove() = _uiState.update {
        if (it.selected == null) it else it.copy(mode = TrackEditMode.MOVE)
    }

    fun confirmMove(to: GeoPoint) {
        val state = _uiState.value
        val index = state.selected ?: return
        if (state.mode != TrackEditMode.MOVE) return
        applyEdit { TrackEdits.moveVertex(it, index, to) }
        _uiState.update { it.copy(mode = TrackEditMode.VIEW) }
    }

    /** @param index where the first new vertex goes; the end of the line when null. */
    fun startAdd(index: Int? = null) = _uiState.update {
        val draft = it.draft ?: return@update it
        it.copy(
            mode = TrackEditMode.ADD,
            insertIndex = (index ?: draft.points.size).coerceIn(0, draft.points.size),
        )
    }

    /** Adds one vertex and stays in add mode, so a run of points is a run of taps. */
    fun addAt(point: GeoPoint) {
        val state = _uiState.value
        if (state.mode != TrackEditMode.ADD) return
        val index = state.insertIndex
        applyEdit { TrackEdits.insertVertex(it, index, point) }
        _uiState.update { it.copy(insertIndex = index + 1, selected = index) }
    }

    fun deleteSelected() {
        val index = _uiState.value.selected ?: return
        if (!_uiState.value.canDeleteVertex) return
        applyEdit { TrackEdits.deleteVertex(it, index) }
        _uiState.update { it.copy(selected = null) }
    }

    /** Leaves move or add mode without touching the draft. */
    fun endMode() = _uiState.update {
        if (it.mode == TrackEditMode.WALK) it else it.copy(mode = TrackEditMode.VIEW)
    }

    // ----------------------------------------------------------------------------------
    // Walking on from the end
    // ----------------------------------------------------------------------------------

    fun startWalk() {
        if (!_uiState.value.canWalk) return
        recorder = TrackRecorder()
        _uiState.update {
            it.copy(
                mode = TrackEditMode.WALK,
                selected = null,
                walkPoints = emptyList(),
                walkLengthMeters = 0.0,
                walkFixCount = 0,
                walkRejectedCount = 0,
                walkAccuracyMeters = 0f,
            )
        }
    }

    /** @return false when nothing was walked, so the screen can say so. */
    fun stopWalk(): Boolean {
        val walk = recorder?.finishContinuation()
        recorder = null
        if (walk != null) applyEdit { TrackEdits.appendWalk(it, walk) }
        _uiState.update { it.copy(mode = TrackEditMode.VIEW, walkPoints = emptyList()) }
        return walk != null
    }

    fun cancelWalk() {
        recorder = null
        _uiState.update { it.copy(mode = TrackEditMode.VIEW, walkPoints = emptyList()) }
    }

    // ----------------------------------------------------------------------------------
    // Undo and save
    // ----------------------------------------------------------------------------------

    private fun applyEdit(edit: (TrackLog) -> TrackLog) {
        val draft = _uiState.value.draft ?: return
        val edited = edit(draft)
        if (edited == draft) return
        undoStack.addLast(draft)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        _uiState.update { it.copy(draft = edited, undoDepth = undoStack.size) }
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        _uiState.update {
            val selected = it.selected?.takeIf { index -> index in previous.points.indices }
            it.copy(
                draft = previous,
                undoDepth = undoStack.size,
                selected = selected,
                mode = if (it.mode == TrackEditMode.WALK) it.mode else TrackEditMode.VIEW,
                insertIndex = it.insertIndex.coerceAtMost(previous.points.size),
            )
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        val draft = state.draft ?: return
        if (!state.isDirty || state.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            // Note and routing switch can be changed from the detail sheet while this
            // screen is open; take them from the row as it is now, not as it was.
            val current = container.trackLogRepository.find(trackId)
            val toWrite = if (current == null) {
                draft
            } else {
                draft.copy(note = current.note, isUsedForRouting = current.isUsedForRouting)
            }
            container.trackLogRepository.replace(toWrite)
            undoStack.clear()
            _uiState.update {
                it.copy(saved = toWrite, draft = toWrite, undoDepth = 0, isSaving = false)
            }
            onSaved()
        }
    }

    override fun onCleared() {
        recorder = null
        super.onCleared()
    }

    companion object {
        /** Matches the recording screens; the recorder's 3 m spacing does the thinning. */
        private const val FIX_INTERVAL_MS = 1_000L

        /** Enough for any sitting; a survey is not edited a hundred steps deep. */
        private const val MAX_UNDO = 50

        fun factory(trackId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MapApplication
                TrackEditorViewModel(app.container, trackId)
            }
        }
    }
}
