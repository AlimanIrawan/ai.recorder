package com.ai.recorder.sync

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class DriveUploader(private val tokenProvider: () -> String, private val client: OkHttpClient = OkHttpClient()) {
    data class UploadResponse(val ok: Boolean, val code: Int, val body: String?)
    fun uploadMultipart(folderId: String, name: String, mime: String, resolver: ContentResolver, uri: Uri): UploadResponse {
        Log.d("DriveUploader", "upload start name=$name mime=$mime")
        val boundary = "boundary_${UUID.randomUUID()}"
        val metaJson = ("{" + "\"name\":\"$name\",\"parents\":[\"$folderId\"],\"mimeType\":\"$mime\"}").toByteArray()
        val metaPart = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray()
        val fileHeader = "\r\n--$boundary\r\nContent-Type: $mime\r\n\r\n".toByteArray()
        val fileTail = "\r\n--$boundary--\r\n".toByteArray()
        val fileBytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return UploadResponse(false, -1, "no input stream")
        val out = java.io.ByteArrayOutputStream()
        out.write(metaPart)
        out.write(metaJson)
        out.write(fileHeader)
        out.write(fileBytes)
        out.write(fileTail)
        val bodyBytes = out.toByteArray()
        val body = bodyBytes.toRequestBody("multipart/related; boundary=$boundary".toMediaType())
        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
            .addHeader("Authorization", "Bearer ${tokenProvider()}")
            .post(body)
            .build()
        val resp = client.newCall(req).execute()
        val bodyStr = resp.body?.string()
        Log.d("DriveUploader", "upload resp code=${resp.code} body=${bodyStr}")
        return UploadResponse(resp.isSuccessful, resp.code, bodyStr)
    }
}
