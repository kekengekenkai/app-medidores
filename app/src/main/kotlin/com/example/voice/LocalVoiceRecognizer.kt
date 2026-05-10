package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class LocalVoiceRecognizer(
  private val context: Context,
  private val onResult: (String) -> Unit,
  private val onError: (String) -> Unit
) {
  private var speechRecognizer: SpeechRecognizer? = null

  fun start(prompt: String = "Dicta el valor") {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      onError("Reconocimiento local requiere Android 12+")
      return
    }
    destroy()
    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    speechRecognizer?.setRecognitionListener(object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) {}
      override fun onBeginningOfSpeech() {}
      override fun onRmsChanged(rmsdB: Float) {}
      override fun onBufferReceived(buffer: ByteArray?) {}
      override fun onEndOfSpeech() {}
      override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        onResult(text)
      }
      override fun onPartialResults(partialResults: Bundle?) {}
      override fun onEvent(eventType: Int, params: Bundle?) {}
      override fun onError(error: Int) {
        val msg = when (error) {
          SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió el audio"
          SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz"
          SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocimiento ocupado"
          SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permisos de micrófono"
          SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
          SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
          SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
          SpeechRecognizer.ERROR_NETWORK -> "Error de red"
          SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red"
          SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Idioma no soportado"
          SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Idioma no disponible"
          else -> "Error de reconocimiento: $error"
        }
        onError(msg)
      }
    })
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
      putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
      putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
    }
    speechRecognizer?.startListening(intent)
  }

  fun cancel() {
    try {
      speechRecognizer?.cancel()
    } catch (e: Exception) {}
    destroy()
  }

  fun destroy() {
    try {
      speechRecognizer?.destroy()
    } catch (e: Exception) {}
    speechRecognizer = null
  }
}