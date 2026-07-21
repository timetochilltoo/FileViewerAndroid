package com.timetochilltoo.fileviewer.core.files

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.recentsDataStore by preferencesDataStore(name = "recents")

class RecentsStore(private val context: Context) {

    private val key = stringPreferencesKey("recents_json")

    val recents: Flow<List<RecentDocument>> = context.recentsDataStore.data.map { prefs ->
        parse(prefs[key])
    }

    suspend fun add(uri: String, displayName: String) {
        val current = context.recentsDataStore.data.first()[key]
        val updated = (listOf(RecentDocument(uri, displayName, System.currentTimeMillis())) + parse(current))
            .distinctBy { it.uri }
            .take(MAX_RECENTS)
        context.recentsDataStore.edit { it[key] = encode(updated) }
    }

    private fun parse(json: String?): List<RecentDocument> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                RecentDocument(
                    uri = obj.optString("uri"),
                    displayName = obj.optString("name"),
                    lastOpenedEpochMs = obj.optLong("ts"),
                )
            }.filter { it.uri.isNotBlank() }
        }.getOrDefault(emptyList())
    }

    private fun encode(recents: List<RecentDocument>): String {
        val array = JSONArray()
        recents.forEach { recent ->
            array.put(
                JSONObject()
                    .put("uri", recent.uri)
                    .put("name", recent.displayName)
                    .put("ts", recent.lastOpenedEpochMs),
            )
        }
        return array.toString()
    }

    companion object {
        const val MAX_RECENTS = 20
    }
}
