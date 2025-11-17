package com.ai.recorder

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteRepository(private val context: Context) {
    private val unifiedRelativePath = "Download/airecording/"

    fun basenameFromMillis(ms: Long): String {
        val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        return fmt.format(Date(ms))
    }

    fun saveSessionAudio(sessionId: String, audioFile: File): Uri {
        return insertAndWrite(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "audio/mp4",
            "audio.m4a",
            audioFile.readBytes(),
            unifiedRelativePath + "$sessionId/"
        )
    }

    fun saveSessionText(sessionId: String, text: String): Uri {
        return insertAndWrite(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "text/plain",
            "note.txt",
            text.toByteArray(),
            unifiedRelativePath + "$sessionId/"
        )
    }

    fun saveSessionImage(sessionId: String, displayName: String, bytes: ByteArray): Uri {
        return insertAndWrite(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "image/jpeg",
            displayName,
            bytes,
            unifiedRelativePath + "$sessionId/"
        )
    }

    private fun chineseDisplayFromBase(basename: String): String {
        return try {
            val src = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
            val d = src.parse(basename) ?: Date()
            SimpleDateFormat("yyyy年MM月dd日 HH点mm分ss秒", Locale.CHINA).format(d)
        } catch (_: Exception) {
            basename
        }
    }

    fun saveAudioAndText(basename: String, audioFile: File, text: String): Pair<Uri, Uri> {
        val disp = chineseDisplayFromBase(basename)
        val audioUri = insertAndWrite(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "audio/mp4",
            "$disp.m4a",
            audioFile.readBytes(),
            unifiedRelativePath
        )

        val textUri = insertAndWrite(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "text/plain",
            "$disp.txt",
            text.toByteArray(),
            unifiedRelativePath
        )

        return audioUri to textUri
    }

    fun saveAudioOnly(basename: String, audioFile: File): Uri {
        val disp = chineseDisplayFromBase(basename)
        return insertAndWrite(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "audio/mp4",
            "$disp.m4a",
            audioFile.readBytes(),
            unifiedRelativePath
        )
    }

    fun saveTextOnly(basename: String, text: String): Uri {
        val disp = chineseDisplayFromBase(basename)
        return insertAndWrite(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "text/plain",
            "$disp.txt",
            text.toByteArray(),
            unifiedRelativePath
        )
    }

    private fun insertAndWrite(collection: Uri, mime: String, displayName: String, data: ByteArray, relativePath: String): Uri {
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(collection, cv) ?: throw IllegalStateException("Insert failed")
        resolver.openOutputStream(uri)?.use { it.write(data) }
        return uri
    }
}
