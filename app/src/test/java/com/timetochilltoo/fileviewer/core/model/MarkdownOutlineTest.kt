package com.timetochilltoo.fileviewer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownOutlineTest {

    @Test
    fun `headings are extracted with levels in order`() {
        val text = "# Title\n\nSome text\n\n## Section A\nbody\n### Sub\n"
        val headings = MarkdownOutline.headings(text)
        assertEquals(
            listOf(
                MarkdownHeading(1, "Title"),
                MarkdownHeading(2, "Section A"),
                MarkdownHeading(3, "Sub"),
            ),
            headings,
        )
    }

    @Test
    fun `headings inside fenced code blocks are ignored`() {
        val text = "# Real\n\n```\n# not a heading\n```\n"
        val headings = MarkdownOutline.headings(text)
        assertEquals(listOf(MarkdownHeading(1, "Real")), headings)
    }

    @Test
    fun `document without headings returns empty list`() {
        assertEquals(emptyList<MarkdownHeading>(), MarkdownOutline.headings("plain\ntext"))
    }
}
