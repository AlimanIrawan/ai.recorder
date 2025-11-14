package com.ai.recorder.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    fun enqueue(context: Context, basename: String, audioUri: Uri, textUri: Uri) {
        val wm = WorkManager.getInstance(context)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val audioData = Data.Builder().putString("name", "$basename.m4a").putString("mime", "audio/mp4").putString("uri", audioUri.toString()).build()
        val textData = Data.Builder().putString("name", "$basename.txt").putString("mime", "text/plain").putString("uri", textUri.toString()).build()
        val audioReq = OneTimeWorkRequestBuilder<UploadWorker>().setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).setInputData(audioData).addTag("upload_audio").build()
        val textReq = OneTimeWorkRequestBuilder<UploadWorker>().setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).setInputData(textData).addTag("upload_text").build()
        wm.enqueue(audioReq)
        wm.enqueue(textReq)
        Log.d("SyncScheduler", "enqueued basename=$basename audio=$audioUri text=$textUri")
    }
}
