package com.timetochilltoo.fileviewer.feature.pdf

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timetochilltoo.fileviewer.core.files.PdfDocumentHandle
import com.timetochilltoo.fileviewer.core.model.DocumentTab
import com.timetochilltoo.fileviewer.core.model.PdfPageMetrics
import com.timetochilltoo.fileviewer.core.model.PdfScaleMode
import com.timetochilltoo.fileviewer.core.model.ViewerDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun PdfWorkspace(
    tab: DocumentTab,
    viewportWidth: Int,
    viewportHeight: Int,
    pdfPageJump: Pair<String, Int>?,
    onConsumePageJump: () -> Unit,
    onPageChange: (Int) -> Unit,
    onScaleChange: (Float) -> Unit,
    onScaleModeChange: (PdfScaleMode) -> Unit,
    onReadingModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pdf = tab.document as? ViewerDocument.Pdf ?: return
    val handle = pdf.handle ?: return

    val metricsList by produceState<List<PdfPageMetrics?>>(
        initialValue = emptyList(),
        key1 = handle,
    ) {
        value = withContext(Dispatchers.IO) {
            List(pdf.pageCount) { index -> handle.pageMetrics(index) }
        }
    }

    val maxWidthPoints = remember(metricsList) {
        metricsList.maxOfOrNull { it?.widthPoints ?: 0 } ?: 0
    }
    val maxHeightPoints = remember(metricsList) {
        metricsList.maxOfOrNull { it?.heightPoints ?: 0 } ?: 0
    }
    val fitWidthScale = if (maxWidthPoints > 0) viewportWidth / maxWidthPoints.toFloat() else 1f
    val fitPageScale = if (maxWidthPoints > 0 && maxHeightPoints > 0) {
        kotlin.math.min(viewportWidth / maxWidthPoints.toFloat(), viewportHeight / maxHeightPoints.toFloat())
    } else {
        1f
    }

    val targetScale = when (tab.pdfScaleMode) {
        PdfScaleMode.FIT_WIDTH -> fitWidthScale
        PdfScaleMode.FIT_PAGE -> fitPageScale
        PdfScaleMode.FREE -> tab.pdfScale
    }.coerceAtLeast(0.1f)

    var scale by remember { mutableStateOf(targetScale) }
    LaunchedEffect(targetScale) {
        scale = targetScale
    }

    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index -> onPageChange(index) }
    }

    LaunchedEffect(pdfPageJump) {
        val jump = pdfPageJump ?: return@LaunchedEffect
        if (jump.first != tab.id) return@LaunchedEffect
        val page = jump.second.coerceIn(0, pdf.pageCount - 1)
        listState.animateScrollToItem(page)
        onConsumePageJump()
    }

    Column(modifier = modifier.fillMaxSize()) {
        PdfToolbar(
            page = tab.pdfPage + 1,
            pageCount = pdf.pageCount,
            scale = scale,
            onPageChange = { onPageChange((it - 1).coerceIn(0, pdf.pageCount - 1)) },
            onPrevious = { onPageChange((tab.pdfPage - 1).coerceIn(0, pdf.pageCount - 1)) },
            onNext = { onPageChange((tab.pdfPage + 1).coerceIn(0, pdf.pageCount - 1)) },
            onZoomIn = { scale = (scale * 1.25f).coerceAtLeast(0.1f); onScaleChange(scale) },
            onZoomOut = { scale = (scale / 1.25f).coerceAtLeast(0.1f); onScaleChange(scale) },
        )

        if (tab.pdfReadingMode) {
            PdfReadingMode(text = tab.pdfReflowText, modifier = Modifier.fillMaxSize())
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    count = pdf.pageCount,
                    key = { it },
                ) { pageIndex ->
                    val currentMatch = if (tab.searchMatchIndex in tab.pdfSearchResults.indices) {
                        tab.pdfSearchResults[tab.searchMatchIndex]
                    } else {
                        null
                    }
                    PdfPageView(
                        handle = handle,
                        pageIndex = pageIndex,
                        scale = scale,
                        searchResults = tab.pdfSearchResults.filter { it.pageIndex == pageIndex },
                        currentMatch = if (currentMatch?.pageIndex == pageIndex) currentMatch else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfToolbar(
    page: Int,
    pageCount: Int,
    scale: Float,
    onPageChange: (Int) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
) {
    var pageField by remember(page) { mutableStateOf(page.toString()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrevious, enabled = page > 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous page")
            }
            OutlinedTextField(
                value = pageField,
                onValueChange = { pageField = it.filter { ch -> ch.isDigit() } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(
                    onGo = { pageField.toIntOrNull()?.let { onPageChange(it) } },
                ),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                modifier = Modifier.width(72.dp),
                singleLine = true,
                label = { Text("${page}/${pageCount}") },
            )
            IconButton(onClick = onNext, enabled = page < pageCount) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next page")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${(scale * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            TextButton(onClick = onZoomOut) { Text("−") }
            TextButton(onClick = onZoomIn) { Text("+") }
        }
    }
}

@Composable
private fun PdfReadingMode(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        ) {
            item {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 28.sp,
                )
            }
        }
    }
}
