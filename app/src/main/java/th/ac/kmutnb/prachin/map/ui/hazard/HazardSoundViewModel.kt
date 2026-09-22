package th.ac.kmutnb.prachin.map.ui.hazard

import android.net.Uri
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
import th.ac.kmutnb.prachin.map.data.model.HazardPoint
import th.ac.kmutnb.prachin.map.data.model.HazardSeverity
import th.ac.kmutnb.prachin.map.data.model.HazardSound
import th.ac.kmutnb.prachin.map.data.model.HazardType
import th.ac.kmutnb.prachin.map.data.model.resolveHazardSound
import th.ac.kmutnb.prachin.map.data.repository.HazardSoundImport
import th.ac.kmutnb.prachin.map.di.AppContainer

data class HazardSoundUiState(
    val sounds: List<HazardSound> = HazardSound.bundled,
    /** Which sound each severity falls back to, for the badges on the list. */
    val defaults: Map<HazardSeverity, String> = emptyMap(),
)

/**
 * Managing the sounds a hazard warning can play, away from any one hazard.
 *
 * Its own screen because importing a sound used to mean starting to mark a hazard first,
 * then scrolling to the bottom of the editor - so the answer to "how do I use my own mp3"
 * was buried inside a task nobody was trying to do. Somebody who wants to add a sound
 * looks in settings, next to the switch that turns the sounds on.
 */
class HazardSoundViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(HazardSoundUiState())
    val uiState: StateFlow<HazardSoundUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            container.hazardSoundRepository.sounds.collect { sounds ->
                _uiState.update { it.copy(sounds = sounds, defaults = defaultsOf(sounds)) }
            }
        }
    }

    /** Resolved through the same rules a real warning uses, so the badges cannot lie. */
    private fun defaultsOf(sounds: List<HazardSound>): Map<HazardSeverity, String> =
        HazardSeverity.entries.mapNotNull { severity ->
            resolveHazardSound(probeFor(severity), sounds)?.let { severity to it.id }
        }.toMap()

    /** A hazard that chose no sound, purely to ask what it would fall back to. */
    private fun probeFor(severity: HazardSeverity) = HazardPoint(
        id = "probe",
        type = HazardType.OTHER,
        severity = severity,
        point = GeoPoint(lat = 0.0, lon = 0.0),
        radiusMeters = HazardPoint.DEFAULT_RADIUS_M,
        description = "",
        soundId = null,
        isActive = true,
        gpsAccuracy = null,
        createdAt = 0L,
        updatedAt = 0L,
    )

    fun preview(sound: HazardSound) = container.hazardSoundPlayer.play(sound)

    /** Says a phrase, so the walker can hear the words before meeting them on a road. */
    fun previewSpeech(text: String) = container.speechAnnouncer.speak(text, interrupt = true)

    /**
     * Plays the tone and then the words, in the order and with the gap a real warning has.
     *
     * Worth its own button: the two are configured separately, and whether they work
     * together on this phone - with its own speaker, its own missing Thai voice data - is
     * not something either preview on its own answers.
     */
    fun previewFullWarning(sound: HazardSound?, text: String) {
        if (sound == null) {
            previewSpeech(text)
        } else {
            container.hazardSoundPlayer.play(sound) { previewSpeech(text) }
        }
    }

    /** True once the engine has been tried and cannot speak Thai. */
    fun isVoiceUnavailable(): Boolean = container.speechAnnouncer.isUnavailable

    fun import(source: Uri, onResult: (HazardSoundImport) -> Unit) {
        viewModelScope.launch {
            val result = container.hazardSoundRepository.import(source)
            // Played straight back, so the answer to "is this the right file" is the file
            // itself rather than its name.
            if (result is HazardSoundImport.Added) container.hazardSoundPlayer.play(result.sound)
            onResult(result)
        }
    }

    fun delete(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            container.hazardSoundRepository.delete(id)
            onDeleted()
        }
    }

    override fun onCleared() {
        container.hazardSoundPlayer.release()
        container.speechAnnouncer.stop()
        super.onCleared()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as MapApplication
                HazardSoundViewModel(app.container)
            }
        }
    }
}
