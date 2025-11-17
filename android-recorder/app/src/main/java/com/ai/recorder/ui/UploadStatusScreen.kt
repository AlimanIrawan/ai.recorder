package com.ai.recorder.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import com.ai.recorder.StatusStore
import com.ai.recorder.SavedEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UploadStatusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val wm = WorkManager.getInstance(context)
    val audios by wm.getWorkInfosByTagLiveData("upload_audio").observeAsState(emptyList())
    val texts by wm.getWorkInfosByTagLiveData("upload_text").observeAsState(emptyList())
    val itemsList = (audios + texts).sortedByDescending { extractBaseName(it) }
    val saved = StatusStore(context).list().sortedByDescending { it.base }
    val purple = Color(0xFF9C27B0)
    val btnWidth = 48.dp
    val audioMap = audios.associateBy { extractBaseName(it) }
    val textMap = texts.associateBy { extractBaseName(it) }
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onBack() },
                colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White),
                modifier = Modifier.size(btnWidth, 32.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) { Text("◀") }
        }
        LazyColumn {
            itemsIndexed(saved) { _: Int, s: SavedEntry ->
                val disp = chineseDisplayFromBase(s.base)
                if (s.audio) {
                    Text("音频 $disp.m4a")
                    Text("已保存到本地")
                    audioMap[s.base]?.let { wi ->
                        Text(stateLabel(wi.state))
                    }
                }
                if (s.text) {
                    Text("文本 $disp.txt")
                    Text("已保存到本地")
                    textMap[s.base]?.let { wi ->
                        Text(stateLabel(wi.state))
                    }
                }
                Text("-------------------------", color = Color(0xFF777777))
            }
            val others = itemsList.filter { extractBaseName(it) !in saved.map { se -> se.base } }
            itemsIndexed(others) { index, info ->
                UploadItem(info)
                val curr = extractBaseName(info)
                val next = others.getOrNull(index + 1)?.let { extractBaseName(it) }
                if (next != null && next != curr) {
                    Text("-------------------------", color = Color(0xFF777777))
                }
            }
        }
    }
}

private fun stateLabel(s: WorkInfo.State): String {
    return when (s) {
        WorkInfo.State.ENQUEUED -> "排队中"
        WorkInfo.State.RUNNING -> "上传中"
        WorkInfo.State.SUCCEEDED -> "已完成"
        WorkInfo.State.FAILED -> "失败"
        WorkInfo.State.CANCELLED -> "已取消"
        WorkInfo.State.BLOCKED -> "阻塞"
        else -> s.name
    }
}

@Composable
private fun UploadItem(info: WorkInfo) {
    val tag = info.tags.firstOrNull { it == "upload_audio" || it == "upload_text" } ?: ""
    val tagName = when (tag) {
        "upload_audio" -> "音频"
        "upload_text" -> "文本"
        else -> tag
    }
    val stateLabel = when (info.state) {
        WorkInfo.State.ENQUEUED -> "排队中"
        WorkInfo.State.RUNNING -> "上传中"
        WorkInfo.State.SUCCEEDED -> "已完成"
        WorkInfo.State.FAILED -> "失败"
        WorkInfo.State.CANCELLED -> "已取消"
        WorkInfo.State.BLOCKED -> "阻塞"
        else -> info.state.name
    }
    val out = info.outputData
    val prog = info.progress
    val name = prog.getString("name") ?: out.getString("name") ?: ""
    Column {
        if (name.isNotEmpty()) Text("$tagName $name")
        Text("已保存到本地")
        Text(stateLabel)
        Spacer(Modifier.height(8.dp))
    }
}

private fun extractBaseName(info: WorkInfo): String {
    val out = info.outputData
    val prog = info.progress
    val base = prog.getString("base") ?: out.getString("base")
    if (base != null) return base
    val name = prog.getString("name") ?: out.getString("name") ?: ""
    return name.substringBefore('.')
}

private fun chineseDisplayFromBase(basename: String): String {
    return try {
        val src = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        val d = src.parse(basename) ?: Date()
        SimpleDateFormat("yyyy年MM月dd日 HH点mm分ss秒", Locale.CHINA).format(d)
    } catch (_: Exception) {
        basename
    }
}
