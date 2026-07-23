package com.timetochilltoo.fileviewer.core.files

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.timetochilltoo.fileviewer.core.model.DocumentKind
import com.timetochilltoo.fileviewer.core.model.PdfScaleMode
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

object SessionCodec {

    data class Entry(val uri: String, val kind: DocumentKind)

    data class Session(val tabs: List<Entry>, val selectedUri: String?)

    data class PdfState(val page: Int, val scale: Float, val mode: PdfScaleMode)

    fun encode(session: Session): String {
        val root = JSONObject()
        val tabs = JSONArray()
        session.tabs.forEach { entry ->
            tabs.put(
                JSONObject()
                    .put("uri", entry.uri)
                    .put("kind", entry.kind.name),
            )
        }
        root.put("tabs", tabs)
        root.put("selected", session.selectedUri ?: JSONObject.NULL)
        return root.toString()
    }

    fun decode(json: String?): Session {
        if (json.isNullOrBlank()) return Session(emptyList(), null)
        return runCatching {
            val root = JSONObject(json)
            val tabsJson = root.optJSONArray("tabs") ?: JSONArray()
            val tabs = (0 until tabsJson.length()).mapNotNull { i ->
                val obj = tabsJson.optJSONObject(i) ?: return@mapNotNull null
                val uri = obj.optString("uri")
                val kind = runCatching {
                    DocumentKind.valueOf(obj.optString("kind"))
                }.getOrNull()
                if (uri.isBlank() || kind == null) null else Entry(uri, kind)
            }
            val selected = root.optString("selected")
                .takeIf { it.isNotBlank() && it != "null" }
            Session(tabs, selected)
        }.getOrDefault(Session(emptyList(), null))
    }

    fun encodeScroll(map: Map<String, Int>): String {
        val root = JSONObject()
        map.forEach { (uri, scrollY) -> root.put(uri, scrollY) }
        return root.toString()
    }

    fun decodeScroll(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            root.keys().asSequence().associateWith { root.getInt(it) }
        }.getOrDefault(emptyMap())
    }

    fun encodePdfState(map: Map<String, PdfState>): String {
        val root = JSONObject()
        map.forEach { (uri, state) ->
            root.put(
                uri,
                JSONObject()
                    .put("page", state.page)
                    .put("scale", state.scale.toDouble())
                    .put("mode", state.mode.name),
            )
        }
        return root.toString()
    }

    fun decodePdfState(json: String?): Map<String, PdfState> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            root.keys().asSequence().mapNotNull { uri ->
                val obj = root.optJSONObject(uri) ?: return@mapNotNull null
                val mode = runCatching {
                    PdfScaleMode.valueOf(obj.optString("mode", PdfScaleMode.FIT_WIDTH.name))
                }.getOrDefault(PdfScaleMode.FIT_WIDTH)
                uri to PdfState(
                    page = obj.optInt("page", 0).coerceAtLeast(0),
                    scale = obj.optDouble("scale", 1.0).toFloat().coerceAtLeast(0.1f),
                    mode = mode,
                )
            }.toMap()
        }.getOrDefault(emptyMap())
    }
}

private val Context.sessionDataStore by preferencesDataStore(name = "session")

class SessionStore(private val context: Context) {

    private val sessionKey = stringPreferencesKey("session_json")
    private val scrollKey = stringPreferencesKey("scroll_json")
    private val pdfStateKey = stringPreferencesKey("pdf_state_json")

    suspend fun load(): SessionCodec.Session =
        SessionCodec.decode(context.sessionDataStore.data.first()[sessionKey])

    suspend fun save(session: SessionCodec.Session) {
        context.sessionDataStore.edit { it[sessionKey] = SessionCodec.encode(session) }
    }

    suspend fun loadScroll(): Map<String, Int> =
        SessionCodec.decodeScroll(context.sessionDataStore.data.first()[scrollKey])

    suspend fun saveScrollMerged(positions: Map<String, Int>) {
        if (positions.isEmpty()) return
        context.sessionDataStore.edit { prefs ->
            val merged = SessionCodec.decodeScroll(prefs[scrollKey]).toMutableMap()
            merged.putAll(positions)
            prefs[scrollKey] = SessionCodec.encodeScroll(merged)
        }
    }

    suspend fun loadPdfState(): Map<String, SessionCodec.PdfState> =
        SessionCodec.decodePdfState(context.sessionDataStore.data.first()[pdfStateKey])

    suspend fun savePdfStateMerged(states: Map<String, SessionCodec.PdfState>) {
        if (states.isEmpty()) return
        context.sessionDataStore.edit { prefs ->
            val merged = SessionCodec.decodePdfState(prefs[pdfStateKey]).toMutableMap()
            merged.putAll(states)
            prefs[pdfStateKey] = SessionCodec.encodePdfState(merged)
        }
    }
}
