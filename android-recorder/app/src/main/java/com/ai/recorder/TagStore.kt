package com.ai.recorder

import android.content.Context
import android.content.SharedPreferences

class TagStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("tags_store", Context.MODE_PRIVATE)

    private fun load(): MutableMap<String, Long> {
        val s = prefs.getString("tags", null) ?: return mutableMapOf()
        val map = mutableMapOf<String, Long>()
        for (line in s.split('\n')) {
            if (line.isBlank()) continue
            val parts = line.split('|')
            val tag = parts[0]
            val ts = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            if (tag.isNotBlank()) map[tag] = ts
        }
        return map
    }

    private fun save(map: Map<String, Long>) {
        val sorted = map.entries.sortedByDescending { it.value }
        val ser = sorted.joinToString("\n") { it.key + "|" + it.value }
        prefs.edit().putString("tags", ser).apply()
    }

    fun list(): List<String> {
        val map = load()
        return map.entries.sortedByDescending { it.value }.map { it.key }
    }

    fun add(tag: String) {
        val t = tag.trim()
        if (t.isEmpty()) return
        val map = load()
        map[t] = System.currentTimeMillis()
        save(map)
    }

    fun markUsed(tag: String) {
        val t = tag.trim()
        if (t.isEmpty()) return
        val map = load()
        map[t] = System.currentTimeMillis()
        save(map)
    }

    fun remove(tag: String) {
        val map = load()
        map.remove(tag)
        save(map)
    }

    fun syncFromText(text: String) {
        val re = Regex("(?<!\\w)#([\u4e00-\u9fa5A-Za-z0-9_]+)")
        val found = re.findAll(text).map { it.groupValues[1] }.toSet()
        if (found.isNotEmpty()) {
            val map = load()
            val now = System.currentTimeMillis()
            for (f in found) map[f] = now
            save(map)
        }
    }
}
