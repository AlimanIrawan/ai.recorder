package com.ai.recorder

import android.content.Context
import android.content.SharedPreferences

data class SavedEntry(val base: String, val audio: Boolean, val text: Boolean, val time: Long)

class StatusStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("status_store", Context.MODE_PRIVATE)

    fun logSaved(base: String, audio: Boolean, text: Boolean) {
        val list = list().toMutableList()
        val idx = list.indexOfFirst { it.base == base }
        val now = System.currentTimeMillis()
        val entry = if (idx >= 0) {
            val old = list[idx]
            SavedEntry(base, old.audio || audio, old.text || text, now)
        } else SavedEntry(base, audio, text, now)
        if (idx >= 0) list[idx] = entry else list.add(entry)
        val ser = serialize(list)
        prefs.edit().putString("entries", ser).apply()
    }

    fun list(): List<SavedEntry> {
        val ser = prefs.getString("entries", null) ?: return emptyList()
        return deserialize(ser)
    }

    private fun serialize(list: List<SavedEntry>): String {
        val sb = StringBuilder()
        for (e in list) {
            sb.append(e.base).append('|').append(if (e.audio) '1' else '0').append('|').append(if (e.text) '1' else '0').append('|').append(e.time).append('\n')
        }
        return sb.toString()
    }

    private fun deserialize(s: String): List<SavedEntry> {
        val out = mutableListOf<SavedEntry>()
        for (line in s.split('\n')) {
            if (line.isBlank()) continue
            val parts = line.split('|')
            if (parts.size >= 4) {
                val base = parts[0]
                val audio = parts[1] == "1"
                val text = parts[2] == "1"
                val time = parts[3].toLongOrNull() ?: 0L
                out.add(SavedEntry(base, audio, text, time))
            }
        }
        return out
    }
}

