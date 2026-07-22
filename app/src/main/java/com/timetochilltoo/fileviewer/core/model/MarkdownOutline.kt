package com.timetochilltoo.fileviewer.core.model

data class EditorSelection(
    val tabId: String,
    val start: Int,
    val end: Int,
    val nonce: Long = System.nanoTime(),
)

data class HeadingJump(
    val tabId: String,
    val headingText: String,
    val nonce: Long = System.nanoTime(),
)

data class MarkdownHeading(val level: Int, val text: String)

object MarkdownOutline {

    private val headingRegex = Regex("^(#{1,6})\\s+(.+?)\\s*$")

    fun headings(markdown: String): List<MarkdownHeading> {
        var inFence = false
        return markdown.lines().mapNotNull { line ->
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
                return@mapNotNull null
            }
            if (inFence) return@mapNotNull null
            val match = headingRegex.find(line) ?: return@mapNotNull null
            MarkdownHeading(level = match.groupValues[1].length, text = match.groupValues[2])
        }
    }
}
