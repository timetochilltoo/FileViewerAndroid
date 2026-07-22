package com.timetochilltoo.fileviewer.feature.markdown

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timetochilltoo.fileviewer.core.model.DocumentTab
import com.timetochilltoo.fileviewer.core.model.EditorSelection
import com.timetochilltoo.fileviewer.core.model.HeadingJump
import com.timetochilltoo.fileviewer.core.model.MarkdownFormatCommand
import com.timetochilltoo.fileviewer.core.model.MarkdownMode
import com.timetochilltoo.fileviewer.core.model.ViewerDocument

private val SPLIT_MIN_WIDTH = 840.dp

@Composable
fun MarkdownWorkspace(
    tab: DocumentTab,
    mode: MarkdownMode,
    onModeChange: (MarkdownMode) -> Unit,
    onTextChange: (String) -> Unit,
    onPreviewScroll: (Int) -> Unit,
    onSelectionChange: (Int, Int) -> Unit,
    selectionOverride: EditorSelection?,
    headingJump: HeadingJump?,
    onFormatCommand: (MarkdownFormatCommand) -> Unit,
) {
    val document = tab.document as? ViewerDocument.Markdown ?: return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val showSplit = maxWidth >= SPLIT_MIN_WIDTH
        val effectiveMode = if (mode == MarkdownMode.SPLIT && !showSplit) {
            MarkdownMode.SOURCE
        } else {
            mode
        }

        Column(modifier = Modifier.fillMaxSize()) {
            ModeSelector(
                mode = effectiveMode,
                showSplit = showSplit,
                onModeChange = onModeChange,
            )
            if (effectiveMode != MarkdownMode.PREVIEW) {
                FormattingToolbar(onCommand = onFormatCommand)
                HorizontalDivider()
            }
            key(tab.id) {
                when (effectiveMode) {
                    MarkdownMode.SOURCE -> MarkdownSourceEditor(
                        tabId = tab.id,
                        text = document.text,
                        onTextChange = onTextChange,
                        onSelectionChange = onSelectionChange,
                        selectionOverride = selectionOverride,
                    )

                    MarkdownMode.PREVIEW -> MarkdownPreview(
                        text = document.text,
                        initialScrollY = tab.markdownScrollY,
                        onScroll = onPreviewScroll,
                        searchText = tab.searchText,
                        searchMatchIndex = tab.searchMatchIndex,
                        headingJump = headingJump,
                    )

                    MarkdownMode.SPLIT -> Row(modifier = Modifier.fillMaxSize()) {
                        MarkdownSourceEditor(
                            tabId = tab.id,
                            text = document.text,
                            onTextChange = onTextChange,
                            onSelectionChange = onSelectionChange,
                            selectionOverride = selectionOverride,
                            modifier = Modifier.weight(1f),
                        )
                        VerticalDivider()
                        MarkdownPreview(
                            text = document.text,
                            initialScrollY = tab.markdownScrollY,
                            onScroll = onPreviewScroll,
                            searchText = tab.searchText,
                            searchMatchIndex = tab.searchMatchIndex,
                            headingJump = headingJump,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    mode: MarkdownMode,
    showSplit: Boolean,
    onModeChange: (MarkdownMode) -> Unit,
) {
    val modes = buildList {
        add(MarkdownMode.PREVIEW to "Preview")
        add(MarkdownMode.SOURCE to "Source")
        if (showSplit) add(MarkdownMode.SPLIT to "Split")
    }
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        modes.forEachIndexed { index, (itemMode, label) ->
            SegmentedButton(
                selected = mode == itemMode,
                onClick = { onModeChange(itemMode) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = modes.size,
                ),
            ) {
                Text(label)
            }
        }
    }
}
