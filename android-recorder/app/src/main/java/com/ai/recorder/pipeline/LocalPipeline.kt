package com.ai.recorder.pipeline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object LocalPipeline {
    fun enqueueTranscription(context: Context, sessionId: String, audioUri: String) {
        val wm = WorkManager.getInstance(context)
        val constraints = Constraints.Builder().build()
        val data = Data.Builder().putString("sessionId", sessionId).putString("audioUri", audioUri).build()
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
}
