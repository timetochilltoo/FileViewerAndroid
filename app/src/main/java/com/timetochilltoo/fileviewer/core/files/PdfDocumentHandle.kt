package com.timetochilltoo.fileviewer.core.files

import android.graphics.Bitmap
import android.graphics.RectF
import com.timetochilltoo.fileviewer.core.model.PdfOutlineItem
import com.timetochilltoo.fileviewer.core.model.PdfPageMetrics
import com.timetochilltoo.fileviewer.core.model.PdfSearchResult
import java.io.Closeable

interface PdfDocumentHandle : Closeable {

    val pageCount: Int

    fun pageMetrics(pageIndex: Int): PdfPageMetrics?

    /**
     * Renders [pageIndex] into [bitmap]. The bitmap must be [width] x [height] pixels.
     * Returns true if rendering succeeded.
     */
    fun renderPage(
        pageIndex: Int,
        bitmap: Bitmap,
        width: Int,
        height: Int,
        renderAnnotations: Boolean = false,
    ): Boolean

    /**
     * Renders a thumbnail of [pageIndex] no wider than [maxWidth] pixels.
     * The returned bitmap must be recycled by the caller.
     */
    fun renderThumbnail(pageIndex: Int, maxWidth: Int): Bitmap?

    /**
     * Extracts the text of the given page. Returns an empty string if text is unavailable.
     */
    fun extractText(pageIndex: Int): String

    /**
     * Searches the entire document for [query]. Matches are returned in page order,
     * each with the list of bounding rectangles on that page.
     */
    fun search(query: String): List<PdfSearchResult>

    /**
     * Returns the PDF outline/table-of-contents, if any.
     */
    fun outline(): List<PdfOutlineItem>

    /**
     * Converts a rectangle in page coordinates (points, origin bottom-left) to a
     * bitmap-relative rectangle (origin top-left) for a page rendered at the given scale.
     */
    fun mapRectToBitmap(rect: RectF, pageWidth: Int, pageHeight: Int): RectF
}
