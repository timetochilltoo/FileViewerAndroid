package com.timetochilltoo.fileviewer.feature.markdown

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.timetochilltoo.fileviewer.core.model.MarkdownFormatCommand

@Composable
fun FormattingToolbar(
    onCommand: (MarkdownFormatCommand) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            var menuExpanded by remember { mutableStateOf(false) }
            TextButton(onClick = { menuExpanded = true }) {
                Text("Format ▾")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                MarkdownFormatCommand.entries.forEach { command ->
                    DropdownMenuItem(
                        text = { Text(command.label) },
                        onClick = {
                            menuExpanded = false
                            onCommand(command)
                        },
                    )
                }
            }
        }
        QuickButton("B", style = TextStyle(fontWeight = FontWeight.Bold)) {
            onCommand(MarkdownFormatCommand.BOLD)
        }
        QuickButton("I", style = TextStyle(fontStyle = FontStyle.Italic)) {
            onCommand(MarkdownFormatCommand.ITALIC)
        }
        QuickButton("U", style = TextStyle(textDecoration = TextDecoration.Underline)) {
            onCommand(MarkdownFormatCommand.UNDERLINE)
        }
        QuickButton("H") {
            onCommand(MarkdownFormatCommand.HEADING)
        }
    }
}

@Composable
private fun QuickButton(
    label: String,
    style: TextStyle = TextStyle(),
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, modifier = Modifier.padding(horizontal = 2.dp)) {
        Text(label, style = style)
    }
}
