package com.ai.recorder.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager

@Composable
fun UploadStatusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val wm = WorkManager.getInstance(context)
    val audios by wm.getWorkInfosByTagLiveData("upload_audio").observeAsState(emptyList())
    val texts by wm.getWorkInfosByTagLiveData("upload_text").observeAsState(emptyList())
    val itemsList = (audios + texts).sortedByDescending { it.id.toString() }
    Column(modifier = Modifier.fillMaxSize()) {
        Text("上传状态")
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onBack() }) { Text("返回") }
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(itemsList) { info ->
                UploadItem(info)
            }
        }
    }
}

@Composable
private fun UploadItem(info: WorkInfo) {
    val tag = info.tags.firstOrNull { it == "upload_audio" || it == "upload_text" } ?: ""
    val state = info.state.name
    val out = info.outputData
    val name = out.getString("name") ?: ""
    val mime = out.getString("mime") ?: ""
    val code = out.getInt("code", -1)
    val body = out.getString("body") ?: ""
    Column {
        Text("$tag $state")
        if (name.isNotEmpty()) Text(name)
        if (mime.isNotEmpty()) Text(mime)
        if (code != -1) Text("$code")
        if (body.isNotEmpty()) Text(body)
        Spacer(Modifier.height(8.dp))
    }
}
