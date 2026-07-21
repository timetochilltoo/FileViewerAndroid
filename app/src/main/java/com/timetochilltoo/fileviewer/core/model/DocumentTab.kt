package com.timetochilltoo.fileviewer.core.model

import java.util.UUID

data class DocumentTab(
    val id: String = UUID.randomUUID().toString(),
    val document: ViewerDocument,
    val searchText: String = "",
    val searchMatchIndex: Int = 0,
    val searchMatchCount: Int = 0,
    val pdfPage: Int = 0,
    val pdfScale: Float = 1f,
    val markdownScrollY: Int = 0,
    val pdfDirty: Boolean = false,
) {
    val hasUnsavedChanges: Boolean
        get() = when (val doc = document) {
            is ViewerDocument.Markdown -> doc.text != doc.savedText
            is ViewerDocument.Pdf -> pdfDirty
        }
}
