package com.ai.recorder.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.ai.recorder.data.AppDatabase
import com.ai.recorder.data.ImageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object Exporter {
    suspend fun exportSessionZip(context: Context, sessionId: String): Uri = withContext(Dispatchers.IO) {
        val db = AppDatabase.get(context)
        val s = db.sessionDao().bySessionId(sessionId) ?: throw IllegalStateException("no session")
        val images: List<ImageEntity> = db.imageDao().listBySession(sessionId).first()
        val displayName = "$sessionId.zip"
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/airecording/$sessionId/")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: throw IllegalStateException("insert zip failed")
        val os: OutputStream = context.contentResolver.openOutputStream(uri) ?: throw IllegalStateException("open zip failed")
        val zos = ZipOutputStream(os)
        try {
            if (!s.note.isNullOrBlank()) {
                zos.putNextEntry(ZipEntry("note.txt"))
                zos.write(s.note.toByteArray())
                zos.closeEntry()
            }
            if (!s.transcript.isNullOrBlank()) {
                zos.putNextEntry(ZipEntry("transcript.txt"))
                zos.write(s.transcript.toByteArray())
                zos.closeEntry()
            }
            if (!s.summary.isNullOrBlank()) {
                zos.putNextEntry(ZipEntry("summary.txt"))
                zos.write(s.summary.toByteArray())
                zos.closeEntry()
            }
            val meta = JSONObject(mapOf(
                "sessionId" to s.sessionId,
                "time" to s.time,
                "location" to s.location,
                "people" to s.people,
                "hashtags" to s.hashtags,
                "audioUri" to s.audioUri
            )).toString(2)
            zos.putNextEntry(ZipEntry("metadata.json"))
            zos.write(meta.toByteArray())
            zos.closeEntry()
            s.audioUri?.let { auri ->
                try {
                    context.contentResolver.openInputStream(Uri.parse(auri))?.use { ins ->
                        zos.putNextEntry(ZipEntry("audio.m4a"))
                        ins.copyTo(zos)
                        zos.closeEntry()
                    }
                } catch (_: Exception) {}
            }
            images.forEachIndexed { idx: Int, img: ImageEntity ->
                try {
                    context.contentResolver.openInputStream(Uri.parse(img.imageUri))?.use { ins ->
                        zos.putNextEntry(ZipEntry("img-$idx.jpg"))
                        ins.copyTo(zos)
                        zos.closeEntry()
                    }
                } catch (_: Exception) {}
            }
        } finally {
            zos.close()
            os.close()
        }
        uri
    }
}
