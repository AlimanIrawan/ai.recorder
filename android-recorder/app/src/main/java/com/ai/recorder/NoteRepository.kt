package com.ai.recorder

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
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

    fun saveAudioAndText(basename: String, audioFile: File, text: String): Pair<Uri, Uri> {
        val audioUri = insertAndWrite(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "audio/mp4",
            "$basename.m4a",
            audioFile.readBytes(),
            unifiedRelativePath
        )

        val textUri = insertAndWrite(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "text/plain",
            "$basename.txt",
            text.toByteArray(),
            unifiedRelativePath
        )

        return audioUri to textUri
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
