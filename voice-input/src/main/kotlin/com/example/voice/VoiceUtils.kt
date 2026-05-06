package com.example.voice

import java.util.Locale
import java.text.Normalizer

object VoiceUtils {
  // Very simple conversion: extract digits by concatenating spoken numbers (no decimals)
  fun parseNumberFromText(text: String, locale: Locale = Locale.getDefault()): Double? {
    val digits = parseDigitsFromText(text)
    return digits?.toDoubleOrNull()
  }

  // New: concatenates spoken number words into a digit string e.g. "doce cero cero cuarenta" -> "120040"
  fun parseDigitsFromText(text: String): String? {
    if (text.isBlank()) return null
    val s = text.lowercase(Locale.ROOT)
    // replace common separators
    val norm = s.replace("-", " ").replace(",", " ")
    // split into tokens
    val tokens = norm.split(Regex("\\s+"))
    val sb = StringBuilder()
    val map = mapOf(
      // 0-9
      "cero" to "0","uno" to "1","dos" to "2","tres" to "3","cuatro" to "4","cinco" to "5","seis" to "6","siete" to "7","ocho" to "8","nueve" to "9",
      // 10-19
      "diez" to "10","once" to "11","doce" to "12","trece" to "13","catorce" to "14","quince" to "15","dieciseis" to "16","diecisiete" to "17","dieciocho" to "18","diecinueve" to "19",
      // tens
      "veinte" to "20","treinta" to "30","cuarenta" to "40","cincuenta" to "50","sesenta" to "60","setenta" to "70","ochenta" to "80","noventa" to "90",
      // hundreds (basic)
      "cien" to "100"," ciento" to "100"
    )

    for (tok in tokens) {
      val t = tok.trim()
      if (t.isEmpty()) continue
      // if token already digits
      if (t.all { it.isDigit() }) {
        sb.append(t)
        continue
      }
      // normalize accents
      val normTok = Normalizer.normalize(t, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u")
        .replace("ñ","n")
      val mapped = map[normTok]
      if (mapped != null) sb.append(mapped) else {
        // if token contains a number inside (e.g., "120"), append as is
        if (normTok.all { it.isDigit() }) sb.append(normTok)
      }
    }
    return if (sb.isNotEmpty()) sb.toString() else null
  }
}
