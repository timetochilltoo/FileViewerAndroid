package com.timetochilltoo.fileviewer.core.files

data class RecentDocument(
    val uri: String,
    val displayName: String,
    val lastOpenedEpochMs: Long,
)
