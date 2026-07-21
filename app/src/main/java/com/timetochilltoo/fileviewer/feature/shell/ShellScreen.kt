package com.timetochilltoo.fileviewer.feature.shell

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.timetochilltoo.fileviewer.core.files.RecentDocument
import com.timetochilltoo.fileviewer.core.model.DocumentTab
import com.timetochilltoo.fileviewer.core.model.ViewerDocument
import com.timetochilltoo.fileviewer.feature.markdown.MarkdownWorkspace
import com.timetochilltoo.fileviewer.feature.pdf.PdfWorkspace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShellScreen(viewModel: AppViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val closeRequestTabId by viewModel.closeRequestTabId.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val recents by viewModel.recents.collectAsState()

    val tabs = uiState.tabs
    val selectedTabId = uiState.selectedTabId
    val selectedTab = tabs.firstOrNull { it.id == selectedTabId }

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

    BackHandler(enabled = tabs.isNotEmpty()) {
        viewModel.requestCloseSelectedTab()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FileViewer") },
                actions = {
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
                    onTextChange = { viewModel.updateMarkdownText(selectedTab.id, it) },
                )

                selectedTab?.document is ViewerDocument.Pdf -> PdfWorkspace(tab = selectedTab)
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
