package com.ai.recorder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class FileState { saving, saved }
enum class AudioState { none, transcribing, done }
enum class SummaryState { none, waiting_network, done }

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val time: String?,
    val location: String?,
    val people: List<String>?,
    val hashtags: List<String>?,
    val note: String?,
    val transcript: String?,
    val summary: String?,
    val summaryError: String?,
    val title: String?,
    val audioUri: String?,
    val transcribeSource: String?,
    val transcribeError: String?,
    val fileState: FileState,
    val audioState: AudioState,
    val summaryState: SummaryState,
    val createdAt: String,
    val updatedAt: String
)

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val imageUri: String,
    val createdAt: String
)
