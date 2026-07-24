package com.timetochilltoo.fileviewer.feature.markdown

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timetochilltoo.fileviewer.core.model.EditorSelection

@Composable
fun MarkdownSourceEditor(
    tabId: String,
    text: String,
    onTextChange: (String) -> Unit,
    onSelectionChange: (Int, Int) -> Unit,
    selectionOverride: EditorSelection?,
    modifier: Modifier = Modifier,
) {
    var fieldValue by remember(tabId) { mutableStateOf(TextFieldValue(text)) }

    LaunchedEffect(selectionOverride) {
        val override = selectionOverride ?: return@LaunchedEffect
        if (override.tabId == tabId && override.start <= text.length) {
            fieldValue = TextFieldValue(
                text,
                selection = TextRange(
                    override.start,
                    override.end.coerceAtMost(text.length),
                ),
            )
        }
    }

    BasicTextField(
        value = fieldValue,
        onValueChange = { newValue ->
            fieldValue = newValue
            onSelectionChange(newValue.selection.start, newValue.selection.end)
            if (newValue.text != text) {
                onTextChange(newValue.text)
            }
        },
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrect = false,
            keyboardType = KeyboardType.Text,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
    )
}
