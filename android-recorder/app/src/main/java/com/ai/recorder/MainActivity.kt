package com.ai.recorder

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.app.NotificationManager
import android.provider.Settings
import android.view.WindowManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ai.recorder.sync.SyncScheduler
import com.ai.recorder.ui.HistoryScreen
import com.ai.recorder.ui.SessionDetailScreen
import com.ai.recorder.repo.SessionRepository
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import com.ai.recorder.pipeline.LocalPipeline
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.location.LocationManager
import android.location.Geocoder
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import java.io.File
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
 
import com.ai.recorder.data.AppDatabase

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
    val sessionRepo = remember { SessionRepository(context) }
    val purple = Color(0xFF9C27B0)
    val green = Color(0xFF4CAF50)
    val btnWidth = 48.dp
    var prevInterruptionFilter by remember { mutableStateOf<Int?>(null) }
    var title by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pendingStart by remember { mutableStateOf(false) }
    var startAt by remember { mutableStateOf<Long?>(null) }
    var showStatus by remember { mutableStateOf(false) }
    var showSaveChoice by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var pendingRecordedFile by remember { mutableStateOf<File?>(null) }
    var pendingBasename by remember { mutableStateOf<String?>(null) }
    val statusStore = remember { StatusStore(context) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var viewingSessionId by remember { mutableStateOf<String?>(null) }
    var toastMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try { LocalPipeline.reEnqueuePending(context) } catch (_: Exception) {}
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        val audioGranted = perms[Manifest.permission.RECORD_AUDIO] == true
        val locGranted = perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (audioGranted && pendingStart) {
            pendingStart = false
            startAt = System.currentTimeMillis()
            if (recorder.start()) {
                isRecording = true
                val header = buildHeader(context, locGranted, startAt ?: System.currentTimeMillis())
                title = header + title
            }
        } else {
            pendingStart = false
            message = "需要录音权限"
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        val sid = currentSessionId ?: repo.basenameFromMillis(System.currentTimeMillis()).also { currentSessionId = it }
        if (bmp != null) {
            val bos = java.io.ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, bos)
            val name = "img-" + System.currentTimeMillis() + ".jpg"
            val uri = NoteRepository(context).saveSessionImage(sid, name, bos.toByteArray())
            GlobalScope.launch {
                AppDatabase.get(context).imageDao().insert(com.ai.recorder.data.ImageEntity(sessionId = sid, imageUri = uri.toString(), createdAt = java.time.OffsetDateTime.now().toString()))
                sessionRepo.createOrUpdateSession(sid, null, null, null, null, title, null)
            }
            title = insertImageIntoTitle(title, name)
        }
    }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { picked: Uri? ->
        val sid = currentSessionId ?: repo.basenameFromMillis(System.currentTimeMillis()).also { currentSessionId = it }
        if (picked != null) {
            val bytes = context.contentResolver.openInputStream(picked)?.use { it.readBytes() }
            if (bytes != null) {
                val name = "img-" + System.currentTimeMillis() + ".jpg"
                val uri = NoteRepository(context).saveSessionImage(sid, name, bytes)
                GlobalScope.launch {
                    AppDatabase.get(context).imageDao().insert(com.ai.recorder.data.ImageEntity(sessionId = sid, imageUri = uri.toString(), createdAt = java.time.OffsetDateTime.now().toString()))
                    sessionRepo.createOrUpdateSession(sid, null, null, null, null, title, null)
                }
                title = insertImageIntoTitle(title, name)
            }
        }
    }
    

    if (showStatus) {
        if (viewingSessionId == null) {
            HistoryScreen(onBack = { showStatus = false }, onOpen = { viewingSessionId = it.sessionId })
        } else {
            SessionDetailScreen(sessionId = viewingSessionId!!, onBack = { viewingSessionId = null })
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.White),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Top
        ) {
        Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val recordButtonContent: @Composable () -> Unit = { Text(if (isRecording) "■" else "●") }
                if (!isRecording) {
                    Button(
                        onClick = {
                        if (!isRecording) {
                        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        val locGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            startAt = System.currentTimeMillis()
                            if (recorder.start()) {
                                isRecording = true
                                val header = buildHeader(context, locGranted, startAt ?: System.currentTimeMillis())
                                title = header + title
                                val act = context as? ComponentActivity
                                act?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                val nm = context.getSystemService(NotificationManager::class.java)
                                if (nm?.isNotificationPolicyAccessGranted == true) {
                                    prevInterruptionFilter = nm.currentInterruptionFilter
                                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                                } else {
                                    act?.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                                }
                            }
                        } else {
                            pendingStart = true
                            launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    } else {
                        val file = recorder.stop()
                        isRecording = false
                        if (file != null) {
                            val base = repo.basenameFromMillis(startAt ?: System.currentTimeMillis())
                            pendingRecordedFile = file
                            pendingBasename = base
                            showSaveChoice = true
                        }
                        val act = context as? ComponentActivity
                        act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        val nm = context.getSystemService(NotificationManager::class.java)
                        prevInterruptionFilter?.let { nm?.setInterruptionFilter(it) }
                        prevInterruptionFilter = null
                    }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = purple,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.size(btnWidth, 32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { recordButtonContent() }
                } else {
                    OutlinedButton(
                        onClick = {
                            // fall through to stop logic below
                            if (!isRecording) {
                                // guarded; will not happen
                            } else {
                                val file = recorder.stop()
                                isRecording = false
                                if (file != null) {
                                    val base = repo.basenameFromMillis(startAt ?: System.currentTimeMillis())
                                    pendingRecordedFile = file
                                    pendingBasename = base
                                    showSaveChoice = true
                                }
                                val act = context as? ComponentActivity
                                act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                val nm = context.getSystemService(NotificationManager::class.java)
                                prevInterruptionFilter?.let { nm?.setInterruptionFilter(it) }
                                prevInterruptionFilter = null
                            }
                        },
                        border = BorderStroke(1.dp, Color.White),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                        modifier = Modifier.size(btnWidth, 32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { recordButtonContent() }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { showTagDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White),
                        modifier = Modifier.size(btnWidth, 32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("#") }
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (granted) takePictureLauncher.launch(null) else launcher.launch(arrayOf(Manifest.permission.CAMERA))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White),
                        modifier = Modifier.size(btnWidth, 32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("拍") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { pickImageLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White),
                        modifier = Modifier.size(btnWidth, 32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("图") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showStatus = true },
                        colors = ButtonDefaults.buttonColors(containerColor = purple, contentColor = Color.White),
                        modifier = Modifier.size(btnWidth, 32.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) { Text("▶") }
                }
            }
            toastMsg?.let { msg ->
                Text(text = msg, color = Color(0xFF00796B), modifier = Modifier.padding(horizontal = 12.dp))
            }
            TextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                singleLine = false,
                maxLines = Int.MAX_VALUE,
                textStyle = TextStyle(color = Color.Black, textAlign = TextAlign.Start),
                colors = androidx.compose.material3.TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent
                )
            )
            
            if (showSaveChoice) {
                AlertDialog(
                    onDismissRequest = { showSaveChoice = false },
                    title = { Text("请选择保存方式") },
                    text = { },
                    confirmButton = {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    TextButton(onClick = {
                                        val file = pendingRecordedFile
                                        val base = pendingBasename
                                    if (file != null && base != null) {
                                        val audioUri = repo.saveSessionAudio(base, file)
                                        val textUri = repo.saveSessionText(base, title)
                                        statusStore.logSaved(base, audio = true, text = true)
                                        GlobalScope.launch {
                                            sessionRepo.createOrUpdateSession(
                                                base,
                                                null,
                                                null,
                                                null,
                                                null,
                                                title,
                                                audioUri
                                            )
                                            LocalPipeline.enqueueTranscription(context, base, audioUri.toString())
                                        }
                                        TagStore(context).syncFromText(title)
                                        title = ""
                                    }
                                        pendingRecordedFile = null
                                        pendingBasename = null
                                        showSaveChoice = false
                                    }) { Text("音频+笔记") }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    TextButton(onClick = {
                                        val file = pendingRecordedFile
                                        val base = pendingBasename
                                    if (file != null) { try { file.delete() } catch (_: Exception) {} }
                                    if (base != null) {
                                        val textUri = repo.saveSessionText(base, title)
                                        statusStore.logSaved(base, audio = false, text = true)
                                        GlobalScope.launch {
                                            sessionRepo.createOrUpdateSession(
                                                base, null, null, null, null, title, null
                                            )
                                        }
                                        TagStore(context).syncFromText(title)
                                        title = ""
                                    }
                                        pendingRecordedFile = null
                                        pendingBasename = null
                                        showSaveChoice = false
                                    }) { Text("笔记") }
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    TextButton(onClick = {
                                        val file = pendingRecordedFile
                                        val base = pendingBasename
                                    if (file != null && base != null) {
                                        val audioUri = repo.saveSessionAudio(base, file)
                                        statusStore.logSaved(base, audio = true, text = false)
                                        GlobalScope.launch {
                                            sessionRepo.createOrUpdateSession(
                                                base, null, null, null, null, null, audioUri
                                            )
                                            LocalPipeline.enqueueTranscription(context, base, audioUri.toString())
                                        }
                                        title = ""
                                    }
                                        pendingRecordedFile = null
                                        pendingBasename = null
                                        showSaveChoice = false
                                    }) { Text("音频") }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    TextButton(onClick = {
                                        val file = pendingRecordedFile
                                        if (file != null) {
                                            try { file.delete() } catch (_: Exception) {}
                                        }
                                        title = ""
                                        pendingRecordedFile = null
                                        pendingBasename = null
                                        showSaveChoice = false
                                    }) { Text("都弃") }
                                }
                            }
                        }
                    },
                    dismissButton = { }
                )
            }
            if (showTagDialog) {
                TagManagerDialog(
                    onDismiss = { showTagDialog = false },
                    onInsert = { tag -> title = insertTagIntoTitle(title, tag) },
                    onDelete = { tag -> TagStore(context).remove(tag) },
                    onAdd = { tag -> TagStore(context).add(tag) }
                )
            }
        }
    }
}

private fun buildHeader(context: android.content.Context, locGranted: Boolean, startAt: Long): String {
    val ts = SimpleDateFormat("yyyy年MM月dd日 HH点mm分ss秒", Locale.CHINA).format(Date(startAt))
    val city = if (locGranted) getCity(context) else ""
    val cityLine = if (city.isNotEmpty()) "$city" else ""
    val sb = StringBuilder()
    sb.append(ts).append('\n')
    if (cityLine.isNotEmpty()) sb.append(cityLine).append('\n')
    sb.append('\n')
    sb.append("人员：").append('\n')
    sb.append("#：").append('\n')
    sb.append("图片：").append('\n')
    sb.append('\n')
    sb.append("笔记：").append('\n')
    return sb.toString()
}

private fun getCity(context: android.content.Context): String {
    return try {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        for (p in providers) {
            val loc = lm.getLastKnownLocation(p)
            if (loc != null) {
                val gc = Geocoder(context, Locale.ENGLISH)
                val addrs = gc.getFromLocation(loc.latitude, loc.longitude, 1)
                val a = addrs?.firstOrNull()
                val locality = a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: ""
                if (locality.isNotEmpty()) return locality
            }
        }
        ""
    } catch (e: Exception) { "" }
}

@Composable
private fun TagManagerDialog(onDismiss: () -> Unit, onInsert: (String) -> Unit, onDelete: (String) -> Unit, onAdd: (String) -> Unit) {
    val context = LocalContext.current
    var newTag by remember { mutableStateOf("") }
    val tags = remember { mutableStateListOf<String>() }
    val inserted = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) {
        tags.clear()
        tags.addAll(TagStore(context).list())
    }
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text("管理#") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = newTag, onValueChange = { newTag = it }, label = { Text("新增#标签") })
                Spacer(Modifier.height(8.dp))
                Row {
                    TextButton(onClick = {
                        val t = newTag.trim()
                        if (t.isNotEmpty()) {
                            onAdd(t)
                            // 将新标签置顶
                            tags.remove(t)
                            tags.add(0, t)
                            newTag = ""
                        }
                    }) { Text("添加") }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(tags.size) { i ->
                        val t = tags[i]
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = t, modifier = Modifier.weight(1f))
                            TextButton(enabled = !inserted.contains(t), onClick = {
                                onInsert(t)
                                TagStore(context).markUsed(t)
                                inserted.add(t)
                                // 置顶显示最近使用
                                tags.remove(t)
                                tags.add(0, t)
                            }) { Text("插入") }
                            TextButton(onClick = { onDelete(t); tags.removeAt(i) }) { Text("删除") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss() }) { Text("完成") } },
        dismissButton = { }
    )
}

private fun insertTagIntoTitle(current: String, tag: String): String {
    val hash = if (tag.startsWith("#")) tag else "#$tag"
    val lines = current.split('\n').toMutableList()
    var idx = lines.indexOfFirst { it.startsWith("#：") }
    if (idx < 0) {
        lines.add("#：")
        idx = lines.lastIndex
    }
    val line = lines[idx]
    val content = line.removePrefix("#：").trim()
    val sep = if (content.isEmpty()) " " else " "
    lines[idx] = "#：" + content + sep + hash
    return lines.joinToString("\n")
}

private fun insertImageIntoTitle(current: String, fileName: String): String {
    val lines = current.split('\n').toMutableList()
    var idx = lines.indexOfFirst { it.startsWith("图片：") }
    if (idx < 0) {
        val anchor = lines.indexOfFirst { it.startsWith("#：") }
        val insertAt = if (anchor >= 0) anchor + 1 else 0
        lines.add(insertAt, "图片：")
        idx = insertAt
    }
    val line = lines[idx]
    val content = line.removePrefix("图片：").trim()
    val sep = if (content.isEmpty()) " " else " "
    lines[idx] = "图片：" + content + sep + fileName
    return lines.joinToString("\n")
}
