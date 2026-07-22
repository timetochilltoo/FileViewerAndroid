package com.timetochilltoo.fileviewer.feature.markdown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private data class GuideItem(val title: String, val syntax: String)

private val guideItems = listOf(
    GuideItem("Heading", "# Heading 1\n## Heading 2"),
    GuideItem("Bold", "**bold text**"),
    GuideItem("Italic", "*italic text*"),
    GuideItem("Bold + Italic", "***bold and italic***"),
    GuideItem("Strikethrough", "~~crossed out~~"),
    GuideItem("Link", "[link text](https://example.com)"),
    GuideItem("Image", "![alt text](image.png)"),
    GuideItem("Bullet list", "- first item\n- second item"),
    GuideItem("Numbered list", "1. first item\n2. second item"),
    GuideItem("Task list", "- [ ] to do\n- [x] done"),
    GuideItem("Quote", "> quoted text"),
    GuideItem("Inline code", "`code`"),
    GuideItem("Code block", "```\ncode block\n```"),
    GuideItem("Table", "| Column 1 | Column 2 |\n| --- | --- |\n| cell | cell |"),
    GuideItem("Horizontal rule", "---"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarkdownGuideScreen(onClose: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Markdown Guide") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            items(guideItems) { item ->
                Column(modifier = Modifier.padding(vertical = 10.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            item.syntax,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}
