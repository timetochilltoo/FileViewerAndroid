package com.timetochilltoo.fileviewer.feature.shell

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.timetochilltoo.fileviewer.core.files.PdfDocumentHandle
import com.timetochilltoo.fileviewer.core.files.RecentDocument
import com.timetochilltoo.fileviewer.core.model.DocumentTab
import com.timetochilltoo.fileviewer.core.model.MarkdownOutline
import com.timetochilltoo.fileviewer.core.model.PdfScaleMode
import com.timetochilltoo.fileviewer.core.model.ViewerDocument
import com.timetochilltoo.fileviewer.feature.markdown.MarkdownGuideScreen
import com.timetochilltoo.fileviewer.feature.markdown.MarkdownWorkspace
import com.timetochilltoo.fileviewer.feature.pdf.PdfWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val closeRequestTabId by viewModel.closeRequestTabId.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val recents by viewModel.recents.collectAsState()
    val markdownMode by viewModel.markdownMode.collectAsState()
    val selectionOverride by viewModel.editorSelectionOverride.collectAsState()
    val headingJump by viewModel.headingJump.collectAsState()
    val pdfPageJump by viewModel.pdfPageJump.collectAsState()

    val tabs = uiState.tabs
    val selectedTabId = uiState.selectedTabId
    val selectedTab = tabs.firstOrNull { it.id == selectedTabId }

    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var pendingSaveAs by remember { mutableStateOf<PendingSaveAs?>(null) }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        val request = pendingSaveAs
        pendingSaveAs = null
        if (uri != null && request != null) {
            val saved = viewModel.saveTabAs(request.tabId, uri.toString())
            if (saved && request.closeAfter) {
                viewModel.confirmClose(request.tabId)
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.openUris(uris.map { it.toString() })
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = !drawerState.isOpen && tabs.isNotEmpty()) {
        if (searchVisible && selectedTab?.searchText?.isNotBlank() == true) {
            viewModel.setSearchText(selectedTab.id, "")
        } else {
            viewModel.requestCloseSelectedTab()
        }
    }

    val activity = LocalContext.current as? Activity

    if (showGuide) {
        MarkdownGuideScreen(onClose = { showGuide = false })
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = tabs.isNotEmpty(),
            drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    selectedTab = selectedTab,
                    onHeadingClick = { headingText ->
                        selectedTab?.let { viewModel.requestHeadingJump(it.id, headingText) }
                        scope.launch { drawerState.close() }
                    },
                    onPdfPageClick = { page ->
                        selectedTab?.let { viewModel.goToPdfPage(it.id, page) }
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("FileViewer") },
                    navigationIcon = {
                        if (tabs.isNotEmpty()) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Contents")
                            }
                        }
                    },
                    actions = {
                        if (tabs.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchVisible = !searchVisible
                                    if (!searchVisible) {
                                        selectedTab?.let { viewModel.setSearchText(it.id, "") }
                                    }
                                },
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                        }
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Markdown Document") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.newMarkdownDocument()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Open…") },
                                onClick = {
                                    menuExpanded = false
                                    openDocumentLauncher.launch(arrayOf("*/*"))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Save") },
                                enabled = selectedTab?.hasUnsavedChanges == true &&
                                    selectedTab.document is ViewerDocument.Markdown,
                                onClick = {
                                    menuExpanded = false
                                    val tab = selectedTab ?: return@DropdownMenuItem
                                    if (tab.document.uri != null) {
                                        viewModel.saveTab(tab.id)
                                    } else {
                                        pendingSaveAs = PendingSaveAs(tab.id, closeAfter = false)
                                        createDocumentLauncher.launch(
                                            suggestedFileName(tab.document.displayName),
                                        )
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Save As…") },
                                enabled = selectedTab?.document is ViewerDocument.Markdown,
                                onClick = {
                                    menuExpanded = false
                                    val tab = selectedTab ?: return@DropdownMenuItem
                                    pendingSaveAs = PendingSaveAs(tab.id, closeAfter = false)
                                    createDocumentLauncher.launch(
                                        suggestedFileName(tab.document.displayName),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Print") },
                                enabled = selectedTab?.document is ViewerDocument.Pdf,
                                onClick = {
                                    menuExpanded = false
                                    selectedTab?.let { tab ->
                                        activity?.let {
                                            viewModel.printPdf(tab.id, it)
                                        }
                                    }
                                },
                            )
                            HorizontalDivider()
                            if (selectedTab?.document is ViewerDocument.Pdf) {
                                DropdownMenuItem(
                                    text = { Text("Fit Width") },
                                    onClick = {
                                        menuExpanded = false
                                        selectedTab?.let {
                                            viewModel.setPdfScaleMode(it.id, PdfScaleMode.FIT_WIDTH)
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Fit Page") },
                                    onClick = {
                                        menuExpanded = false
                                        selectedTab?.let {
                                            viewModel.setPdfScaleMode(it.id, PdfScaleMode.FIT_PAGE)
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (selectedTab?.pdfReadingMode == true) {
                                                "Exit Reading Mode"
                                            } else {
                                                "Reading Mode"
                                            },
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        selectedTab?.let {
                                            viewModel.setPdfReadingMode(it.id, !it.pdfReadingMode)
                                        }
                                    },
                                )
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = { Text("Markdown Guide") },
                                onClick = {
                                    menuExpanded = false
                                    showGuide = true
                                },
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (tabs.isNotEmpty()) {
                    StatusStrip(selectedTab, statusMessage)
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                if (tabs.isNotEmpty()) {
                    TabStrip(
                        tabs = tabs,
                        selectedTabId = selectedTabId,
                        onSelect = viewModel::selectTab,
                        onClose = viewModel::requestCloseTab,
                    )
                    if (searchVisible && selectedTab != null) {
                        SearchBar(
                            tab = selectedTab,
                            onQueryChange = { viewModel.setSearchText(selectedTab.id, it) },
                            onNext = { viewModel.nextSearchMatch(selectedTab.id) },
                            onPrevious = { viewModel.previousSearchMatch(selectedTab.id) },
                            onClose = {
                                viewModel.setSearchText(selectedTab.id, "")
                                searchVisible = false
                            },
                        )
                    }
                }
                when {
                    tabs.isEmpty() -> EmptyState(
                        recents = recents,
                        statusMessage = statusMessage,
                        onOpen = { openDocumentLauncher.launch(arrayOf("*/*")) },
                        onNewMarkdown = viewModel::newMarkdownDocument,
                        onRecent = viewModel::reopenRecent,
                    )

                    selectedTab?.document is ViewerDocument.Markdown -> MarkdownWorkspace(
                        tab = selectedTab,
                        mode = markdownMode,
                        onModeChange = viewModel::setMarkdownMode,
                        onTextChange = { viewModel.updateMarkdownText(selectedTab.id, it) },
                        onPreviewScroll = { viewModel.updateMarkdownScroll(selectedTab.id, it) },
                        onSelectionChange = { start, end ->
                            viewModel.updateMarkdownSelection(selectedTab.id, start, end)
                        },
                        selectionOverride = selectionOverride,
                        headingJump = headingJump,
                        onFormatCommand = viewModel::applyMarkdownFormat,
                    )

                    selectedTab?.document is ViewerDocument.Pdf -> {
                        BoxWithConstraints(
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            val density = LocalDensity.current
                            val widthPx = with(density) { maxWidth.toPx().toInt() }
                            val heightPx = with(density) { maxHeight.toPx().toInt() }
                            PdfWorkspace(
                                tab = selectedTab,
                                viewportWidth = widthPx,
                                viewportHeight = heightPx,
                                pdfPageJump = pdfPageJump,
                                onConsumePageJump = viewModel::consumePdfPageJump,
                                onPageChange = { viewModel.setPdfPage(selectedTab.id, it) },
                                onScaleChange = { viewModel.setPdfScale(selectedTab.id, it) },
                                onScaleModeChange = { viewModel.setPdfScaleMode(selectedTab.id, it) },
                                onReadingModeChange = { viewModel.setPdfReadingMode(selectedTab.id, it) },
                            )
                        }
                    }
                }
            }
        }
    }

    closeRequestTabId?.let { tabId ->
        val tab = tabs.firstOrNull { it.id == tabId }
        if (tab != null) {
            UnsavedCloseDialog(
                tab = tab,
                onSave = {
                    if (tab.document.uri != null) {
                        if (viewModel.saveTab(tabId)) {
                            viewModel.confirmClose(tabId)
                        }
                    } else {
                        pendingSaveAs = PendingSaveAs(tabId, closeAfter = true)
                        createDocumentLauncher.launch(
                            suggestedFileName(tab.document.displayName),
                        )
                    }
                },
                onDiscard = { viewModel.confirmClose(tabId) },
                onCancel = viewModel::cancelClose,
            )
        }
    }
}

private data class PendingSaveAs(val tabId: String, val closeAfter: Boolean)

private fun suggestedFileName(displayName: String): String =
    if (displayName.endsWith(".md", ignoreCase = true)) displayName else "$displayName.md"

@Composable
private fun DrawerContent(
    selectedTab: DocumentTab?,
    onHeadingClick: (String) -> Unit,
    onPdfPageClick: (Int) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Contents", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        when (val document = selectedTab?.document) {
            is ViewerDocument.Markdown -> {
                val headings = MarkdownOutline.headings(document.text)
                if (headings.isEmpty()) {
                    Text(
                        "No headings in this document",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn {
                        items(headings) { heading ->
                            Text(
                                text = heading.text,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onHeadingClick(heading.text) }
                                    .padding(
                                        start = ((heading.level - 1) * 16).dp,
                                        top = 8.dp,
                                        bottom = 8.dp,
                                    ),
                            )
                        }
                    }
                }
            }

            is ViewerDocument.Pdf -> PdfDrawerContent(
                document = document,
                onPageClick = onPdfPageClick,
            )

            null -> Text("No document open", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PdfDrawerContent(
    document: ViewerDocument.Pdf,
    onPageClick: (Int) -> Unit,
) {
    val outline = remember(document.handle) {
        document.handle?.outline().orEmpty()
    }
    if (outline.isEmpty()) {
        Text(
            "No PDF outline available",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
    } else {
        LazyColumn {
            items(outline) { item ->
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPageClick(item.pageIndex) }
                        .padding(
                            start = (item.depth * 16).dp,
                            top = 8.dp,
                            bottom = 8.dp,
                        ),
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
    Text("Pages", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    LazyColumn {
        items(document.pageCount) { page ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPageClick(page) }
                    .padding(vertical = 4.dp),
            ) {
                document.handle?.let { handle ->
                    val density = LocalDensity.current
                    val maxWidthPx = with(density) { 96.dp.roundToPx() }
                    PdfPageThumbnail(
                        handle = handle,
                        pageIndex = page,
                        maxWidthPx = maxWidthPx,
                        modifier = Modifier
                            .width(96.dp)
                            .heightIn(max = 132.dp)
                            .background(Color.LightGray),
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = "Page ${page + 1}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
private fun PdfPageThumbnail(
    handle: PdfDocumentHandle,
    pageIndex: Int,
    maxWidthPx: Int,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, handle, pageIndex, maxWidthPx) {
        value = withContext(Dispatchers.IO) {
            handle.renderThumbnail(pageIndex, maxWidthPx)
        }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Page ${pageIndex + 1} thumbnail",
            modifier = modifier,
        )
    } ?: Box(modifier = modifier)
}

@Composable
private fun SearchBar(
    tab: DocumentTab,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        OutlinedTextField(
            value = tab.searchText,
            onValueChange = onQueryChange,
            placeholder = { Text("Search") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onNext() }),
            modifier = Modifier.weight(1f),
        )
        if (tab.searchText.isNotBlank()) {
            Text(
                text = if (tab.searchMatchCount > 0) {
                    "${tab.searchMatchIndex + 1}/${tab.searchMatchCount}"
                } else {
                    "0/0"
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
        IconButton(onClick = onPrevious, enabled = tab.searchMatchCount > 0) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
        }
        IconButton(onClick = onNext, enabled = tab.searchMatchCount > 0) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = "Close search")
        }
    }
    HorizontalDivider()
}

@Composable
private fun TabStrip(
    tabs: List<DocumentTab>,
    selectedTabId: String?,
    onSelect: (String) -> Unit,
    onClose: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        tabs.forEach { tab ->
            key(tab.id) {
                val selected = tab.id == selectedTabId
                Surface(
                    onClick = { onSelect(tab.id) },
                    color = if (selected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 6.dp),
                    ) {
                        if (tab.hasUnsavedChanges) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFFFF9800), CircleShape),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = tab.document.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = if (selected) {
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            modifier = Modifier.widthIn(max = 140.dp),
                        )
                        IconButton(
                            onClick = { onClose(tab.id) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close tab",
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun StatusStrip(selectedTab: DocumentTab?, statusMessage: String?) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selectedTab?.document?.displayName ?: "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp),
            )
            if (selectedTab?.hasUnsavedChanges == true) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Unsaved changes",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9800),
                )
            }
            Spacer(Modifier.weight(1f))
            if (selectedTab?.document is ViewerDocument.Pdf) {
                Text(
                    text = "${selectedTab.document.pageCount} pages",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.width(12.dp))
            }
            if (statusMessage != null) {
                Text(text = statusMessage, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EmptyState(
    recents: List<RecentDocument>,
    statusMessage: String?,
    onOpen: () -> Unit,
    onNewMarkdown: () -> Unit,
    onRecent: (RecentDocument) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("FileViewer", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Open a Markdown note or PDF to get started.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onOpen) { Text("Open Document") }
            OutlinedButton(onClick = onNewMarkdown) { Text("New Markdown") }
        }
        if (statusMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(32.dp))
            Text(
                "Recent",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(recents, key = { it.uri }) { recent ->
                    Text(
                        text = recent.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRecent(recent) }
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UnsavedCloseDialog(
    tab: DocumentTab,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Unsaved changes") },
        text = {
            Text("Save changes to “${tab.document.displayName}” before closing?")
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) { Text("Don't Save") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        },
    )
}
