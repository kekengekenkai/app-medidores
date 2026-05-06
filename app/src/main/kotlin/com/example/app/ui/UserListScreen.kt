package com.example.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

private val esES = Locale("es", "ES")
private val esFormat = DecimalFormat("#,##0", DecimalFormatSymbols(esES))
private fun formatInt(v: Double): String = esFormat.format(v.toLong())

data class UserMetric(var name: String, var anterior: Double, var actual: Double, var completed: Boolean = false)

@Composable
fun UserListScreen(
  metrics: List<UserMetric>,
  currentDictatingIndex: Int?,
  onDictateStart: (Int, UserMetric) -> Unit,
  onDictateEnd: (Int, UserMetric) -> Unit,
  onDictate: (Int, UserMetric) -> Unit
) {
  LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
    itemsIndexed(metrics) { index, m ->
      UserMetricRow(
        user = m,
        index = index,
        isDicticating = currentDictatingIndex == index,
        onDictate = onDictate,
        onDictateStart = onDictateStart,
        onDictateEnd = onDictateEnd
      )
    }
  }
}

@Composable
fun UserMetricRow(
  user: UserMetric,
  index: Int,
  isDicticating: Boolean,
  onDictate: (Int, UserMetric) -> Unit,
  onDictateStart: (Int, UserMetric) -> Unit,
  onDictateEnd: (Int, UserMetric) -> Unit
) {
  val rowHeight = if (isDicticating) 83.dp else 72.dp
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .height(rowHeight)
      .border(BorderStroke(width = if (isDicticating) 2.dp else 0.dp, color = if (isDicticating) Color(0xFF3F51B5) else Color.Transparent), RoundedCornerShape(12.dp))
      .background(Color.White, shape = RoundedCornerShape(12.dp)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp)
    ) {
      Box(
        modifier = Modifier
          .size(12.dp)
          .background(if (isDicticating) Color(0xFFD32F2F) else Color.Transparent, CircleShape)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(text = user.name.uppercase(Locale.getDefault()) + if (user.completed) " ✓" else "",
        modifier = Modifier.weight(0.4f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(text = formatInt(user.anterior), modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
      Text(text = formatInt(user.actual), modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
      Text(text = formatInt(user.actual - user.anterior), modifier = Modifier.weight(0.2f), fontWeight = FontWeight.Bold, fontSize = 16.sp)
      Box(
        modifier = Modifier
          .size(40.dp)
          .padding(start = 4.dp)
          .pointerInput(Unit) {
            detectTapGestures(
              onPress = {
                onDictateStart(index, user)
                onDictate(index, user)
                try {
                  awaitRelease()
                } catch (_: Throwable) {
                }
                onDictateEnd(index, user)
              }
            )
          },
        contentAlignment = Alignment.Center
      ) {
        Icon(imageVector = Icons.Filled.Mic, contentDescription = "Dictar")
      }
    }
  }
}
