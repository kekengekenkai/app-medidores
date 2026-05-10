package com.example.prefs

import android.content.Context

data class LastBookInfo(val uri: String, val name: String, val lastOpenedAt: Long, val lastRowIndex: Int)
data class RecentBookInfo(val uri: String, val name: String, val importedAt: Long, val lastOpenedAt: Long, val lastRowIndex: Int)

class PrefsHelper(private val context: Context) {
  private val prefs = context.getSharedPreferences("medidores_prefs", Context.MODE_PRIVATE)
  private val recentBooks = mutableListOf<RecentBookInfo>()

  init {
    loadRecentBooks()
  }

  private fun loadRecentBooks() {
    val count = prefs.getInt("recent_count", 0)
    recentBooks.clear()
    for (i in 0 until count) {
      val uri = prefs.getString("recent_uri_$i", null) ?: continue
      val name = prefs.getString("recent_name_$i", "") ?: ""
      val imported = prefs.getLong("recent_imported_$i", 0L)
      val opened = prefs.getLong("recent_opened_$i", 0L)
      val row = prefs.getInt("recent_row_$i", 0)
      recentBooks.add(RecentBookInfo(uri, name, imported, opened, row))
    }
  }

  private fun saveRecentBooks() {
    val editor = prefs.edit()
    editor.putInt("recent_count", recentBooks.size)
    for (i in recentBooks.indices) {
      val b = recentBooks[i]
      editor.putString("recent_uri_$i", b.uri)
      editor.putString("recent_name_$i", b.name)
      editor.putLong("recent_imported_$i", b.importedAt)
      editor.putLong("recent_opened_$i", b.lastOpenedAt)
      editor.putInt("recent_row_$i", b.lastRowIndex)
    }
    for (i in recentBooks.size until prefs.getInt("recent_count", 0) + 5) {
      editor.remove("recent_uri_$i")
      editor.remove("recent_name_$i")
      editor.remove("recent_imported_$i")
      editor.remove("recent_opened_$i")
      editor.remove("recent_row_$i")
    }
    editor.apply()
  }

  fun addRecentBook(uri: String, name: String, lastRowIndex: Int) {
    val now = System.currentTimeMillis()
    val existing = recentBooks.indexOfFirst { it.uri == uri }
    if (existing >= 0) {
      val old = recentBooks[existing]
      recentBooks[existing] = old.copy(lastOpenedAt = now, lastRowIndex = lastRowIndex)
      val book = recentBooks.removeAt(existing)
      recentBooks.add(0, book)
    } else {
      recentBooks.add(0, RecentBookInfo(uri, name, now, now, lastRowIndex))
      if (recentBooks.size > 7) recentBooks.removeAt(recentBooks.lastIndex)
    }
    saveRecentBooks()
    saveActiveBook(uri, name, lastRowIndex)
  }

  fun getRecentBooks(): List<RecentBookInfo> = recentBooks.toList()

  fun saveActiveBook(uri: String, name: String, lastRowIndex: Int) {
    val now = System.currentTimeMillis()
    prefs.edit()
      .putString("last_uri", uri)
      .putString("last_name", name)
      .putLong("last_opened", now)
      .putInt("last_row_index", lastRowIndex)
      .apply()
  }

    fun loadLastBook(): LastBookInfo? {
    val uri = prefs.getString("last_uri", null) ?: return null
    val name = prefs.getString("last_name", "") ?: ""
    val lastOpened = prefs.getLong("last_opened", 0L)
    val lastRowIndex = prefs.getInt("last_row_index", 0)
    return LastBookInfo(uri, name, lastOpened, lastRowIndex)
  }

  fun saveConsumptionBounds(min: Double, max: Double) {
    val minBits = java.lang.Double.doubleToRawLongBits(min)
    val maxBits = java.lang.Double.doubleToRawLongBits(max)
    prefs.edit()
      .putLong("consumption_min", minBits)
      .putLong("consumption_max", maxBits)
      .apply()
  }

  fun loadConsumptionBounds(): Pair<Double, Double> {
    val minBits = prefs.getLong("consumption_min", java.lang.Double.doubleToRawLongBits(10.0))
    val maxBits = prefs.getLong("consumption_max", java.lang.Double.doubleToRawLongBits(400.0))
    val min = java.lang.Double.longBitsToDouble(minBits)
    val max = java.lang.Double.longBitsToDouble(maxBits)
    return Pair(min, max)
    }

  fun saveLastDictationIndex(uri: String, index: Int) {
    prefs.edit()
      .putInt("last_dictate_index", index)
      .apply()
  }

  fun loadLastDictationIndex(): Int? {
    val v = prefs.getInt("last_dictate_index", -1)
    return if (v >= 0) v else null
  }

  fun saveUseLocalRecognition(use: Boolean) {
    prefs.edit().putBoolean("use_local_recognition", use).apply()
  }

  fun useLocalRecognition(): Boolean {
    return prefs.getBoolean("use_local_recognition", false)
  }

  fun saveLocalRecognizerType(type: Int) {
    prefs.edit().putInt("local_recognizer_type", type).apply()
  }

  fun loadLocalRecognizerType(): Int {
    return prefs.getInt("local_recognizer_type", 1)
  }
}
