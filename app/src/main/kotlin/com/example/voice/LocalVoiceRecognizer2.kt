package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class LocalVoiceRecognizer2(
  private val context: Context,
  private val onResult: (String) -> Unit,
  private val onError: (String) -> Unit
) : VoiceRecognizer {
  private var recognizer: SpeechRecognizer? = null
  private val mainHandler = Handler(Looper.getMainLooper())

  override fun start(prompt: String) {
    mainHandler.post {
      try {
        val finalPrompt = if (prompt.isEmpty()) "Dicta el valor" else prompt
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer?.setRecognitionListener(createListener())
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_PROMPT, finalPrompt)
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        recognizer?.startListening(intent)
      } catch (e: Exception) {
        onError("Error inicializando: ${e.message}")
      }
    }
  }

  private fun createListener() = object : RecognitionListener {
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
        1 -> "Timeout de red"
        2 -> "Error de red"
        3 -> "Error de audio"
        4 -> "Error del servidor"
        5 -> "Error del cliente"
        6 -> "Timeout de voz"
        7 -> "No se entendió"
        8 -> "Reconocimiento ocupado"
        9 -> "Sin permisos"
        else -> "Error: $error"
      }
      onError(msg)
    }
  }

  override fun destroy() {
    mainHandler.post {
      try { recognizer?.destroy() } catch (e: Exception) {}
      recognizer = null
    }
  }
}