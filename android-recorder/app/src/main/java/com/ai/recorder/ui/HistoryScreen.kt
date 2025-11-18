package com.ai.recorder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.recorder.data.AppDatabase
import com.ai.recorder.data.SessionEntity
 

@Composable
fun HistoryScreen(onBack: () -> Unit, onOpen: (SessionEntity) -> Unit) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).sessionDao() }
    val purple = Color(0xFF9C27B0)
    val query = remember { mutableStateOf("") }
    val peopleQ = remember { mutableStateOf("") }
    val hashQ = remember { mutableStateOf("") }
    val flow = when {
        query.value.isBlank() && peopleQ.value.isBlank() && hashQ.value.isBlank() -> dao.listAll()
        else -> dao.searchAdvanced(query.value, peopleQ.value, hashQ.value)
    }
    val items by flow.collectAsState(initial = emptyList())
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onBack() },
                colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White)
            ) { Text("◀") }
            OutlinedTextField(
                value = query.value,
                onValueChange = { query.value = it },
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                singleLine = true,
                label = { Text("搜索") }
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = peopleQ.value,
                onValueChange = { peopleQ.value = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("人员筛选") }
            )
            OutlinedTextField(
                value = hashQ.value,
                onValueChange = { hashQ.value = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("#标签筛选") }
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items) { s ->
                HistoryItem(s, onOpen)
            }
        }
    }
}

@Composable
private fun HistoryItem(s: SessionEntity, onOpen: (SessionEntity) -> Unit) {
    val ctx = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().clickable { onOpen(s) }.padding(horizontal = 12.dp, vertical = 10.dp)) {
        val title = s.title?.takeIf { it.isNotBlank() }
            ?: s.summary?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: s.note?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: s.sessionId
        Text(text = title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        val meta = listOfNotNull(s.time, s.location, s.people?.joinToString("·"), s.hashtags?.joinToString(" ")).filter { it.isNotBlank() }
        Text(text = meta.joinToString("  |  "), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color(0xFF666666))
        Spacer(Modifier.height(2.dp))
        val audioLabel = when {
            !s.transcribeError.isNullOrBlank() -> "失败"
            s.audioState == com.ai.recorder.data.AudioState.done -> "完成转录"
            s.audioUri.isNullOrBlank() -> "无音频"
            s.audioState == com.ai.recorder.data.AudioState.transcribing -> "转录中"
            else -> "待转录"
        }
        val src = s.transcribeSource ?: ""
        val err = s.transcribeError ?: ""
        val stateLine = buildString {
            append("音频：").append(audioLabel)
            if (src.isNotBlank()) append(" （").append(src).append(")")
            if (err.isNotBlank()) append(" — ").append(err)
        }
        Text(text = stateLine, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color(0xFF888888))
        val summaryStateLabel = when {
            !s.summaryError.isNullOrBlank() -> "失败"
            !s.summary.isNullOrBlank() -> "已完成"
            else -> "无"
        }
        val summarySnippet = when {
            !s.summaryError.isNullOrBlank() -> s.summaryError
            !s.summary.isNullOrBlank() -> if (s.summary.length > 60) s.summary.substring(0, 60) + "…" else s.summary
            else -> null
        }
        val summaryLine = buildString {
            append("总结：").append(summaryStateLabel)
            if (!summarySnippet.isNullOrBlank()) append("  ").append(summarySnippet)
        }
        Text(text = summaryLine, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color(0xFF888888))
        val transcriptSnippet = s.transcript?.takeIf { it.isNotBlank() }?.let { if (it.length > 60) it.substring(0, 60) + "…" else it }
        transcriptSnippet?.let { Text(text = "转录：$it", maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color(0xFF888888)) }
        
    }
}
