package com.timetochilltoo.fileviewer.feature.shell

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timetochilltoo.fileviewer.core.files.DocumentRepository
import com.timetochilltoo.fileviewer.core.files.RecentDocument
import com.timetochilltoo.fileviewer.core.files.RecentsStore
import com.timetochilltoo.fileviewer.core.files.SessionCodec
import com.timetochilltoo.fileviewer.core.files.SessionStore
import com.timetochilltoo.fileviewer.core.model.DocumentKind
import com.timetochilltoo.fileviewer.core.model.DocumentTab
import com.timetochilltoo.fileviewer.core.model.MarkdownMode
import com.timetochilltoo.fileviewer.core.model.TabManager
import com.timetochilltoo.fileviewer.core.model.ViewerDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(application)
    private val recentsStore = RecentsStore(application)
    private val sessionStore = SessionStore(application)
    private val tabManager = TabManager()

    private var sessionRestoreEnabled = true
    private val scrollPositions = mutableMapOf<String, Int>()

    data class ShellUiState(
        val tabs: List<DocumentTab> = emptyList(),
        val selectedTabId: String? = null,
    )

    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    private val _closeRequestTabId = MutableStateFlow<String?>(null)
    val closeRequestTabId: StateFlow<String?> = _closeRequestTabId.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _markdownMode = MutableStateFlow(MarkdownMode.SOURCE)
    val markdownMode: StateFlow<MarkdownMode> = _markdownMode.asStateFlow()

    val recents: StateFlow<List<RecentDocument>> = recentsStore.recents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedTab: DocumentTab?
        get() = tabManager.selected

    init {
        viewModelScope.launch {
            scrollPositions.putAll(sessionStore.loadScroll())
            if (sessionRestoreEnabled) {
                restoreSession()
            }
        }
    }

    fun setMarkdownMode(mode: MarkdownMode) {
        _markdownMode.value = mode
    }

    fun skipSessionRestore() {
        sessionRestoreEnabled = false
    }

    private suspend fun restoreSession() {
        val session = sessionStore.load()
        session.tabs.forEach { entry ->
            repository.load(entry.uri)?.let { document ->
                tabManager.add(
                    DocumentTab(
                        document = document,
                        markdownScrollY = scrollPositions[entry.uri] ?: 0,
                    ),
                )
            }
        }
        session.selectedUri?.let { uri ->
            tabManager.findByUri(uri)?.let { tabManager.select(it.id) }
        }
        if (tabManager.tabs.isNotEmpty()) pushState()
    }

    private fun sessionSnapshot(): SessionCodec.Session = SessionCodec.Session(
        tabs = tabManager.tabs.mapNotNull { tab ->
            tab.document.uri?.let { uri ->
                SessionCodec.Entry(
                    uri = uri,
                    kind = when (tab.document) {
                        is ViewerDocument.Markdown -> DocumentKind.MARKDOWN
                        is ViewerDocument.Pdf -> DocumentKind.PDF
                    },
                )
            }
        },
        selectedUri = tabManager.selected?.document?.uri,
    )

    private fun scheduleSessionSave() {
        viewModelScope.launch { sessionStore.save(sessionSnapshot()) }
    }

    fun updateMarkdownScroll(tabId: String, scrollY: Int) {
        val tab = tabManager.findById(tabId) ?: return
        val uri = tab.document.uri ?: return
        scrollPositions[uri] = scrollY
    }

    fun persistState() {
        viewModelScope.launch {
            sessionStore.save(sessionSnapshot())
            sessionStore.saveScrollMerged(scrollPositions)
        }
    }

    private fun pushState() {
        _uiState.value = ShellUiState(
            tabs = tabManager.tabs,
            selectedTabId = tabManager.selectedTabId,
        )
    }

    fun openUris(uris: List<String>) = uris.forEach(::openUri)

    fun openUri(uri: String) {
        tabManager.findByUri(uri)?.let {
            tabManager.select(it.id)
            pushState()
            return
        }
        val document = repository.load(uri)
        if (document == null) {
            android.util.Log.w("FileViewer", "openUri: repository.load returned null for $uri")
            _statusMessage.value = "Could not open document"
            return
        }
        tabManager.add(
            DocumentTab(
                document = document,
                markdownScrollY = scrollPositions[uri] ?: 0,
            ),
        )
        pushState()
        scheduleSessionSave()
        _statusMessage.value = null
        viewModelScope.launch { recentsStore.add(uri, document.displayName) }
    }

    fun reopenRecent(recent: RecentDocument) = openUri(recent.uri)

    fun newMarkdownDocument(initialText: String = "") {
        tabManager.add(
            DocumentTab(
                document = ViewerDocument.Markdown(
                    uri = null,
                    displayName = "Untitled",
                    text = initialText,
                    savedText = "",
                ),
            ),
        )
        pushState()
    }

    fun selectTab(id: String) {
        tabManager.select(id)
        pushState()
        scheduleSessionSave()
    }

    fun requestCloseTab(id: String) {
        val tab = tabManager.findById(id) ?: return
        if (TabManager.needsCloseConfirmation(tab)) {
            _closeRequestTabId.value = id
        } else {
            closeTab(id)
        }
    }

    fun requestCloseSelectedTab() {
        tabManager.selectedTabId?.let(::requestCloseTab)
    }

    fun confirmClose(tabId: String) {
        _closeRequestTabId.value = null
        closeTab(tabId)
    }

    fun cancelClose() {
        _closeRequestTabId.value = null
    }

    private fun closeTab(id: String) {
        val removed = tabManager.remove(id)
        (removed?.document as? ViewerDocument.Pdf)?.handle?.close()
        pushState()
        scheduleSessionSave()
    }

    fun updateMarkdownText(tabId: String, text: String) {
        val tab = tabManager.findById(tabId) ?: return
        val markdown = tab.document as? ViewerDocument.Markdown ?: return
        tabManager.update(tab.copy(document = markdown.copy(text = text)))
        pushState()
    }

    fun saveTab(tabId: String): Boolean {
        val tab = tabManager.findById(tabId) ?: return false
        val markdown = tab.document as? ViewerDocument.Markdown ?: return false
        val uri = markdown.uri ?: return false
        return if (repository.writeMarkdown(uri, markdown.text)) {
            tabManager.update(tab.copy(document = markdown.copy(savedText = markdown.text)))
            pushState()
            _statusMessage.value = "Saved"
            true
        } else {
            _statusMessage.value = "Save failed"
            false
        }
    }

    fun saveTabAs(tabId: String, uri: String): Boolean {
        val tab = tabManager.findById(tabId) ?: return false
        val markdown = tab.document as? ViewerDocument.Markdown ?: return false
        if (!repository.writeMarkdown(uri, markdown.text)) {
            _statusMessage.value = "Save failed"
            return false
        }
        repository.takePersistablePermission(uri)
        val name = repository.displayName(android.net.Uri.parse(uri)) ?: "Untitled.md"
        tabManager.update(
            tab.copy(
                document = markdown.copy(
                    uri = uri,
                    displayName = name,
                    savedText = markdown.text,
                ),
            ),
        )
        pushState()
        _statusMessage.value = "Saved"
        viewModelScope.launch { recentsStore.add(uri, name) }
        scheduleSessionSave()
        return true
    }
}
