package com.timetochilltoo.fileviewer.core.model

import android.graphics.RectF

data class PdfPageMetrics(
    val pageIndex: Int,
    val widthPoints: Int,
    val heightPoints: Int,
    val rotation: Int,
)

data class PdfSearchResult(
    val pageIndex: Int,
    val matchIndex: Int,
    val pageCharStart: Int,
    val pageCharCount: Int,
    val rects: List<RectF>,
)

data class PdfOutlineItem(
    val title: String,
    val pageIndex: Int,
    val depth: Int,
    val children: List<PdfOutlineItem> = emptyList(),
)
