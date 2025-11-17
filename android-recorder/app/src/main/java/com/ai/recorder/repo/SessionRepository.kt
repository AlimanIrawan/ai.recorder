package com.ai.recorder.repo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.ai.recorder.data.AppDatabase
import com.ai.recorder.data.AudioState
import com.ai.recorder.data.FileState
import com.ai.recorder.data.SessionEntity
import com.ai.recorder.data.SummaryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionRepository(private val context: Context) {
    private val db = AppDatabase.get(context)

    fun newSessionId(ts: Long = System.currentTimeMillis()): String {
        val fmt = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())
        return fmt.format(Date(ts))
    }

    suspend fun createOrUpdateSession(
        sessionId: String,
        timeIso: String?,
        location: String?,
        people: List<String>?,
        hashtags: List<String>?,
        note: String?,
        audioUri: Uri?
    ) = withContext(Dispatchers.IO) {
        val now = isoNow()
        val dao = db.sessionDao()
        val exist = dao.bySessionId(sessionId)
        val entity = SessionEntity(
            id = exist?.id ?: 0,
            sessionId = sessionId,
            time = timeIso,
            location = location,
            people = people,
            hashtags = hashtags,
            note = note,
            transcript = exist?.transcript,
            summary = exist?.summary,
            summaryError = exist?.summaryError,
            title = exist?.title,
            audioUri = audioUri?.toString() ?: exist?.audioUri,
            transcribeSource = exist?.transcribeSource,
            transcribeError = exist?.transcribeError,
            fileState = FileState.saved,
            audioState = exist?.audioState ?: if (audioUri != null) AudioState.transcribing else AudioState.none,
            summaryState = exist?.summaryState ?: SummaryState.none,
            createdAt = exist?.createdAt ?: now,
            updatedAt = now
        )
        if (exist == null) dao.insert(entity) else dao.update(entity)
        entity
    }

    suspend fun saveTextToSessionFolder(sessionId: String, text: String): Uri = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "note.txt")
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/airecording/$sessionId/")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            ?: throw IllegalStateException("Insert note.txt failed")
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(text.toByteArray())
        }
        uri
    }

    suspend fun saveAudioToSessionFolder(sessionId: String, bytes: ByteArray): Uri = withContext(Dispatchers.IO) {
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "audio.m4a")
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/airecording/$sessionId/")
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
            ?: throw IllegalStateException("Insert audio.m4a failed")
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
        uri
    }

    private fun isoNow(): String = java.time.OffsetDateTime.now().toString()
}
