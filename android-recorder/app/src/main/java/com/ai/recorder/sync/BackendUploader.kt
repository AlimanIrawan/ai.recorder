package com.ai.recorder.sync

import android.content.ContentResolver
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class BackendUploader(private val client: OkHttpClient = OkHttpClient()) {
    fun upload(url: String, folderId: String, name: String, mime: String, resolver: ContentResolver, uri: Uri): DriveUploader.UploadResponse {
        val tmp = File.createTempFile("upload_", null)
        resolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } } ?: return DriveUploader.UploadResponse(false, -1, "no input stream")
        val fileBody = tmp.asRequestBody(mime.toMediaType())
        val form = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("folderId", folderId)
            .addFormDataPart("name", name)
            .addFormDataPart("mime", mime)
            .addFormDataPart("file", name, fileBody)
            .build()
        val req = Request.Builder().url(url).post(form).build()
        val resp = client.newCall(req).execute()
        val bodyStr = resp.body?.string()
        return DriveUploader.UploadResponse(resp.isSuccessful, resp.code, bodyStr)
    }
}
