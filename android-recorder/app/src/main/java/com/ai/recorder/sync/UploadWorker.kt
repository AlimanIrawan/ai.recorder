package com.ai.recorder.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.ai.recorder.BuildConfig

class UploadWorker(appContext: Context, params: WorkerParameters) : Worker(appContext, params) {
    override fun doWork(): Result {
        val folderId = BuildConfig.DRIVE_FOLDER_ID
        if (folderId.isEmpty()) return Result.failure()
        val name = inputData.getString("name") ?: return Result.failure()
        val mime = inputData.getString("mime") ?: return Result.failure()
        val uriStr = inputData.getString("uri") ?: return Result.failure()
        val uri = Uri.parse(uriStr)
        Log.d("UploadWorker", "start name=$name mime=$mime uri=$uriStr")
        setProgressAsync(androidx.work.Data.Builder().putString("name", name).putString("mime", mime).putString("uri", uriStr).build())
        val backendUrl = BuildConfig.BACKEND_UPLOAD_URL
        if (backendUrl.isEmpty()) {
            Log.e("UploadWorker", "missing BACKEND_UPLOAD_URL for OAuth upload")
            return Result.failure()
        }
        val resp = try {
            BackendUploader().upload(backendUrl, folderId, name, mime, applicationContext.contentResolver, uri)
        } catch (e: Exception) {
            Log.e("UploadWorker", "backend upload error: ${e.message}")
            DriveUploader.UploadResponse(false, -1, e.message)
        }
        setProgressAsync(androidx.work.Data.Builder().putInt("code", resp.code).build())
        val out = androidx.work.Data.Builder().putString("name", name).putString("mime", mime).putInt("code", resp.code).putString("body", resp.body ?: "").build()
        Log.d("UploadWorker", "done name=$name success=${resp.ok}")
        return if (resp.ok) Result.success(out) else Result.retry()
    }
}
