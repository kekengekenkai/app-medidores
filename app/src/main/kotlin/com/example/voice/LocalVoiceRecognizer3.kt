package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class LocalVoiceRecognizer3(
  private val context: Context,
  private val onResult: (String) -> Unit,
  private val onError: (String) -> Unit
) : VoiceRecognizer {
  private var recognizer: SpeechRecognizer? = null

  override fun start(prompt: String) {
    destroy()
    val finalPrompt = if (prompt.isEmpty()) "Dicta el valor" else prompt
    recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    recognizer?.setRecognitionListener(object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) {}
      override fun onBeginningOfSpeech() {}
      override fun onRmsChanged(rmsdB: Float) {}
      override fun onBufferReceived(buffer: ByteArray?) {}
      override fun onEndOfSpeech() {}
      override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        onResult(matches?.firstOrNull() ?: "")
      }
      override fun onPartialResults(partialResults: Bundle?) {}
      override fun onEvent(eventType: Int, params: Bundle?) {}
      override fun onError(error: Int) {
        val msg = when (error) {
          SpeechRecognizer.ERROR_NO_MATCH -> "No se entendió"
          SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Sin voz"
          SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Ocupado"
          SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Sin permisos"
          SpeechRecognizer.ERROR_CLIENT -> "Error cliente"
          SpeechRecognizer.ERROR_SERVER -> "Error servidor"
          SpeechRecognizer.ERROR_AUDIO -> "Error audio"
          SpeechRecognizer.ERROR_NETWORK -> "Error red"
          else -> "Error: $error"
        }
        onError(msg)
      }
    })
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_PROMPT, finalPrompt)
    }
    recognizer?.startListening(intent)
  }

  override fun destroy() {
    try { recognizer?.destroy() } catch (e: Exception) {}
    recognizer = null
  }
}