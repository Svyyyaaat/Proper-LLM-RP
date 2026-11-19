package com.properrp.app.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Text-to-speech with the phone's own engine (no network, no cost): the passage read aloud in the app's
 * language. Started on first use, and again when the language changes.
 */
class Speaker(private val context: Context, private val locale: () -> Locale) {
    enum class State { IDLE, STARTING, READY, NO_VOICE, FAILED }

    var state by mutableStateOf(State.IDLE)
        private set
    /** Whether something is being read right now (the passage button shows Stop). */
    var speaking by mutableStateOf(false)
        private set

    private var tts: TextToSpeech? = null
    private var pending: Pair<String, Float>? = null
    private var started: Locale? = null

    private fun start() {
        if (tts != null) return
        state = State.STARTING
        tts = TextToSpeech(context.applicationContext) { status ->
            val t = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) { state = State.FAILED; return@TextToSpeech }
            val l = locale().also { started = it }
            val r = t.setLanguage(l)
            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) { state = State.NO_VOICE; return@TextToSpeech }
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { speaking = true }
                override fun onDone(id: String?) { speaking = false }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { speaking = false }
            })
            state = State.READY
            pending?.let { (text, rate) -> say(text, rate) }
            pending = null
        }
    }

    /** Reads [text] aloud ([rate] 1 = normal; a bit slower suits listening practice). */
    fun say(text: String, rate: Float = 1f) {
        // the language changed since the engine started: start it again in the new one
        if (tts != null && started != null && started != locale()) shutdown()
        when (state) {
            State.READY -> {
                val t = tts ?: return
                t.setSpeechRate(rate)
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "properrp")
            }
            State.NO_VOICE, State.FAILED -> {}
            else -> { pending = text to rate; start() }
        }
    }

    fun stop() { tts?.stop(); speaking = false }

    fun shutdown() { tts?.shutdown(); tts = null; state = State.IDLE; speaking = false }

    /** Why nothing is heard (a string resource), for the UI; null when fine. */
    fun problem(): Int? = when (state) {
        State.NO_VOICE -> com.properrp.app.R.string.tts_no_voice
        State.FAILED -> com.properrp.app.R.string.tts_unavailable
        else -> null
    }
}
