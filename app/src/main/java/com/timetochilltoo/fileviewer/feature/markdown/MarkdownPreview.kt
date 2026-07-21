package com.timetochilltoo.fileviewer.feature.markdown

import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import kotlinx.coroutines.delay

private const val PREVIEW_DEBOUNCE_MS = 150L

@Composable
fun MarkdownPreview(
    text: String,
    initialScrollY: Int,
    onScroll: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .build()
    }

    val debouncedText by produceState(initialValue = text, text) {
        delay(PREVIEW_DEBOUNCE_MS)
        value = text
    }

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    AndroidView(
        factory = { ctx ->
            val textView = TextView(ctx).apply {
                textSize = 16f
                setLineSpacing(0f, 1.15f)
                setPadding(32, 24, 32, 24)
            }
            ScrollView(ctx).apply {
                addView(textView)
                setOnScrollChangeListener { _, _, scrollY, _, _ -> onScroll(scrollY) }
                post { scrollTo(0, initialScrollY) }
            }
        },
        update = { scrollView ->
            val textView = scrollView.getChildAt(0) as TextView
            textView.setTextColor(textColor)
            markwon.setMarkdown(textView, debouncedText)
        },
        modifier = modifier,
    )
}
