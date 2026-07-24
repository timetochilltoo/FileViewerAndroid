package com.timetochilltoo.fileviewer.feature.shell

import android.app.Activity
import android.app.Application
import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timetochilltoo.fileviewer.core.files.DocumentRepository
import com.timetochilltoo.fileviewer.core.files.RecentDocument
import com.timetochilltoo.fileviewer.core.files.RecentsStore
import com.timetochilltoo.fileviewer.core.files.SessionCodec
import com.timetochilltoo.fileviewer.core.files.SessionStore
import com.timetochilltoo.fileviewer.core.model.DocumentKind
import com.timetochilltoo.fileviewer.core.model.DocumentTab
import com.timetochilltoo.fileviewer.core.model.EditorSelection
import com.timetochilltoo.fileviewer.core.model.HeadingJump
import com.timetochilltoo.fileviewer.core.model.MarkdownFormatCommand
import com.timetochilltoo.fileviewer.core.model.MarkdownFormatter
import com.timetochilltoo.fileviewer.core.model.MarkdownMode
import com.timetochilltoo.fileviewer.core.model.PdfScaleMode
import com.timetochilltoo.fileviewer.core.model.TabManager
import com.timetochilltoo.fileviewer.core.model.ViewerDocument
import com.timetochilltoo.fileviewer.feature.pdf.PdfPrintAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(application)
    private val recentsStore = RecentsStore(application)
    private val sessionStore = SessionStore(application)
    private val tabManager = TabManager()

    private var sessionRestoreEnabled = true
    private val scrollPositions = mutableMapOf<String, Int>()
    private val pdfStates = mutableMapOf<String, SessionCodec.PdfState>()

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

    private val _editorSelectionOverride = MutableStateFlow<EditorSelection?>(null)
    val editorSelectionOverride: StateFlow<EditorSelection?> =
        _editorSelectionOverride.asStateFlow()

    private val _headingJump = MutableStateFlow<HeadingJump?>(null)
    val headingJump: StateFlow<HeadingJump?> = _headingJump.asStateFlow()

    private val _pdfPageJump = MutableStateFlow<Pair<String, Int>?>(null)
    val pdfPageJump: StateFlow<Pair<String, Int>?> = _pdfPageJump.asStateFlow()

    private val editorSelections = mutableMapOf<String, Pair<Int, Int>>()
    private val pdfSearchJobs = mutableMapOf<String, Job>()

    override fun onCleared() {
        tabManager.tabs.forEach { tab ->
            (tab.document as? ViewerDocument.Pdf)?.handle?.close()
        }
    }

    val recents: StateFlow<List<RecentDocument>> = recentsStore.recents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedTab: DocumentTab?
        get() = tabManager.selected

    init {
        viewModelScope.launch {
            scrollPositions.putAll(sessionStore.loadScroll())
            pdfStates.putAll(sessionStore.loadPdfState())
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
            val document = withContext(Dispatchers.IO) { repository.load(entry.uri) }
            document?.let {
                val base = DocumentTab(
                    document = it,
                    markdownScrollY = scrollPositions[entry.uri] ?: 0,
                )
                tabManager.add(base.withPdfState(entry.uri))
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

    private fun DocumentTab.withPdfState(uri: String): DocumentTab {
        val state = pdfStates[uri] ?: return this
        val pdf = document as? ViewerDocument.Pdf ?: return this
        return copy(
            pdfPage = state.page.coerceIn(0, pdf.pageCount - 1),
            pdfScale = state.scale.coerceAtLeast(0.1f),
            pdfScaleMode = state.mode,
        )
    }

    private fun updatePdfStatesFromTabs() {
        tabManager.tabs.forEach { tab ->
            tab.document.uri?.let { uri ->
                (tab.document as? ViewerDocument.Pdf)?.let { pdf ->
                    pdfStates[uri] = SessionCodec.PdfState(
                        page = tab.pdfPage.coerceIn(0, pdf.pageCount - 1),
                        scale = tab.pdfScale.coerceAtLeast(0.1f),
                        mode = tab.pdfScaleMode,
                    )
                }
            }
        }
    }

    private fun scheduleSessionSave() {
        viewModelScope.launch { sessionStore.save(sessionSnapshot()) }
    }

    fun updateMarkdownScroll(tabId: String, scrollY: Int) {
        val tab = tabManager.findById(tabId) ?: return
        val uri = tab.document.uri ?: return
        scrollPositions[uri] = scrollY
    }

    fun updateMarkdownSelection(tabId: String, start: Int, end: Int) {
        editorSelections[tabId] = start to end
    }

    fun applyMarkdownFormat(command: MarkdownFormatCommand) {
        val tab = tabManager.selected ?: return
        val markdown = tab.document as? ViewerDocument.Markdown ?: return
        val (start, end) = editorSelections[tab.id]
            ?: (markdown.text.length to markdown.text.length)
        val result = MarkdownFormatter.apply(command, markdown.text, start, end)
        updateMarkdownText(tab.id, result.text)
        editorSelections[tab.id] = result.selectionStart to result.selectionEnd
        _editorSelectionOverride.value = EditorSelection(
            tabId = tab.id,
            start = result.selectionStart,
            end = result.selectionEnd,
        )
    }

    fun setSearchText(tabId: String, query: String) {
        val tab = tabManager.findById(tabId) ?: return
        val trimmed = query.trim()
        when (val doc = tab.document) {
            is ViewerDocument.Markdown -> {
                val occurrences = countOccurrences(doc.text, trimmed)
                tabManager.update(
                    tab.copy(
                        searchText = query,
                        searchMatchCount = occurrences,
                        searchMatchIndex = if (occurrences > 0) 0 else -1,
                    ),
                )
                pushState()
            }
            is ViewerDocument.Pdf -> {
                tabManager.update(
                    tab.copy(
                        searchText = query,
                        searchMatchCount = 0,
                        searchMatchIndex = -1,
                        pdfSearchResults = emptyList(),
                    ),
                )
                pushState()
                pdfSearchJobs[tabId]?.cancel()
                val handle = doc.handle ?: return
                if (trimmed.isBlank()) {
                    pdfSearchJobs.remove(tabId)
                    return
                }
                pdfSearchJobs[tabId] = viewModelScope.launch(Dispatchers.IO) {
                    delay(PDF_SEARCH_DEBOUNCE_MS)
                    val results = handle.search(trimmed)
                    withContext(Dispatchers.Main) {
                        pdfSearchJobs.remove(tabId)
                        val current = tabManager.findById(tabId) ?: return@withContext
                        if (current.searchText != query) return@withContext
                        tabManager.update(
                            current.copy(
                                searchMatchCount = results.size,
                                searchMatchIndex = if (results.isNotEmpty()) 0 else -1,
                                pdfSearchResults = results,
                                pdfPage = results.firstOrNull()?.pageIndex ?: current.pdfPage,
                            ),
                        )
                        pushState()
                        results.firstOrNull()?.let { _pdfPageJump.value = tabId to it.pageIndex }
                    }
                }
            }
        }
    }

    fun nextSearchMatch(tabId: String) = stepSearchMatch(tabId, +1)

    fun previousSearchMatch(tabId: String) = stepSearchMatch(tabId, -1)

    private fun stepSearchMatch(tabId: String, delta: Int) {
        val tab = tabManager.findById(tabId) ?: return
        if (tab.searchMatchCount <= 0) return
        val next = Math.floorMod(
            tab.searchMatchIndex + delta,
            tab.searchMatchCount,
        )
        val result = tab.pdfSearchResults.getOrNull(next)
        tabManager.update(tab.copy(searchMatchIndex = next, pdfPage = result?.pageIndex ?: tab.pdfPage))
        pushState()
        result?.let { _pdfPageJump.value = tabId to it.pageIndex }
    }

    fun setPdfPage(tabId: String, page: Int) {
        val tab = tabManager.findById(tabId) ?: return
        val clamped = page.coerceIn(0, (tab.document as? ViewerDocument.Pdf)?.pageCount?.minus(1) ?: 0)
        tabManager.update(tab.copy(pdfPage = clamped))
        tab.document.uri?.let { uri ->
            pdfStates[uri] = SessionCodec.PdfState(
                page = clamped,
                scale = tab.pdfScale.coerceAtLeast(0.1f),
                mode = tab.pdfScaleMode,
            )
        }
        pushState()
    }

    fun goToPdfPage(tabId: String, page: Int) {
        setPdfPage(tabId, page)
        _pdfPageJump.value = tabId to page
    }

    fun setPdfScale(tabId: String, scale: Float) {
        val tab = tabManager.findById(tabId) ?: return
        if (tab.document !is ViewerDocument.Pdf) return
        val clamped = scale.coerceAtLeast(0.1f)
        tabManager.update(
            tab.copy(
                pdfScale = clamped,
                pdfScaleMode = PdfScaleMode.FREE,
            ),
        )
        tab.document.uri?.let { uri ->
            pdfStates[uri] = SessionCodec.PdfState(
                page = tab.pdfPage,
                scale = clamped,
                mode = PdfScaleMode.FREE,
            )
        }
        pushState()
    }

    fun setPdfScaleMode(tabId: String, mode: PdfScaleMode) {
        val tab = tabManager.findById(tabId) ?: return
        if (tab.document !is ViewerDocument.Pdf) return
        tabManager.update(tab.copy(pdfScaleMode = mode))
        tab.document.uri?.let { uri ->
            pdfStates[uri] = SessionCodec.PdfState(
                page = tab.pdfPage,
                scale = tab.pdfScale.coerceAtLeast(0.1f),
                mode = mode,
            )
        }
        pushState()
    }

    fun setPdfReadingMode(tabId: String, enabled: Boolean) {
        val tab = tabManager.findById(tabId) ?: return
        val pdf = tab.document as? ViewerDocument.Pdf ?: return
        tabManager.update(tab.copy(pdfReadingMode = enabled))
        pushState()
        if (enabled && pdf.handle != null) {
            viewModelScope.launch(Dispatchers.IO) {
                val text = buildString {
                    for (i in 0 until pdf.pageCount) {
                        appendLine("— Page ${i + 1} —")
                        appendLine(pdf.handle.extractText(i))
                        appendLine()
                    }
                }
                withContext(Dispatchers.Main) {
                    val current = tabManager.findById(tabId) ?: return@withContext
                    tabManager.update(current.copy(pdfReflowText = text))
                    pushState()
                }
            }
        }
    }

    private fun countOccurrences(haystack: String, needle: String): Int {
        if (needle.isBlank()) return 0
        var count = 0
        var index = haystack.indexOf(needle, 0, ignoreCase = true)
        while (index >= 0) {
            count++
            index = haystack.indexOf(needle, index + needle.length, ignoreCase = true)
        }
        return count
    }

    fun requestHeadingJump(tabId: String, headingText: String) {
        if (tabManager.findById(tabId) == null) return
        _headingJump.value = HeadingJump(tabId, headingText)
        _markdownMode.value = MarkdownMode.PREVIEW
    }

    fun consumePdfPageJump() {
        _pdfPageJump.value = null
    }

    fun persistState() {
        viewModelScope.launch {
            updatePdfStatesFromTabs()
            sessionStore.save(sessionSnapshot())
            sessionStore.saveScrollMerged(scrollPositions)
            sessionStore.savePdfStateMerged(pdfStates)
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
        viewModelScope.launch {
            val document = withContext(Dispatchers.IO) { repository.load(uri) }
            if (document == null) {
                android.util.Log.w("FileViewer", "openUri: repository.load returned null for $uri")
                _statusMessage.value = "Could not open document"
                return@launch
            }
            tabManager.add(
                DocumentTab(
                    document = document,
                    markdownScrollY = scrollPositions[uri] ?: 0,
                ).withPdfState(uri),
            )
            pushState()
            scheduleSessionSave()
            _statusMessage.value = null
            viewModelScope.launch { recentsStore.add(uri, document.displayName) }
        }
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
        viewModelScope.launch { sessionStore.savePdfStateMerged(pdfStates) }
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

    fun printPdf(tabId: String, activity: Activity): Boolean {
        val tab = tabManager.findById(tabId) ?: return false
        val pdf = tab.document as? ViewerDocument.Pdf ?: return false
        val manager = activity
            .getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
        val adapter = PdfPrintAdapter(activity.applicationContext, pdf.uri)
        manager.print(pdf.displayName, adapter, PrintAttributes.Builder().build())
        _statusMessage.value = "Printing…"
        return true
    }

    private companion object {
        const val PDF_SEARCH_DEBOUNCE_MS = 250L
    }
}
