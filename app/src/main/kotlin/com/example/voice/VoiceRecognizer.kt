package com.example.voice

interface VoiceRecognizer {
  fun start(prompt: String)
  fun destroy()
}