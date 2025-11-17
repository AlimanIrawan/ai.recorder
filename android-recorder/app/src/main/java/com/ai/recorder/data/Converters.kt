package com.ai.recorder.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun listToString(value: List<String>?): String? = value?.joinToString("\u0001")

    @TypeConverter
    fun stringToList(value: String?): List<String>? = value?.split("\u0001")?.filter { it.isNotEmpty() }

    @TypeConverter
    fun fileStateToString(v: FileState?): String? = v?.name

    @TypeConverter
    fun stringToFileState(s: String?): FileState? = s?.let { FileState.valueOf(it) }

    @TypeConverter
    fun audioStateToString(v: AudioState?): String? = v?.name

    @TypeConverter
    fun stringToAudioState(s: String?): AudioState? = s?.let { AudioState.valueOf(it) }

    @TypeConverter
    fun summaryStateToString(v: SummaryState?): String? = v?.name

    @TypeConverter
    fun stringToSummaryState(s: String?): SummaryState? = s?.let { SummaryState.valueOf(it) }
}

