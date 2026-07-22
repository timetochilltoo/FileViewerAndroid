package com.timetochilltoo.fileviewer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownFormatterTest {

    private fun fmt(
        command: MarkdownFormatCommand,
        text: String,
        start: Int,
        end: Int,
    ) = MarkdownFormatter.apply(command, text, start, end)

    private fun selectionOf(text: String, target: String): Pair<Int, Int> {
        val start = text.indexOf(target)
        require(start >= 0) { "target not found: $target" }
        return start to start + target.length
    }

    @Test
    fun `bold wraps selection and selects inner text`() {
        val text = "hello world"
        val (s, e) = selectionOf(text, "hello")
        val result = fmt(MarkdownFormatCommand.BOLD, text, s, e)
        assertEquals("**hello** world", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(7, result.selectionEnd)
    }

    @Test
    fun `bold removes markers outside the selection`() {
        val text = "**hello** world"
        val (s, e) = selectionOf(text, "hello")
        val result = fmt(MarkdownFormatCommand.BOLD, text, s, e)
        assertEquals("hello world", result.text)
        assertEquals(0, result.selectionStart)
        assertEquals(5, result.selectionEnd)
    }

    @Test
    fun `bold removes markers included in the selection`() {
        val text = "say **hello** now"
        val (s, e) = selectionOf(text, "**hello**")
        val result = fmt(MarkdownFormatCommand.BOLD, text, s, e)
        assertEquals("say hello now", result.text)
    }

    @Test
    fun `bold with empty selection inserts placeholder selected`() {
        val result = fmt(MarkdownFormatCommand.BOLD, "", 0, 0)
        assertEquals("**bold text**", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(11, result.selectionEnd)
    }

    @Test
    fun `italic wraps and unwraps without confusing bold`() {
        val text = "**bold** and plain"
        val (s, e) = selectionOf(text, "plain")
        val wrapped = fmt(MarkdownFormatCommand.ITALIC, text, s, e)
        assertEquals("**bold** and *plain*", wrapped.text)

        val (s2, e2) = selectionOf(wrapped.text, "plain")
        val unwrapped = fmt(MarkdownFormatCommand.ITALIC, wrapped.text, s2, e2)
        assertEquals("**bold** and plain", unwrapped.text)
    }

    @Test
    fun `italic on bold text does not strip bold markers`() {
        val text = "**word**"
        val (s, e) = selectionOf(text, "word")
        val result = fmt(MarkdownFormatCommand.ITALIC, text, s, e)
        assertEquals("***word***", result.text)
    }

    @Test
    fun `underline uses html tags and toggles`() {
        val text = "hello"
        val wrapped = fmt(MarkdownFormatCommand.UNDERLINE, text, 0, 5)
        assertEquals("<u>hello</u>", wrapped.text)
        val (s, e) = selectionOf(wrapped.text, "hello")
        val unwrapped = fmt(MarkdownFormatCommand.UNDERLINE, wrapped.text, s, e)
        assertEquals("hello", unwrapped.text)
    }

    @Test
    fun `inline code toggles single line`() {
        val text = "run this"
        val wrapped = fmt(MarkdownFormatCommand.CODE, text, 0, 3)
        assertEquals("`run` this", wrapped.text)
        val (s, e) = selectionOf(wrapped.text, "run")
        val unwrapped = fmt(MarkdownFormatCommand.CODE, wrapped.text, s, e)
        assertEquals("run this", unwrapped.text)
    }

    @Test
    fun `multiline code becomes fenced block and toggles back`() {
        val text = "line1\nline2"
        val wrapped = fmt(MarkdownFormatCommand.CODE, text, 0, text.length)
        assertEquals("```\nline1\nline2\n```", wrapped.text)
        val inner = "line1\nline2"
        val s = wrapped.text.indexOf(inner)
        val unwrapped = fmt(MarkdownFormatCommand.CODE, wrapped.text, s, s + inner.length)
        assertEquals("line1\nline2", unwrapped.text)
    }

    @Test
    fun `link wraps selection and placeholder when empty`() {
        val text = "click here"
        val (s, e) = selectionOf(text, "here")
        val result = fmt(MarkdownFormatCommand.LINK, text, s, e)
        assertEquals("click [here](https://example.com)", result.text)

        val empty = fmt(MarkdownFormatCommand.LINK, "", 0, 0)
        assertEquals("[link text](https://example.com)", empty.text)
        assertEquals(1, empty.selectionStart)
        assertEquals(10, empty.selectionEnd)
    }

    @Test
    fun `heading prefixes lines then strips them`() {
        val text = "alpha\nbeta"
        val headed = fmt(MarkdownFormatCommand.HEADING, text, 0, text.length)
        assertEquals("## alpha\n## beta", headed.text)
        val unheaded = fmt(MarkdownFormatCommand.HEADING, headed.text, 0, headed.text.length)
        assertEquals("alpha\nbeta", unheaded.text)
    }

    @Test
    fun `heading normalizes mixed levels to h2`() {
        val text = "# title\nplain"
        val result = fmt(MarkdownFormatCommand.HEADING, text, 0, text.length)
        assertEquals("## title\n## plain", result.text)
    }

    @Test
    fun `heading applies to current line when selection is a caret`() {
        val text = "first\nsecond\nthird"
        val caret = text.indexOf("second") + 2
        val result = fmt(MarkdownFormatCommand.HEADING, text, caret, caret)
        assertEquals("first\n## second\nthird", result.text)
    }

    @Test
    fun `bullet list toggles per line skipping blanks`() {
        val text = "one\n\ntwo"
        val bulleted = fmt(MarkdownFormatCommand.BULLET_LIST, text, 0, text.length)
        assertEquals("- one\n\n- two", bulleted.text)
        val unbulleted = fmt(MarkdownFormatCommand.BULLET_LIST, bulleted.text, 0, bulleted.text.length)
        assertEquals("one\n\ntwo", unbulleted.text)
    }

    @Test
    fun `numbered list enumerates and renumbers existing numbers`() {
        val text = "one\ntwo"
        val numbered = fmt(MarkdownFormatCommand.NUMBERED_LIST, text, 0, text.length)
        assertEquals("1. one\n2. two", numbered.text)
        val renumbered = fmt(MarkdownFormatCommand.NUMBERED_LIST, "7. a\n9. b\nplain", 0, "7. a\n9. b\nplain".length)
        assertEquals("1. a\n2. b\n3. plain", renumbered.text)
        val stripped = fmt(MarkdownFormatCommand.NUMBERED_LIST, numbered.text, 0, numbered.text.length)
        assertEquals("one\ntwo", stripped.text)
    }

    @Test
    fun `quote toggles per line`() {
        val text = "one\ntwo"
        val quoted = fmt(MarkdownFormatCommand.QUOTE, text, 0, text.length)
        assertEquals("> one\n> two", quoted.text)
        val unquoted = fmt(MarkdownFormatCommand.QUOTE, quoted.text, 0, quoted.text.length)
        assertEquals("one\ntwo", unquoted.text)
    }

    @Test
    fun `table converts comma separated lines`() {
        val text = "Name, Age\nPatrick, 40"
        val result = fmt(MarkdownFormatCommand.TABLE, text, 0, text.length)
        assertEquals(
            "| Name | Age |\n| --- | --- |\n| Patrick | 40 |",
            result.text,
        )
    }

    @Test
    fun `table inserts template on empty selection`() {
        val result = fmt(MarkdownFormatCommand.TABLE, "", 0, 0)
        assertEquals(
            "| Column 1 | Column 2 | Column 3 |\n| --- | --- | --- |\n|  |  |  |",
            result.text,
        )
        assertEquals(2, result.selectionStart)
        assertEquals(10, result.selectionEnd)
    }

    @Test
    fun `task list converts lines and toggles back`() {
        val text = "buy milk\ncall mom"
        val tasked = fmt(MarkdownFormatCommand.TASK_LIST, text, 0, text.length)
        assertEquals("- [ ] buy milk\n- [ ] call mom", tasked.text)
        val untasked = fmt(MarkdownFormatCommand.TASK_LIST, tasked.text, 0, tasked.text.length)
        assertEquals("buy milk\ncall mom", untasked.text)
    }

    @Test
    fun `task list inserts template on empty blank line`() {
        val result = fmt(MarkdownFormatCommand.TASK_LIST, "", 0, 0)
        assertEquals("- [ ] Task item", result.text)
        assertEquals(6, result.selectionStart)
        assertEquals(15, result.selectionEnd)
    }

    @Test
    fun `non ascii indices stay consistent`() {
        val text = "標題文字"
        val result = fmt(MarkdownFormatCommand.BOLD, text, 0, text.length)
        assertEquals("**標題文字**", result.text)
        val (s, e) = selectionOf(result.text, "標題文字")
        val unwrapped = fmt(MarkdownFormatCommand.BOLD, result.text, s, e)
        assertEquals("標題文字", unwrapped.text)
    }
}
