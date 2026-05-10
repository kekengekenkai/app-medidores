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

  private var retryCount = 0
  private val maxRetries = 2

  fun start(prompt: String = "Dicta el valor") {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      onError("Reconocimiento local requiere Android 12+")
      return
    }
    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
      setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() { retryCount = 0 }
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onResults(results: Bundle?) {
          retryCount = 0
          val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
          val text = matches?.firstOrNull() ?: ""
          onResult(text)
        }
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onError(error: Int) {
          val canRetry = error == SpeechRecognizer.ERROR_CLIENT ||
                         error == SpeechRecognizer.ERROR_SERVER ||
                         error == SpeechRecognizer.ERROR_AUDIO ||
                         error == SpeechRecognizer.ERROR_NETWORK ||
                         error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT
          if (canRetry && retryCount < maxRetries) {
            retryCount++
            android.util.Log.d("LocalVoiceRecognizer", "Retry attempt $retryCount")
            destroy()
            start(prompt)
            return
          }
          retryCount = 0
          val msg = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió el audio"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Reconocimiento ocupado"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permisos de micrófono"
            SpeechRecognizer.ERROR_CLIENT -> "Error del cliente"
            SpeechRecognizer.ERROR_SERVER -> "Error del servidor"
            SpeechRecognizer.ERROR_AUDIO -> "Error de audio"
            else -> "Error de reconocimiento: $error"
          }
          onError(msg)
        }
      })
    }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
      putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
      putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
      putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
    }
    speechRecognizer?.startListening(intent)
  }

  fun cancel() {
    speechRecognizer?.cancel()
    destroy()
  }

  fun destroy() {
    speechRecognizer?.destroy()
    speechRecognizer = null
  }
}
