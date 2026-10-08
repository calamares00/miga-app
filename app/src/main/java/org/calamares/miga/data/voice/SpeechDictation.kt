package org.calamares.miga.data.voice

import org.calamares.miga.L10n
import org.calamares.miga.R
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

sealed interface DictationResult {
    data class Success(val text: String) : DictationResult
    /** [code] is the SpeechRecognizer error, to tell silence apart from real failures. */
    data class Error(val reason: String, val code: Int? = null) : DictationResult {
        /** Nothing (or nothing understandable) was said: not worth reporting when listening continuously. */
        val isSilence: Boolean
            get() = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
    }
}

/**
 * Thin wrapper over Android's built-in speech recognition. Transcription does not depend on the AI
 * provider; the raw text can be tidied up afterwards with the AI (see cleanUpDictation).
 */
object SpeechDictation {

    fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Starts listening in [languageTag] (e.g. "es-ES") and calls [onResult] exactly once when done,
     * with a result or an error. The returned [SpeechRecognizer] stays alive until
     * [SpeechRecognizer.destroy] is called, which is the caller's job (see the DisposableEffect in
     * the screen that uses it).
     */
    fun startListening(context: Context, languageTag: String, onResult: (DictationResult) -> Unit): SpeechRecognizer {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // The language comes from Settings; with the system default an English phone would
            // always dictate in English.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.forLanguageTag(languageTag).toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                onResult(
                    if (text.isNullOrBlank()) DictationResult.Error(L10n.str(R.string.didnt_catch), SpeechRecognizer.ERROR_NO_MATCH)
                    else DictationResult.Success(text)
                )
            }

            override fun onError(error: Int) {
                onResult(DictationResult.Error(describeError(error), error))
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        recognizer.startListening(intent)
        return recognizer
    }

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> L10n.str(R.string.didnt_catch_try_again)
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> L10n.str(R.string.no_speech_detected)
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> L10n.str(R.string.no_connection_speech_recognition)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> L10n.str(R.string.microphone_permission_missing)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> L10n.str(R.string.speech_recognition_busy_try_again)
        SpeechRecognizer.ERROR_CLIENT -> L10n.str(R.string.couldnt_start_microphone)
        else -> L10n.str(R.string.couldnt_recognise_audio)
    }
}
