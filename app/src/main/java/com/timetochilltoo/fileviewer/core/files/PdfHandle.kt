package com.timetochilltoo.fileviewer.core.files

import android.os.ParcelFileDescriptor
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import java.io.Closeable

class PdfHandle(
    private val core: PdfiumCore,
    val fileDescriptor: ParcelFileDescriptor,
    val document: PdfDocument,
) : Closeable {

    @Volatile
    var isClosed = false
        private set

    override fun close() {
        if (isClosed) return
        isClosed = true
        runCatching { core.closeDocument(document) }
        runCatching { fileDescriptor.close() }
    }
}
