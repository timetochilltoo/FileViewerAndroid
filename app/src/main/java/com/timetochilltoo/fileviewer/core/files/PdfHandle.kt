package com.timetochilltoo.fileviewer.core.files

import android.graphics.Bitmap
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.util.Log
import com.timetochilltoo.fileviewer.core.model.PdfOutlineItem
import com.timetochilltoo.fileviewer.core.model.PdfPageMetrics
import com.timetochilltoo.fileviewer.core.model.PdfSearchResult
import io.legere.pdfiumandroid.FindFlags
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfPage
import io.legere.pdfiumandroid.PdfTextPage
import io.legere.pdfiumandroid.PdfiumCore
import java.io.Closeable
import java.util.EnumSet

private const val TAG = "PdfHandle"
private const val MAX_CACHED_PAGES = 4

class PdfHandle(
    private val fileDescriptor: ParcelFileDescriptor,
    private val document: PdfDocument,
) : PdfDocumentHandle, Closeable {

    @Volatile
    private var isClosed = false
        private set

    private val pageCache = PageCache(document, MAX_CACHED_PAGES)

    override val pageCount: Int
        get() = document.getPageCount()

    override fun pageMetrics(pageIndex: Int): PdfPageMetrics? = runOnPage(pageIndex) {
        val page = pageCache.page(pageIndex) ?: return@runOnPage null
        PdfPageMetrics(
            pageIndex = pageIndex,
            widthPoints = page.getPageWidthPoint(),
            heightPoints = page.getPageHeightPoint(),
            rotation = page.getPageRotation(),
        )
    }

    override fun renderPage(
        pageIndex: Int,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        renderAnnotations: Boolean,
    ): Boolean = runOnPage(pageIndex) {
        bitmap.eraseColor(0xFFFFFFFF.toInt())
        val page = pageCache.page(pageIndex) ?: return@runOnPage false
        page.renderPageBitmap(bitmap, 0, 0, width, height, renderAnnotations, true)
        true
    } ?: false

    override fun renderThumbnail(pageIndex: Int, maxWidth: Int): Bitmap? = runOnPage(pageIndex) {
        val page = pageCache.page(pageIndex) ?: return@runOnPage null
        val pageWidth = page.getPageWidthPoint().toFloat()
        val pageHeight = page.getPageHeightPoint().toFloat()
        if (pageWidth <= 0f || pageHeight <= 0f) return@runOnPage null
        val scale = maxWidth / pageWidth
        val width = maxWidth
        val height = (pageHeight * scale).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFFFFFFFF.toInt())
        page.renderPageBitmap(bitmap, 0, 0, width, height, false, true)
        bitmap
    }

    override fun extractText(pageIndex: Int): String = runOnPage(pageIndex) {
        val textPage = pageCache.textPage(pageIndex) ?: return@runOnPage ""
        textPage.textPageGetText(0, textPage.textPageCountChars())
    } ?: ""

    override fun search(query: String): List<PdfSearchResult> {
        if (query.isBlank()) return emptyList()
        synchronized(this) {
            if (isClosed) return emptyList()
            val results = mutableListOf<PdfSearchResult>()
            try {
                for (pageIndex in 0 until pageCount) {
                    val textPage = pageCache.textPage(pageIndex) ?: continue
                    val findResult = textPage.findStart(
                        query,
                        EnumSet.noneOf(FindFlags::class.java),
                        0,
                    ) ?: continue
                    try {
                        do {
                            val start = findResult.getSchResultIndex()
                            val count = findResult.getSchCount()
                            if (count <= 0) continue
                            val rectCount = textPage.textPageCountRects(start, count)
                            val rects = (0 until rectCount)
                                .mapNotNull { textPage.textPageGetRect(it) }
                                .filter { !it.isEmpty }
                            if (rects.isNotEmpty()) {
                                results.add(
                                    PdfSearchResult(
                                        pageIndex = pageIndex,
                                        matchIndex = results.size,
                                        pageCharStart = start,
                                        pageCharCount = count,
                                        rects = rects,
                                    ),
                                )
                            }
                        } while (findResult.findNext())
                    } finally {
                        findResult.close()
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "search failed", e)
            }
            return results
        }
    }

    override fun outline(): List<PdfOutlineItem> {
        synchronized(this) {
            if (isClosed) return emptyList()
            return try {
                document.getTableOfContents().map { it.toOutlineItem(depth = 0) }
            } catch (e: Throwable) {
                Log.w(TAG, "outline failed", e)
                emptyList()
            }
        }
    }

    override fun mapRectToBitmap(rect: RectF, pageWidth: Int, pageHeight: Int): RectF {
        return RectF(
            rect.left,
            pageHeight - rect.top,
            rect.right,
            pageHeight - rect.bottom,
        )
    }

    override fun close() {
        synchronized(this) {
            if (isClosed) return
            isClosed = true
            pageCache.close()
            runCatching { document.close() }
            runCatching { fileDescriptor.close() }
        }
    }

    private inline fun <T : Any> runOnPage(pageIndex: Int, block: () -> T?): T? =
        synchronized(this) {
            if (isClosed || pageIndex < 0 || pageIndex >= pageCount) return null
            try {
                block()
            } catch (e: Throwable) {
                Log.w(TAG, "operation failed for page $pageIndex", e)
                null
            }
        }

    private class PageCache(
        private val document: PdfDocument,
        private val maxSize: Int,
    ) : Closeable {

        private data class Entry(
            val page: PdfPage,
            val textPage: PdfTextPage?,
        )

        private val map = LinkedHashMap<Int, Entry>(maxSize + 1, 0.75f, true)

        @Synchronized
        fun page(pageIndex: Int): PdfPage? {
            val entry = map[pageIndex] ?: open(pageIndex) ?: return null
            return entry.page
        }

        @Synchronized
        fun textPage(pageIndex: Int): PdfTextPage? {
            val entry = map[pageIndex] ?: open(pageIndex) ?: return null
            return entry.textPage
        }

        private fun open(pageIndex: Int): Entry? {
            trimIfNeeded()
            val page = try {
                document.openPage(pageIndex)
            } catch (e: Throwable) {
                Log.w(TAG, "openPage failed for $pageIndex", e)
                return null
            }
            val textPage = try {
                page.openTextPage()
            } catch (e: Throwable) {
                Log.w(TAG, "openTextPage failed for $pageIndex", e)
                null
            }
            val entry = Entry(page, textPage)
            map[pageIndex] = entry
            return entry
        }

        private fun trimIfNeeded() {
            while (map.size >= maxSize) {
                val oldest = map.entries.first().key
                map.remove(oldest)?.close()
            }
        }

        @Synchronized
        override fun close() {
            map.values.forEach { it.close() }
            map.clear()
        }

        private fun Entry.close() {
            runCatching { textPage?.close() }
            runCatching { page.close() }
        }
    }
}

private fun PdfDocument.Bookmark.toOutlineItem(depth: Int): PdfOutlineItem =
    PdfOutlineItem(
        title = title.orEmpty(),
        pageIndex = pageIdx.toInt().coerceAtLeast(0),
        depth = depth,
        children = children.map { it.toOutlineItem(depth + 1) },
    )

private val RectF.isEmpty: Boolean
    get() = width() <= 0 || height() <= 0
