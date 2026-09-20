package th.ac.kmutnb.prachin.map.core.speech

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Says short warnings out loud, in Thai, with no network and no paid service.
 *
 * `android.speech.tts.TextToSpeech` is part of AOSP and synthesises on the device, so this
 * keeps working in aeroplane mode like everything else here. Nothing is downloaded and no
 * audio ever leaves the phone.
 *
 * Voice matters for this app specifically: a warning about a hazard is useless if reading
 * it means looking down at the screen, which is exactly what someone walking towards a bad
 * crossing should not do.
 *
 * The engine is bound lazily on the first utterance, so a user who never turns warnings on
 * never starts a TTS service. Once bound it is kept for the life of the process - the same
 * reasoning as [th.ac.kmutnb.prachin.map.map.LocalTileServer]: an idle service connection
 * costs almost nothing, while tearing it down between warnings would make the next one
 * arrive a second late, which for a warning is the whole of its value.
 */
class SpeechAnnouncer(context: Context) {

    private val appContext = context.applicationContext

    private var engine: TextToSpeech? = null

    /** Held until the engine reports ready, then spoken. Only the latest one matters. */
    private var pending: Utterance? = null

    private var state: State = State.IDLE

    private data class Utterance(val text: String, val interrupt: Boolean)

    private enum class State { IDLE, STARTING, READY, UNAVAILABLE }

    /**
     * True once the engine has been tried and cannot speak Thai.
     *
     * Exposed so the UI can say so once, rather than leaving the user to wonder why the
     * phone is silent. Thai voice data is missing on plenty of budget devices, and the
     * on-screen warning has to be enough on its own when it is.
     */
    val isUnavailable: Boolean get() = state == State.UNAVAILABLE

    /**
     * Speaks [text], or drops it if the device cannot.
     *
     * @param interrupt true to cut off whatever is being said. Used for a real danger:
     * finishing a sentence about a puddle while the walker reaches a bad crossing is the
     * wrong order to say things in.
     */
    fun speak(text: String, interrupt: Boolean = false) {
        if (text.isBlank()) return
        when (state) {
            State.UNAVAILABLE -> return
            State.READY -> utter(text, interrupt)
            State.STARTING -> pending = Utterance(text, interrupt)
            State.IDLE -> {
                pending = Utterance(text, interrupt)
                start()
            }
        }
    }

    /** Stops the current warning, for instance when navigation is stopped. */
    fun stop() {
        pending = null
        runCatching { engine?.stop() }
    }

    private fun start() {
        state = State.STARTING
        engine = TextToSpeech(appContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "text to speech unavailable, status=$status")
                state = State.UNAVAILABLE
                pending = null
                return@TextToSpeech
            }
            state = if (configure()) State.READY else State.UNAVAILABLE
            val queued = pending
            pending = null
            if (state == State.READY && queued != null) utter(queued.text, queued.interrupt)
        }
    }

    /** @return true when the engine can actually speak Thai. */
    private fun configure(): Boolean {
        val tts = engine ?: return false
        val result = runCatching { tts.setLanguage(THAI) }.getOrElse {
            Log.w(TAG, "setting the Thai locale failed", it)
            return false
        }
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "no Thai voice on this device, result=$result")
            return false
        }
        // Navigation guidance ducks music instead of stopping it, and keeps playing when
        // the phone is in the walker's pocket with the screen off.
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        // Slightly slower than default: these are warnings heard once, outdoors, next to
        // traffic, by someone who is walking.
        tts.setSpeechRate(SPEECH_RATE)
        return true
    }

    private fun utter(text: String, interrupt: Boolean) {
        val mode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        runCatching { engine?.speak(text, mode, null, UTTERANCE_ID) }
            .onFailure { Log.w(TAG, "speaking failed", it) }
    }

    private companion object {
        const val TAG = "SpeechAnnouncer"
        const val UTTERANCE_ID = "hazard"
        const val SPEECH_RATE = 0.95f
        val THAI: Locale = Locale("th", "TH")
    }
}
