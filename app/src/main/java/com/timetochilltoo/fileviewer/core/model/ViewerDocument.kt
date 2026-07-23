package com.timetochilltoo.fileviewer.core.model

import com.timetochilltoo.fileviewer.core.files.PdfDocumentHandle

sealed interface ViewerDocument {
    val uri: String?
    val displayName: String

    data class Markdown(
        override val uri: String?,
        override val displayName: String,
        val text: String,
        val savedText: String,
    ) : ViewerDocument

    data class Pdf(
        override val uri: String,
        override val displayName: String,
        val pageCount: Int,
        val handle: PdfDocumentHandle?,
    ) : ViewerDocument
}
