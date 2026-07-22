package com.timetochilltoo.fileviewer.feature.markdown

import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.timetochilltoo.fileviewer.core.model.HeadingJump
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import kotlinx.coroutines.delay

private const val PREVIEW_DEBOUNCE_MS = 150L
private const val MATCH_COLOR = 0x66FFEB3B.toInt()
private const val CURRENT_MATCH_COLOR = 0x66FF9800.toInt()

@Composable
fun MarkdownPreview(
    text: String,
    initialScrollY: Int,
    onScroll: (Int) -> Unit,
    modifier: Modifier = Modifier,
    searchText: String = "",
    searchMatchIndex: Int = -1,
    headingJump: HeadingJump? = null,
) {
    val context = LocalContext.current
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create(context))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.headingBreakHeight(0)
                }
            })
            .build()
    }

    val debouncedText by produceState(initialValue = text, text) {
        delay(PREVIEW_DEBOUNCE_MS)
        value = text
    }

    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    var lastNavTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var lastJump by remember { mutableStateOf<HeadingJump?>(null) }

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
            applySearchHighlights(textView, searchText, searchMatchIndex)

            val jump = headingJump
            if (jump != null && jump != lastJump) {
                lastJump = jump
                val offset = textView.text.toString().indexOf(jump.headingText, ignoreCase = true)
                scrollToOffset(scrollView, textView, offset)
            } else if (searchText.isNotBlank()) {
                val target = searchText to searchMatchIndex
                if (target != lastNavTarget) {
                    lastNavTarget = target
                    val offset = nthOccurrence(
                        textView.text.toString(),
                        searchText,
                        searchMatchIndex,
                    )
                    scrollToOffset(scrollView, textView, offset)
                }
            }
        },
        modifier = modifier,
    )
}

private fun applySearchHighlights(textView: TextView, query: String, matchIndex: Int) {
    val content = textView.text
    if (content !is Spannable) return
    content.getSpans(0, content.length, BackgroundColorSpan::class.java)
        .forEach { content.removeSpan(it) }
    if (query.isBlank()) return
    val haystack = content.toString()
    var index = haystack.indexOf(query, 0, ignoreCase = true)
    var n = 0
    while (index >= 0) {
        content.setSpan(
            BackgroundColorSpan(if (n == matchIndex) CURRENT_MATCH_COLOR else MATCH_COLOR),
            index,
            index + query.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        index = haystack.indexOf(query, index + query.length, ignoreCase = true)
        n++
    }
}

private fun nthOccurrence(haystack: String, needle: String, n: Int): Int {
    if (needle.isBlank() || n < 0) return -1
    var index = haystack.indexOf(needle, 0, ignoreCase = true)
    var count = 0
    while (index >= 0) {
        if (count == n) return index
        index = haystack.indexOf(needle, index + needle.length, ignoreCase = true)
        count++
    }
    return -1
}

private fun scrollToOffset(scrollView: ScrollView, textView: TextView, offset: Int) {
    if (offset < 0) return
    scrollView.post {
        val layout = textView.layout ?: return@post
        val line = layout.getLineForOffset(offset)
        scrollView.smoothScrollTo(0, layout.getLineTop(line))
    }
}
