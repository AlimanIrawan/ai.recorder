package com.ai.recorder.pipeline

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import androidx.core.app.NotificationCompat
import android.content.pm.ServiceInfo
import com.ai.recorder.BuildConfig
import com.ai.recorder.data.AppDatabase
import com.ai.recorder.data.AudioState
import com.ai.recorder.data.SummaryState
 
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit

class TranscribeWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(3600, TimeUnit.SECONDS)
        .writeTimeout(600, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    override fun doWork(): Result {
        val sessionId = inputData.getString("sessionId") ?: return Result.failure()
        val audioUri = inputData.getString("audioUri") ?: return Result.failure()
        val db = AppDatabase.get(applicationContext)
        val now = java.time.OffsetDateTime.now().toString()
        setForegroundAsync(getForegroundInfo())
        try {
            Log.i("TranscribeWorker", "start sid=$sessionId uri=$audioUri")
            kotlinx.coroutines.runBlocking { db.sessionDao().updateTranscript(sessionId, null, null, AudioState.transcribing.name, now) }
            val combined = runBackendTranscribeAndSummarize(audioUri)
            kotlinx.coroutines.runBlocking { db.sessionDao().updateTranscript(sessionId, combined.text, combined.source, AudioState.done.name, now) }
            kotlinx.coroutines.runBlocking { db.sessionDao().updateSummaryWithTitle(sessionId, combined.title, combined.summary, SummaryState.done.name, now) }
            Log.i("TranscribeWorker", "success sid=$sessionId src=${'$'}{combined.source} len=${'$'}{combined.text.length}")
            return Result.success()
        } catch (e: Exception) {
            Log.e("TranscribeWorker", "error sid=$sessionId ${e.message}", e)
            val emsg = when {
                e is FileNotFoundException -> "audio_not_found"
                e.message?.contains("audio_not_found") == true -> "audio_not_found"
                e.message?.contains("backend_http") == true -> e.message ?: "backend_error"
                e.message?.contains("backend_empty_output") == true -> e.message ?: "backend_empty_output"
                else -> e.message ?: "transcribe failed"
            }
            kotlinx.coroutines.runBlocking { db.sessionDao().updateTranscriptError(sessionId, emsg, "error", AudioState.none.name, now) }
            return Result.failure()
        }
    }

    override fun getForegroundInfo(): ForegroundInfo {
        val notification = NotificationHelper.build(applicationContext, "转录中", "正在处理音频…")
        return ForegroundInfo(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private data class TrOut(val text: String, val source: String)
    private data class CombinedOut(val text: String, val title: String?, val summary: String?, val source: String)

    private fun runBackendTranscribeAndSummarize(audioUri: String): CombinedOut {
        val base0 = BuildConfig.BACKEND_TRANSCRIBE_URL
        if (base0.isEmpty()) throw RuntimeException("backend_url_missing")
        val base = base0.trimEnd('/')
        val candidates = listOf(
            "$base/api/transcribe-and-summarize",
            "$base/transcribe-and-summarize"
        )
        val uri = Uri.parse(audioUri)
        val tmp = File.createTempFile("ts_", ".m4a", applicationContext.cacheDir)
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        } ?: throw IllegalStateException("no audio input")
        val fileBody = tmp.asRequestBody("audio/mp4".toMediaType())
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.m4a", fileBody)
            .build()
        var lastTxt = ""
        for (u in candidates) {
            val req = Request.Builder().url(u).post(form).build()
            client.newCall(req).execute().use { resp ->
                val txt = resp.body?.string() ?: ""
                lastTxt = txt
                if (resp.code == 404) {
                    // try next candidate
                } else {
                if (!resp.isSuccessful) throw RuntimeException("backend_http_${resp.code}: $txt")
                val json = try { JSONObject(txt) } catch (_: Exception) { null }
                val jobId = json?.optString("jobId")
                if (!jobId.isNullOrBlank()) {
                    val jpaths = listOf("$base/api/jobs/$jobId", "$base/jobs/$jobId")
                    var loops = 0
                    while (loops < 3600) {
                        for (jp in jpaths) {
                            val jreq = Request.Builder().url(jp).get().build()
                            client.newCall(jreq).execute().use { jresp ->
                                val jtxt = jresp.body?.string() ?: ""
                                if (jresp.isSuccessful) {
                                    val j = try { JSONObject(jtxt) } catch (_: Exception) { null }
                                    val st = j?.optString("status") ?: ""
                                    if (st == "done") {
                                        val text = j.optString("text")
                                        val title = j.optString("title")
                                        val summary = j.optString("summary")
                                        if (text.isBlank()) throw RuntimeException("backend_empty_output")
                                        return CombinedOut(text, title, summary, "remote:openai")
                                    } else if (st == "error") {
                                        val err = j.optString("error")
                                        throw RuntimeException("backend_http_500: $err")
                                    }
                                }
                            }
                        }
                        Thread.sleep(3000)
                        loops++
                    }
                    throw RuntimeException("backend_http_408: poll timeout")
                } else {
                    val text = json?.optString("text") ?: json?.optString("transcript") ?: txt
                    val title = json?.optString("title")
                    val summary = json?.optString("summary")
                    if (text.isBlank()) throw RuntimeException("backend_empty_output")
                    return CombinedOut(text, title, summary, "remote:openai")
                }
                }
            }
        }
        throw RuntimeException("backend_http_404: $lastTxt")
    }

    

    private fun runRemoteTranscription(backend: String, audioUri: String): String {
        val uri = Uri.parse(audioUri)
        val tmp = File.createTempFile("ts_", ".m4a", applicationContext.cacheDir)
        applicationContext.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        } ?: throw IllegalStateException("no audio input")
        val body = tmp.asRequestBody("audio/mp4".toMediaType())
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.m4a", body)
            .build()
        val req = Request.Builder().url(backend).post(form).build()
        client.newCall(req).execute().use { resp ->
            val txt = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw RuntimeException("remote_http_${resp.code}: $txt")
            val json = try { JSONObject(txt) } catch (_: Exception) { null }
            val text = json?.optString("text") ?: json?.optString("transcript") ?: txt
            return text
        }
    }

    
}
