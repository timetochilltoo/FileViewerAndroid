package com.timetochilltoo.fileviewer.core.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.timetochilltoo.fileviewer.core.model.DocumentKind
import com.timetochilltoo.fileviewer.core.model.ViewerDocument
import io.legere.pdfiumandroid.PdfiumCore

class DocumentRepository(private val context: Context) {

    private val pdfiumCore by lazy { PdfiumCore() }

    fun takePersistablePermission(uriString: String) {
        val uri = Uri.parse(uriString)
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
    }

    fun displayName(uri: Uri): String? {
        runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) {
                        return cursor.getString(0)
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    fun kindFor(displayName: String): DocumentKind? {
        val lower = displayName.lowercase()
        return when {
            lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt") ->
                DocumentKind.MARKDOWN
            lower.endsWith(".pdf") -> DocumentKind.PDF
            else -> null
        }
    }

    fun load(uriString: String): ViewerDocument? {
        takePersistablePermission(uriString)
        val uri = Uri.parse(uriString)
        val name = displayName(uri) ?: "Document"
        return when (kindFor(name)) {
            DocumentKind.MARKDOWN -> loadMarkdown(uriString, uri, name)
            DocumentKind.PDF -> loadPdf(uriString, uri, name)
            null -> null
        }
    }

    private fun loadMarkdown(uriString: String, uri: Uri, name: String): ViewerDocument? =
        runCatching {
            val text = context.contentResolver
                .openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: return null
            ViewerDocument.Markdown(
                uri = uriString,
                displayName = name,
                text = text,
                savedText = text,
            )
        }.onFailure {
            android.util.Log.w("FileViewer", "loadMarkdown failed for $uriString", it)
        }.getOrNull()

    private fun loadPdf(uriString: String, uri: Uri, name: String): ViewerDocument? =
        runCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val document = pdfiumCore.newDocument(pfd)
            pdfiumCore.getPageCount(document)
            ViewerDocument.Pdf(
                uri = uriString,
                displayName = name,
                pageCount = document.getPageCount(),
                handle = PdfHandle(pdfiumCore, pfd, document),
            )
        }.onFailure {
            android.util.Log.w("FileViewer", "loadPdf failed for $uriString", it)
        }.getOrNull()

    fun writeMarkdown(uriString: String, text: String): Boolean = runCatching {
        context.contentResolver
            .openOutputStream(Uri.parse(uriString), "wt")
            ?.bufferedWriter(Charsets.UTF_8)
            ?.use { it.write(text) }
            ?: return false
        true
    }.getOrDefault(false)
}
