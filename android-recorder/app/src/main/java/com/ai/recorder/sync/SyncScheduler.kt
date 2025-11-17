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
        val disp = chineseName(basename)
        val audioData = Data.Builder().putString("name", "$disp.m4a").putString("mime", "audio/mp4").putString("uri", audioUri.toString()).putString("base", basename).build()
        val textData = Data.Builder().putString("name", "$disp.txt").putString("mime", "text/plain").putString("uri", textUri.toString()).putString("base", basename).build()
        val audioReq = OneTimeWorkRequestBuilder<UploadWorker>().setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).setInputData(audioData).addTag("upload_audio").build()
        val textReq = OneTimeWorkRequestBuilder<UploadWorker>().setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).setInputData(textData).addTag("upload_text").build()
        wm.enqueue(audioReq)
        wm.enqueue(textReq)
        Log.d("SyncScheduler", "enqueued basename=$basename audio=$audioUri text=$textUri")
    }

    fun enqueueAudioOnly(context: Context, basename: String, audioUri: Uri) {
        val wm = WorkManager.getInstance(context)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val disp = chineseName(basename)
        val audioData = Data.Builder().putString("name", "$disp.m4a").putString("mime", "audio/mp4").putString("uri", audioUri.toString()).putString("base", basename).build()
        val audioReq = OneTimeWorkRequestBuilder<UploadWorker>().setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).setInputData(audioData).addTag("upload_audio").build()
        wm.enqueue(audioReq)
        Log.d("SyncScheduler", "enqueued audio-only basename=$basename audio=$audioUri")
    }

    fun enqueueTextOnly(context: Context, basename: String, textUri: Uri) {
        val wm = WorkManager.getInstance(context)
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val disp = chineseName(basename)
        val textData = Data.Builder().putString("name", "$disp.txt").putString("mime", "text/plain").putString("uri", textUri.toString()).putString("base", basename).build()
        val textReq = OneTimeWorkRequestBuilder<UploadWorker>().setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS).setInputData(textData).addTag("upload_text").build()
        wm.enqueue(textReq)
        Log.d("SyncScheduler", "enqueued text-only basename=$basename text=$textUri")
    }

    private fun chineseName(basename: String): String {
        return try {
            val src = java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.getDefault())
            val d = src.parse(basename) ?: java.util.Date()
            java.text.SimpleDateFormat("yyyy年MM月dd日 HH点mm分ss秒", java.util.Locale.CHINA).format(d)
        } catch (_: Exception) {
            basename
        }
    }
}
