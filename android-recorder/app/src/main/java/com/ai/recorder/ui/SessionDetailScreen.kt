package com.ai.recorder.ui

import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.recorder.data.AppDatabase
import com.ai.recorder.data.SummaryState
 
import coil.compose.AsyncImage
import com.ai.recorder.export.Exporter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SessionDetailScreen(sessionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context) }
    val purple = Color(0xFF9C27B0)
    val btnWidth = 64.dp
    val session by produceState(initialValue = null as com.ai.recorder.data.SessionEntity?) {
        value = db.sessionDao().bySessionId(sessionId)
    }
    val images by db.imageDao().listBySession(sessionId).collectAsState(initial = emptyList())
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var duration by remember { mutableStateOf(0) }
    var position by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { player?.release(); player = null }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { onBack() }, colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White)) { Text("◀ 返回") }
            
            Button(onClick = { scope.launch { Exporter.exportSessionZip(context, sessionId) } }, colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White)) { Text("导出ZIP") }
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            item {
                Text(text = session?.title ?: "", color = Color.Black)
                Spacer(Modifier.height(6.dp))
                val meta = listOfNotNull(session?.time, session?.location, session?.people?.joinToString("·"), session?.hashtags?.joinToString(" ")).filter { it.isNotBlank() }
                Text(text = meta.joinToString("  |  "), color = Color(0xFF666666))
                Spacer(Modifier.height(12.dp))
                if (!session?.audioUri.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            try {
                                player?.release(); player = MediaPlayer()
                                player?.setDataSource(context, Uri.parse(session!!.audioUri))
                                player?.prepare()
                                player?.start()
                                duration = player?.duration ?: 0
                                scope.launch {
                                    while (player != null && player?.isPlaying == true) {
                                        position = player?.currentPosition ?: 0
                                        delay(300)
                                    }
                                }
                            } catch (_: Exception) {}
                        }, colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White)) { Text("播放音频") }
                        Button(onClick = { try { player?.pause() } catch (_: Exception) {} }, colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White)) { Text("暂停") }
                        Button(onClick = { try { player?.stop() } catch (_: Exception) {} }, colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White)) { Text("停止") }
                    }
                    if (duration > 0) {
                        Spacer(Modifier.height(6.dp))
                        Slider(value = position.toFloat(), onValueChange = { v -> position = v.toInt() }, valueRange = 0f..duration.toFloat(), onValueChangeFinished = { try { player?.seekTo(position) } catch (_: Exception) {} })
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            if (images.isNotEmpty()) {
                item { Text("图片：", color = Color.Black) }
                items(images) { img ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AsyncImage(model = img.imageUri, contentDescription = null, modifier = Modifier.size(64.dp))
                        Text(text = img.imageUri, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF555555), modifier = Modifier.weight(1f))
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)); Text("笔记：", color = Color.Black) }
            item { Text(text = session?.note ?: "", color = Color(0xFF333333)) }
            item { Spacer(Modifier.height(12.dp)); Text("转录：", color = Color.Black) }
            item { Text(text = session?.transcript ?: "", color = Color(0xFF333333)) }
            item { Spacer(Modifier.height(12.dp)); Text("总结：", color = Color.Black) }
            item {
                val summaryText = when {
                    !session?.summaryError.isNullOrBlank() -> "失败：${session?.summaryError}"
                    !session?.summary.isNullOrBlank() -> session?.summary
                    else -> "无"
                }
                Text(text = summaryText ?: "无", color = Color(0xFF333333))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
