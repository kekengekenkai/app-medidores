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
) : VoiceRecognizer {
  private var speechRecognizer: SpeechRecognizer? = null

  override fun start(prompt: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      onError("Reconocimiento local requiere Android 12+")
      return
    }
    val finalPrompt = if (prompt.isEmpty()) "Dicta el valor" else prompt
    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
      setRecognitionListener(object : RecognitionListener {
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
            SpeechRecognizer.ERROR_CLIENT -> "Error del cliente de reconocimiento"
            SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
            SpeechRecognizer.ERROR_NETWORK -> "Error de red"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Timeout de red"
            SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
            else -> "Error de reconocimiento local: $error"
          }
          onError(msg)
        }
      })
    }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_PROMPT, finalPrompt)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        putExtra("android.speech.extra.PREFER_OFFLINE", true)
      }
    }
    speechRecognizer?.startListening(intent)
  }

  fun cancel() {
    speechRecognizer?.cancel()
    destroy()
  }

  override fun destroy() {
    speechRecognizer?.destroy()
    speechRecognizer = null
  }
}
