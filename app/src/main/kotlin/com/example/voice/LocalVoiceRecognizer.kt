package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class LocalVoiceRecognizer(
  private val context: Context,
  private val onResult: (String) -> Unit,
  private val onError: (String) -> Unit,
  private val onFallback: () -> Unit = {}
) {
  private var speechRecognizer: SpeechRecognizer? = null
  private var isDestroyed = false

  fun start(prompt: String = "Dicta el valor") {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      onError("Reconocimiento local requiere Android 12+")
      return
    }
    isDestroyed = false

    val mainHandler = Handler(Looper.getMainLooper())
    mainHandler.post {
      if (isDestroyed) return@post

      speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
        setRecognitionListener(createListener())
      }

      if (isDestroyed) {
        speechRecognizer?.destroy()
        speechRecognizer = null
        return@post
      }

      val intent = createIntent(prompt)
      speechRecognizer?.startListening(intent)
    }
  }

  private fun createIntent(prompt: String): Intent {
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "es-ES")
      putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
      putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
      putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
      putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
      putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500)
      putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000)
    }
  }

  private fun createListener(): RecognitionListener {
    return object : RecognitionListener {
      override fun onReadyForSpeech(params: android.os.Bundle?) {
        android.util.Log.d("LocalVoiceRecognizer", "ready for speech")
      }

      override fun onBeginningOfSpeech() {
        android.util.Log.d("LocalVoiceRecognizer", "beginning of speech")
      }

      override fun onRmsChanged(rmsdB: Float) {}

      override fun onBufferReceived(buffer: ByteArray?) {}

      override fun onEndOfSpeech() {}

      override fun onResults(results: android.os.Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        android.util.Log.d("LocalVoiceRecognizer", "results: $text")
        onResult(text)
      }

      override fun onPartialResults(partialResults: android.os.Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val text = matches?.firstOrNull() ?: ""
        if (text.isNotEmpty()) {
          android.util.Log.d("LocalVoiceRecognizer", "partial: $text")
        }
      }

      override fun onEvent(eventType: Int, params: android.os.Bundle?) {}

      override fun onError(error: Int) {
        android.util.Log.e("LocalVoiceRecognizer", "error: $error")

        when (error) {
          SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
          SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> {
            android.util.Log.d("LocalVoiceRecognizer", "language unavailable, triggering fallback")
            destroy()
            onFallback()
            return
          }
          SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
            cancel()
            return
          }
        }

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
          else -> "Error de reconocimiento: $error"
        }
        onError(msg)
      }
    }
  }

  private fun cancel() {
    try {
      speechRecognizer?.cancel()
    } catch (e: Exception) {
      android.util.Log.e("LocalVoiceRecognizer", "cancel error", e)
    }
    destroy()
  }

  fun destroy() {
    isDestroyed = true
    try {
      speechRecognizer?.destroy()
    } catch (e: Exception) {
      android.util.Log.e("LocalVoiceRecognizer", "destroy error", e)
    }
    speechRecognizer = null
  }
}