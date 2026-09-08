package com.bmo00.miga.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

sealed interface DictationResult {
    data class Success(val text: String) : DictationResult
    data class Error(val reason: String) : DictationResult
}

/**
 * Envoltorio fino sobre el reconocimiento de voz nativo de Android: graba y transcribe en el
 * propio dispositivo, sin coste ni llamada de red - el texto en bruto resultante se limpia
 * después con el proveedor de IA configurado (ver DictationCleanupClient), pero la transcripción
 * en sí no depende de ningún proveedor, así que funciona igual sea cual sea el elegido.
 */
object SpeechDictation {

    fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Empieza a escuchar y llama a [onResult] una única vez, al terminar (éxito o error). El
     * [SpeechRecognizer] devuelto sigue vivo hasta que se llame a [SpeechRecognizer.destroy] -
     * quien llama es responsable de eso (ver DisposableEffect en la pantalla que lo usa).
     */
    fun startListening(context: Context, onResult: (DictationResult) -> Unit): SpeechRecognizer {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle) {
                val text = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                onResult(if (text.isNullOrBlank()) DictationResult.Error("No se ha entendido nada") else DictationResult.Success(text))
            }

            override fun onError(error: Int) {
                onResult(DictationResult.Error(describeError(error)))
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
        SpeechRecognizer.ERROR_NO_MATCH -> "No se ha entendido nada, prueba otra vez"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se ha detectado voz"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sin conexión para el reconocimiento de voz"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta el permiso de micrófono"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "El reconocimiento de voz está ocupado, prueba otra vez"
        SpeechRecognizer.ERROR_CLIENT -> "No se pudo iniciar el micrófono"
        else -> "No se pudo reconocer el audio"
    }
}
