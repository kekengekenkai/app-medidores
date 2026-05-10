package com.example.app

import android.content.Intent
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity.RESULT_OK
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale

import com.example.app.ui.UserListScreen
import com.example.app.ui.UserMetric
import com.example.coreexcel.WorkbookManager
import com.example.prefs.PrefsHelper
import com.example.voice.VoiceUtils
import android.speech.RecognizerIntent
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

data class WorkbookInfo(val name: String, val uri: String)
data class RowView(var name: String, var anterior: Double, var actual: Double)

private val esES = Locale("es", "ES")
private val esFormat = DecimalFormat("#,##0", DecimalFormatSymbols(esES))
private fun fmt(n: Double): String = esFormat.format(n.toLong())

private const val IMPORT_XLSX = 1001

private fun queryDisplayName(resolver: android.content.ContentResolver, uri: android.net.Uri): String? {
  val cursor = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
  cursor?.use {
    val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
    if (idx >= 0 && it.moveToFirst()) {
      return it.getString(idx)
    }
  }
  return null
}

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
  private lateinit var prefs: PrefsHelper
  private lateinit var importLauncher: ActivityResultLauncher<Array<String>>
  private lateinit var voiceLauncher: ActivityResultLauncher<Intent>
  private lateinit var micPermissionLauncher: ActivityResultLauncher<String>
  private var pendingMicRequest = false
  private val books = mutableStateListOf<WorkbookInfo>()
  private val selectedBook = mutableStateOf<WorkbookInfo?>(null)
  private val dropdownExpanded = mutableStateOf(false)
  private val rowsState = mutableStateListOf<RowView>()
  private var selectedRowIndex by mutableStateOf(0)
  private val metricsList = mutableStateListOf<UserMetric>()
  private var currentDictateIndex: Int? = null
  private var manualDictIndex: Int? by mutableStateOf(null)
  private val showManualInputDialog = mutableStateOf(false)
  private val manualInputValue = mutableStateOf("")
  private val pendingVoiceValue = mutableStateOf<Double?>(null)
  private var localRecognizer: com.example.voice.LocalVoiceRecognizer? = null
  private var dictatingXlsxRow: Int? = null
  private var isDirty = false
  private var roletePlayer: android.media.MediaPlayer? = null
  private val showRoleteDialog = mutableStateOf(false)

  private fun saveCurrentBook() {
    val book = selectedBook.value ?: return
    val uri = android.net.Uri.parse(book.uri)
    val resolver = contentResolver
    val manager = WorkbookManager(book.name, book.uri)
    try {
      resolver.openInputStream(uri)?.use { manager.load(it) }
    } catch (e: Exception) {
      android.util.Log.e("MainActivity", "save: load failed", e)
      return
    }
    for (i in rowsState.indices) {
      val r = rowsState[i]
      manager.updateActualForRow(i, r.actual)
    }
    try {
      resolver.openOutputStream(uri)?.use { manager.save(resolver, uri) }
      isDirty = false
      android.util.Log.d("MainActivity", "save: success")
    } catch (e: Exception) {
      android.util.Log.e("MainActivity", "save: write failed", e)
    }
  }

  private fun handleVoiceResult(spoken: String) {
    val digits = VoiceUtils.parseDigitsFromText(spoken)
    val number = digits?.toLongOrNull()?.toDouble()
    if (dictatingXlsxRow != null) {
      if (number != null) {
        handleXlsxVoiceResult(spoken, dictatingXlsxRow!!)
      } else {
        showManualInputDialog.value = true
        manualDictIndex = dictatingXlsxRow
        pendingVoiceValue.value = null
        manualInputValue.value = ""
      }
      dictatingXlsxRow = null
      return
    }
    if (currentDictateIndex != null && metricsList.isNotEmpty()) {
      val idx = currentDictateIndex!!
      val m = metricsList.getOrNull(idx)
      if (m != null) {
        val consumption = number?.let { it - m.anterior } ?: Double.NaN
        val (minBound, maxBound) = prefs.loadConsumptionBounds()
        if (number != null && consumption in minBound..maxBound) {
          val updated = m.copy(actual = number, completed = true)
          metricsList[idx] = updated
          currentDictateIndex = idx + 1
          if (idx + 1 < metricsList.size) {
            startDictationForIndex(idx + 1)
          } else {
            currentDictateIndex = null
          }
        } else {
          showManualInputDialog.value = true
          manualDictIndex = idx
          pendingVoiceValue.value = number
          manualInputValue.value = number?.toString() ?: ""
          currentDictateIndex = null
        }
      }
    } else if (number != null) {
      showManualInputDialog.value = true
      manualDictIndex = selectedRowIndex
      pendingVoiceValue.value = number
    } else {
      showManualInputDialog.value = true
    }
  }

  private fun ensureMicPermission(): Boolean {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
      pendingMicRequest = true
      micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      return false
    }
    return true
  }

  private fun startDictationForIndex(index: Int) {
    if (index < 0 || index >= metricsList.size) return
    val useLocal = prefs.useLocalRecognition()
    if (useLocal && !ensureMicPermission()) return
    currentDictateIndex = index
    val name = metricsList.getOrNull(index)?.name ?: "item"
    val prompt = "Dicta valor de Actual para $name"
    prefs.saveLastDictationIndex(selectedBook.value?.uri ?: "", index)
    android.util.Log.d("MainActivity", "startDictationForIndex: useLocal=$useLocal")
    if (useLocal) {
      localRecognizer?.destroy()
      localRecognizer = com.example.voice.LocalVoiceRecognizer(
        this,
        onResult = { spoken -> handleVoiceResult(spoken) },
        onError = { msg ->
          android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
          currentDictateIndex = null
        },
        onFallback = {
          android.util.Log.d("MainActivity", "local failed, falling back to online")
          val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
          }
          voiceLauncher.launch(intent)
        }
      )
      localRecognizer?.start(prompt)
    } else {
      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
      }
      voiceLauncher.launch(intent)
    }
  }

  private fun startDictationForXlsxRow(index: Int) {
    if (index < 0 || index >= rowsState.size) return
    updateSelectedRow(index)
    val name = rowsState.getOrNull(index)?.name ?: "item"
    val prompt = "Dicta valor de Actual para $name"
    val useLocal = prefs.useLocalRecognition()
    if (useLocal && !ensureMicPermission()) return
    android.util.Log.d("MainActivity", "startDictationForXlsxRow: useLocal=$useLocal")
    if (useLocal) {
      localRecognizer?.destroy()
      localRecognizer = com.example.voice.LocalVoiceRecognizer(
        this,
        onResult = { spoken -> handleXlsxVoiceResult(spoken, index) },
        onError = { msg ->
          android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        },
        onFallback = {
          android.util.Log.d("MainActivity", "local failed, falling back to online")
          dictatingXlsxRow = index
          val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
          }
          voiceLauncher.launch(intent)
        }
      )
      localRecognizer?.start(prompt)
    } else {
      dictatingXlsxRow = index
      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
      }
      voiceLauncher.launch(intent)
    }
  }

  private fun handleXlsxVoiceResult(spoken: String, index: Int) {
    val digits = VoiceUtils.parseDigitsFromText(spoken)
    val number = digits?.toLongOrNull() ?: return
    val row = rowsState.getOrNull(index) ?: return
    val consumption = number.toDouble() - row.anterior
    val (minBound, maxBound) = prefs.loadConsumptionBounds()
    if (consumption in minBound..maxBound) {
      rowsState[index] = row.copy(actual = number.toDouble())
      isDirty = true
      saveCurrentBook()
    } else {
      showManualInputDialog.value = true
      manualDictIndex = index
      pendingVoiceValue.value = number.toDouble()
      manualInputValue.value = number.toString()
    }
  }

  private fun loadWorkbook(uri: android.net.Uri): Boolean {
    try {
      val name = queryDisplayName(contentResolver, uri) ?: "Libro_${books.size + 1}"
      val resolver = contentResolver
      val manager = WorkbookManager(name, uri.toString())
      val stream = resolver.openInputStream(uri)
      if (stream == null) {
        android.util.Log.e("MainActivity", "openInputStream returned null for $uri")
        return false
      }
      stream.use { manager.load(it) }
      val wb = WorkbookInfo(name, uri.toString())
      books.add(wb)
      selectedBook.value = wb
      rowsState.clear()
      manager.rows.forEach { r ->
        rowsState.add(RowView(r.name, r.anterior, r.actual))
      }
      prefs.addRecentBook(uri.toString(), name, 0)
      try {
        val grant = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        contentResolver.takePersistableUriPermission(uri, grant)
      } catch (e: Exception) {
        android.util.Log.w("MainActivity", "takePersistableUriPermission failed", e)
      }
      val savedIndex = prefs.loadLastBook()?.lastRowIndex ?: 0
      selectedRowIndex = savedIndex.coerceAtMost(rowsState.size - 1)
      prefs.saveActiveBook(uri.toString(), name, selectedRowIndex)
      resumeDictationIfNeeded()
      return true
    } catch (e: Exception) {
      android.util.Log.e("MainActivity", "loadWorkbook failed", e)
      return false
    }
  }

  private fun updateSelectedRow(index: Int) {
    selectedRowIndex = index
    val book = selectedBook.value
    if (book != null) {
      prefs.addRecentBook(book.uri, book.name, index)
    }
  }

  private fun resumeDictationIfNeeded() {
    val idx = prefs.loadLastDictationIndex() ?: return
    if (idx >= 0 && idx < metricsList.size) {
      currentDictateIndex = idx
      startDictationForIndex(idx)
    }
  }

  private fun loadLastBookIfRecent() {
    val last = prefs.loadLastBook() ?: return
    val fiveDaysMs = 5L * 24 * 60 * 60 * 1000
    if (System.currentTimeMillis() - last.lastOpenedAt <= fiveDaysMs) {
      try {
        val uri = android.net.Uri.parse(last.uri)
        val ok = loadWorkbook(uri)
        if (ok) {
          resumeDictationIfNeeded()
          updateSelectedRow(last.lastRowIndex.coerceAtMost(rowsState.size - 1))
        }
      } catch (e: Exception) {
        // Ignore
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    localRecognizer?.destroy()
    if (isDirty) saveCurrentBook()
  }

  override fun onPause() {
    super.onPause()
    if (isDirty) saveCurrentBook()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    prefs = PrefsHelper(this)
    loadLastBookIfRecent()

    localRecognizer = com.example.voice.LocalVoiceRecognizer(
      this,
      onResult = { spoken -> handleVoiceResult(spoken) },
      onError = { msg ->
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()
        currentDictateIndex = null
      },
      onFallback = {
        android.util.Log.d("MainActivity", "fallback: using online recognition")
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
        }
        voiceLauncher.launch(intent)
      }
    )

    importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
      uri?.let {
        android.util.Log.d("MainActivity", "Importing URI: $it")
        val ok = try { loadWorkbook(it) } catch (e: Exception) {
          android.util.Log.e("MainActivity", "loadWorkbook exception", e)
          false
        }
        android.util.Log.d("MainActivity", "loadWorkbook result: $ok")
        if (!ok) {
          android.widget.Toast.makeText(this, "No se pudo cargar el archivo XLSX", android.widget.Toast.LENGTH_LONG).show()
        }
      }
    }

    voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == RESULT_OK && result.data != null) {
        val texts = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        val spoken = texts?.firstOrNull() ?: ""
        handleVoiceResult(spoken)
      }
    }

    micPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      pendingMicRequest = false
      if (granted) {
        android.util.Log.d("MainActivity", "Mic permission granted")
      } else {
        android.widget.Toast.makeText(this, "Permiso de micrófono denegado", android.widget.Toast.LENGTH_LONG).show()
      }
    }

    setContent {
      MaterialTheme {
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val settingsDialog = remember { mutableStateOf(false) }
        var showUsersScreen by remember { mutableStateOf(false) }
        val dictateStateEnabled = remember { mutableStateOf(true) }
        val bookDropdownExpanded = remember { mutableStateOf(false) }
        val settingsDropdownExpanded = remember { mutableStateOf(false) }
        val useLocalRecog = remember { mutableStateOf(prefs.useLocalRecognition()) }
        val listState = rememberLazyListState()
        val shareDialog = remember { mutableStateOf(false) }
        val shareSelectedUris = remember { mutableStateOf(setOf<String>()) }

        var initialScrollDone by remember { mutableStateOf(false) }
        LaunchedEffect(rowsState.size) {
          if (!initialScrollDone && rowsState.isNotEmpty() && selectedRowIndex > 0) {
            listState.scrollToItem(selectedRowIndex.coerceAtMost(rowsState.size - 1))
            initialScrollDone = true
          }
        }

        Scaffold(
          snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
          topBar = {
            TopAppBar(
              title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Box(modifier = Modifier.weight(1f)) {
                    Text(
                      text = selectedBook.value?.name ?: prefs.loadLastBook()?.name ?: "Sin libro",
                      style = MaterialTheme.typography.headlineSmall,
                      modifier = Modifier.clickable { bookDropdownExpanded.value = true }
                    )
                    DropdownMenu(
                      expanded = bookDropdownExpanded.value,
                      onDismissRequest = { bookDropdownExpanded.value = false }
                    ) {
                      val recent = prefs.getRecentBooks()
                      val weekMs = 7L * 24 * 60 * 60 * 1000
                      recent.forEach { rb ->
                        val isOld = System.currentTimeMillis() - rb.importedAt > weekMs
                        DropdownMenuItem(
                          text = {
                            Column {
                              Text(rb.name, style = MaterialTheme.typography.titleMedium)
                              if (isOld) Text("antiguo", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                          },
                          onClick = {
                            bookDropdownExpanded.value = false
                            try {
                              val uri = android.net.Uri.parse(rb.uri)
                              selectedRowIndex = rb.lastRowIndex
                              loadWorkbook(uri)
                            } catch (e: Exception) {
                              android.util.Log.e("MainActivity", "Failed to load recent book", e)
                            }
                          }
                        )
                      }
                      DropdownMenuItem(
                        text = { Text("Importar XLSX…", style = MaterialTheme.typography.titleMedium) },
                        onClick = {
                          bookDropdownExpanded.value = false
                          importLauncher.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/vnd.ms-excel"))
                        }
                      )
                    }
                  }
                  IconButton(onClick = {
                    shareSelectedUris.value = emptySet()
                    shareDialog.value = true
                  }, modifier = Modifier.align(Alignment.CenterVertically)) {
                    Icon(imageVector = androidx.compose.material.icons.Icons.Default.Share, contentDescription = "Compartir")
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                }
              },
              actions = {
                Box {
                  TextButton(
                    onClick = {
                      useLocalRecog.value = prefs.useLocalRecognition()
                      settingsDropdownExpanded.value = true
                    },
                    modifier = Modifier.size(52.dp)
                  ) {
                    Text("⚙", style = MaterialTheme.typography.titleLarge)
                  }
                  DropdownMenu(
                    expanded = settingsDropdownExpanded.value,
                    onDismissRequest = { settingsDropdownExpanded.value = false }
                  ) {
                    DropdownMenuItem(
                      text = { Text("Configurar consumo…", style = MaterialTheme.typography.bodyLarge) },
                      onClick = {
                        settingsDropdownExpanded.value = false
                        settingsDialog.value = true
                      }
                    )
                    DropdownMenuItem(
                      text = {
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.SpaceBetween,
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          Text("Reconocimiento local", style = MaterialTheme.typography.bodyLarge)
                          Switch(checked = useLocalRecog.value, onCheckedChange = {
                            useLocalRecog.value = it
                            prefs.saveUseLocalRecognition(it)
                            android.util.Log.d("MainActivity", "Reconocimiento local cambiado a: $it")
                          })
                        }
                      },
                      onClick = {}
                    )
                    DropdownMenuItem(
                      text = { Text("Activar rolete", style = MaterialTheme.typography.bodyLarge) },
                      onClick = {
                        settingsDropdownExpanded.value = false
                        roletePlayer?.release()
                        roletePlayer = android.media.MediaPlayer.create(this@MainActivity, R.raw.rolete).also {
                          it.start()
                        }
                        showRoleteDialog.value = true
                      }
                    )
                  }
                }
              }
            )
          }
        ) { innerPadding ->
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(innerPadding)
          ) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
              Column(modifier = Modifier.weight(1f)) {
                Text(text = "Nombre")
                Text(text = "Anterior", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
              }
              Text(text = "Actual", modifier = Modifier.weight(0.5f))
              Text(text = "Consumo", modifier = Modifier.weight(0.5f))
              Spacer(modifier = Modifier.weight(0.25f))
            }
            HorizontalDivider()
            LazyColumn(
              state = listState,
              contentPadding = PaddingValues(vertical = 8.dp)
            ) {
              items(rowsState) { r ->
                val isSelected = rowsState.indexOf(r) == selectedRowIndex
                val hasActual = r.actual != 0.0
                val bgColor = when {
                  isSelected -> Color(0xFFE3F2FD)
                  !hasActual -> Color(0xFFFFF9C4)
                  else -> Color(0xFFF5F5F5)
                }
                Card(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 3.dp)
                    .clickable { updateSelectedRow(rowsState.indexOf(r)) },
                  border = BorderStroke(0.5.dp, Color(0xFFBDBDBD)),
                  colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(text = r.name, maxLines = 1)
                      Text(text = "Ant. ${fmt(r.anterior)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Text(text = if (hasActual) fmt(r.actual) else "—", modifier = Modifier.weight(0.5f))
                    Text(text = if (hasActual) fmt(r.actual - r.anterior) else "—", modifier = Modifier.weight(0.5f))
                    IconButton(onClick = {
                      startDictationForXlsxRow(rowsState.indexOf(r))
                    }, modifier = Modifier.size(36.dp)) {
                      Icon(imageVector = androidx.compose.material.icons.Icons.Default.Mic, contentDescription = "Dictar")
                    }
                  }
                }
              }
            }
            if (selectedBook.value != null) {
              Spacer(Modifier.height(8.dp))
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                  val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga el valor de Actual")
                  }
                  voiceLauncher.launch(intent)
                }) {
                  Text("Grabar")
                }
                Button(onClick = {
                  saveCurrentBook()
                }) {
                  Text("Guardar")
                }
              }
            }
          }
          if (showManualInputDialog.value) {
            val idx = manualDictIndex?.coerceIn(0, rowsState.size - 1) ?: selectedRowIndex
            val inputValue = remember { mutableStateOf(manualInputValue.value) }
            AlertDialog(
              onDismissRequest = { showManualInputDialog.value = false },
              title = { Text("Confirmar o corregir valor") },
              text = {
                Column {
                  val row = rowsState.getOrNull(idx)
                  if (row != null) {
                    val spokenVal = inputValue.value.toDoubleOrNull()
                    val cons = spokenVal?.let { it - row.anterior }
                    Text("Anterior: ${fmt(row.anterior)}", style = MaterialTheme.typography.headlineSmall)
                    Text("Valor actual: ${inputValue.value}", style = MaterialTheme.typography.headlineSmall)
                    if (cons != null) Text("Consumo: ${fmt(cons)}", style = MaterialTheme.typography.titleLarge, color = Color(0xFFFF5722))
                    Spacer(Modifier.height(8.dp))
                  }
                  OutlinedTextField(
                    value = inputValue.value,
                    onValueChange = { inputValue.value = it },
                    label = { Text("Valor de Actual") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                  )
                }
              },
              confirmButton = {
                Button(onClick = {
                  val v = inputValue.value.toDoubleOrNull()
                  if (v != null && idx in rowsState.indices) {
                    rowsState[idx] = rowsState[idx].copy(actual = v)
                    updateSelectedRow(idx)
                    isDirty = true
                    saveCurrentBook()
                  }
                  showManualInputDialog.value = false
                }) { Text("OK") }
              },
              dismissButton = { Button(onClick = { showManualInputDialog.value = false }) { Text("Cancelar") } }
            )
          }
          if (settingsDialog.value) {
            val currentBounds = prefs.loadConsumptionBounds()
            val minInput = remember { mutableStateOf(currentBounds.first.toString()) }
            val maxInput = remember { mutableStateOf(currentBounds.second.toString()) }
            AlertDialog(
              onDismissRequest = { settingsDialog.value = false },
              title = { Text("Ajustes de consumo") },
              text = {
                Column {
                  OutlinedTextField(
                    value = minInput.value,
                    onValueChange = { minInput.value = it },
                    label = { Text("Min consumo") }
                  )
                  Spacer(Modifier.height(8.dp))
                  OutlinedTextField(
                    value = maxInput.value,
                    onValueChange = { maxInput.value = it },
                    label = { Text("Max consumo") }
                  )
                }
              },
              confirmButton = {
                Button(onClick = {
                  val current = prefs.loadConsumptionBounds()
                  val min = minInput.value.toDoubleOrNull() ?: current.first
                  val max = maxInput.value.toDoubleOrNull() ?: current.second
                  prefs.saveConsumptionBounds(min, max)
                  settingsDialog.value = false
                }) { Text("Guardar") }
              },
              dismissButton = { Button(onClick = { settingsDialog.value = false }) { Text("Cerrar") } }
            )
          }
          if (shareDialog.value) {
            val recent = prefs.getRecentBooks()
            val weekMs = 7L * 24 * 60 * 60 * 1000
            AlertDialog(
              onDismissRequest = { shareDialog.value = false },
              title = { Text("Compartir libros") },
              text = {
                LazyColumn(Modifier.fillMaxWidth()) {
                  items(recent) { rb ->
                    val isOld = System.currentTimeMillis() - rb.importedAt > weekMs
                    val isSelected = shareSelectedUris.value.contains(rb.uri)
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                          shareSelectedUris.value = if (isSelected) {
                            shareSelectedUris.value - rb.uri
                          } else {
                            shareSelectedUris.value + rb.uri
                          }
                        }
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Checkbox(checked = isSelected, onCheckedChange = null)
                      Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(rb.name, style = MaterialTheme.typography.bodyLarge)
                        if (isOld) Text("antiguo", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                      }
                    }
                  }
                }
              },
              confirmButton = {
                Button(
                  onClick = {
                    val uris = shareSelectedUris.value.map { android.net.Uri.parse(it) }
                    if (uris.isNotEmpty()) {
                      val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putParcelableArrayListExtra(android.content.Intent.EXTRA_STREAM, ArrayList(uris))
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                      }
                      startActivity(android.content.Intent.createChooser(intent, "Compartir"))
                    }
                    shareDialog.value = false
                  },
                  enabled = shareSelectedUris.value.isNotEmpty()
                ) { Text("Compartir") }
              },
              dismissButton = { Button(onClick = { shareDialog.value = false }) { Text("Cancelar") } }
            )
          }
          if (showRoleteDialog.value) {
            val progress = remember { mutableFloatStateOf(0f) }
            val messages = listOf(
              "Escaneando consumo.....",
              "Detectando aires...",
              "Pava eléctrica..."
            )
            val currentMsg = remember { mutableStateOf(messages[0]) }
            LaunchedEffect(Unit) {
              val duration = 3000L
              val steps = 30
              val delay = duration / steps
              for (i in 0 until steps) {
                kotlinx.coroutines.delay(delay)
                progress.floatValue = (i + 1).toFloat() / steps
                val msgIndex = (i * messages.size / steps).coerceIn(0, messages.size - 1)
                currentMsg.value = messages[msgIndex]
              }
              showRoleteDialog.value = false
            }
            AlertDialog(
              onDismissRequest = { showRoleteDialog.value = false },
              title = { Text("Rolete") },
              text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Text(currentMsg.value, style = MaterialTheme.typography.bodyLarge)
                  Spacer(Modifier.height(16.dp))
                  LinearProgressIndicator(
                    progress = { progress.floatValue },
                    modifier = Modifier.fillMaxWidth()
                  )
                }
              },
              confirmButton = {
                Button(onClick = { showRoleteDialog.value = false }) { Text("OK") }
              }
            )
          }
          if (showUsersScreen) {
            if (metricsList.isEmpty()) {
              metricsList.addAll(
                listOf(
                  UserMetric("COMUNA DE SA PEREIRA FTE", 31940.00, 31940.00, false),
                  UserMetric("GAARETTO ALEJANDRA", 67593.00, 67848.00, false),
                  UserMetric("GAETTO ALEJANDRA", 7326.00, 7504.00, false),
                  UserMetric("MAINETTI GUSTAVO", 49175.00, 49357.00, false)
                )
              )
            }
            UserListScreen(
              metrics = metricsList,
              currentDictatingIndex = currentDictateIndex,
              onDictateStart = { idx, u ->
                currentDictateIndex = idx
                scope.launch { snackbarHostState.showSnackbar("escuchando") }
                startDictationForIndex(idx)
              },
              onDictateEnd = { idx, u ->
                scope.launch { snackbarHostState.showSnackbar("escuchando") }
              },
              onDictate = { idx, u -> startDictationForIndex(idx) }
            )
          }
        }
      }
    }
  }
}
