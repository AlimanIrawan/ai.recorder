package com.ai.recorder.pipeline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import android.util.Log
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.concurrent.TimeUnit
import com.ai.recorder.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalPipeline {
    fun enqueueTranscription(context: Context, sessionId: String, audioUri: String) {
        val wm = WorkManager.getInstance(context)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val data = Data.Builder().putString("sessionId", sessionId).putString("audioUri", audioUri).build()
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val nc = cm?.getNetworkCapabilities(cm.activeNetwork)
        val connected = nc?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        Log.i("LocalPipeline", "enqueue sid=$sessionId connected=$connected uri=$audioUri")
        val req = OneTimeWorkRequestBuilder<TranscribeWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setInputData(data)
            .addTag("local_transcribe")
            .build()
        wm.beginUniqueWork("transcribe_global_queue", androidx.work.ExistingWorkPolicy.APPEND, req).enqueue()
    }

    fun enqueueSummarize(context: Context, sessionId: String) {
        
    }

    suspend fun reEnqueuePending(context: Context) {
        val db = AppDatabase.get(context)
        val pending = withContext(Dispatchers.IO) { db.sessionDao().listPendingForTranscribe() }
        pending.forEach { s ->
            val uri = s.audioUri ?: return@forEach
            enqueueTranscription(context, s.sessionId, uri)
        }
    }
}
