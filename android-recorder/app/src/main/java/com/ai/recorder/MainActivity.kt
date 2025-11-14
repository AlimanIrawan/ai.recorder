package com.ai.recorder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ai.recorder.sync.SyncScheduler
import com.ai.recorder.ui.UploadStatusScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    val context = LocalContext.current
    val recorder = remember { Recorder(context) }
    val repo = remember { NoteRepository(context) }
    var title by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pendingStart by remember { mutableStateOf(false) }
    var startAt by remember { mutableStateOf<Long?>(null) }
    var showStatus by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && pendingStart) {
            pendingStart = false
            if (recorder.start()) isRecording = true
        } else {
            pendingStart = false
            message = "需要录音权限"
        }
    }

    if (showStatus) {
        UploadStatusScreen(onBack = { showStatus = false })
    } else {
        Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "保存目录：Download/airecording")
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题/文字内容") })
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            if (isRecording) return@Button
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                startAt = System.currentTimeMillis()
                if (recorder.start()) isRecording = true
            } else {
                pendingStart = true
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }) { Text("开始录音") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            if (!isRecording) return@Button
            val file = recorder.stop()
            isRecording = false
            if (file != null) {
                val base = repo.basenameFromMillis(startAt ?: System.currentTimeMillis())
                val uris = repo.saveAudioAndText(base, file, title)
                SyncScheduler.enqueue(context, base, uris.first, uris.second)
                message = "保存成功：$base"
            } else {
                message = "录音文件无效"
            }
        }) { Text("结束记录") }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = message, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { showStatus = true }) { Text("上传状态") }
    }
    }
}
