package com.timetochilltoo.fileviewer.feature.markdown

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timetochilltoo.fileviewer.core.model.DocumentTab
import com.timetochilltoo.fileviewer.core.model.ViewerDocument

@Composable
fun MarkdownWorkspace(
    tab: DocumentTab,
    onTextChange: (String) -> Unit,
) {
    val document = tab.document as? ViewerDocument.Markdown ?: return
    BasicTextField(
        value = document.text,
        onValueChange = onTextChange,
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    )
}
