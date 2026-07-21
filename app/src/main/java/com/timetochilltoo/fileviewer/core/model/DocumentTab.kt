package com.timetochilltoo.fileviewer.core.model

import android.net.Uri
import java.util.UUID

data class DocumentTab(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri?,
    val displayName: String,
    val kind: DocumentKind,
    val searchText: String = "",
    val searchMatchIndex: Int = 0,
    val searchMatchCount: Int = 0,
    val pdfPage: Int = 0,
    val pdfPageCount: Int = 0,
    val pdfScale: Float = 1f,
    val markdownScrollY: Int = 0,
    val hasUnsavedChanges: Boolean = false,
)
