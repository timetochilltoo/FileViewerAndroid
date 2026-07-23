package com.timetochilltoo.fileviewer.feature.pdf

import android.annotation.SuppressLint
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.timetochilltoo.fileviewer.core.files.PdfDocumentHandle
import com.timetochilltoo.fileviewer.core.model.PdfPageMetrics
import com.timetochilltoo.fileviewer.core.model.PdfSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@SuppressLint("ProduceStateDoesNotAssignValue")
@Composable
fun PdfPageView(
    handle: PdfDocumentHandle,
    pageIndex: Int,
    scale: Float,
    searchResults: List<PdfSearchResult>,
    currentMatch: PdfSearchResult?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val metrics by produceState<PdfPageMetrics?>(initialValue = null, handle, pageIndex) {
        value = withContext(Dispatchers.IO) { handle.pageMetrics(pageIndex) }
    }

    if (metrics == null) {
        Box(modifier = modifier)
        return
    }

    val pageWidthPx = (metrics!!.widthPoints * scale).toInt().coerceAtLeast(1)
    val pageHeightPx = (metrics!!.heightPoints * scale).toInt().coerceAtLeast(1)
    val widthDp = with(density) { pageWidthPx.toDp() }
    val heightDp = with(density) { pageHeightPx.toDp() }

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(pageIndex) {
        onDispose { bitmap?.recycle() }
    }

    LaunchedEffect(handle, pageIndex, scale) {
        val newBitmap = Bitmap.createBitmap(pageWidthPx, pageHeightPx, Bitmap.Config.ARGB_8888)
        val success = withContext(Dispatchers.IO) {
            handle.renderPage(pageIndex, newBitmap, pageWidthPx, pageHeightPx)
        }
        if (success) {
            bitmap?.recycle()
            bitmap = newBitmap
        } else {
            newBitmap.recycle()
        }
    }

    Box(
        modifier = modifier.size(widthDp, heightDp),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "PDF page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
            )
        } ?: Text(
            text = "Loading…",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.align(Alignment.Center),
        )

        val matchColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f).toArgb()
        val currentMatchColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f).toArgb()
        val strokeColor = MaterialTheme.colorScheme.primary.toArgb()

        Canvas(modifier = Modifier.fillMaxSize()) {
            val resultsOnPage = searchResults.filter { it.pageIndex == pageIndex }
            for (result in resultsOnPage) {
                val color = if (result == currentMatch) currentMatchColor else matchColor
                for (rect in result.rects) {
                    val left = rect.left.coerceIn(0f, metrics!!.widthPoints.toFloat())
                    val right = rect.right.coerceIn(0f, metrics!!.widthPoints.toFloat())
                    val top = metrics!!.heightPoints - rect.top.coerceIn(0f, metrics!!.heightPoints.toFloat())
                    val bottom = metrics!!.heightPoints - rect.bottom.coerceIn(0f, metrics!!.heightPoints.toFloat())
                    val drawLeft = left * scale
                    val drawTop = top * scale
                    val drawRight = right * scale
                    val drawBottom = bottom * scale
                    if (drawRight <= drawLeft || drawBottom <= drawTop) continue
                    drawRect(
                        color = Color(color),
                        topLeft = Offset(drawLeft, drawTop),
                        size = Size(drawRight - drawLeft, drawBottom - drawTop),
                        style = Fill,
                    )
                    if (result == currentMatch) {
                        drawRect(
                            color = Color(strokeColor),
                            topLeft = Offset(drawLeft, drawTop),
                            size = Size(drawRight - drawLeft, drawBottom - drawTop),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}
