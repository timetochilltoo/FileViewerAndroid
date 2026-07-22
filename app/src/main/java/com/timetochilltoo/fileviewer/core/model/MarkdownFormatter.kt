package com.timetochilltoo.fileviewer.core.model

data class FormatResult(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

/**
 * Pure port of the macOS FileViewer markdown toggle logic.
 * All indices are Kotlin String indices (UTF-16 code units), which matches
 * how the editor's TextFieldValue selection is reported.
 */
object MarkdownFormatter {

    fun apply(
        command: MarkdownFormatCommand,
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
    ): FormatResult {
        val start = selectionStart.coerceIn(0, text.length)
        val end = selectionEnd.coerceIn(start, text.length)
        return when (command) {
            MarkdownFormatCommand.BOLD ->
                toggleInline(text, start, end, "**", "**", "bold text")

            MarkdownFormatCommand.ITALIC -> toggleItalic(text, start, end)

            MarkdownFormatCommand.UNDERLINE ->
                toggleInline(text, start, end, "<u>", "</u>", "underlined text")

            MarkdownFormatCommand.CODE -> toggleCode(text, start, end)

            MarkdownFormatCommand.LINK -> applyLink(text, start, end)

            MarkdownFormatCommand.HEADING -> toggleLines(text, start, end, ::headingTransform)

            MarkdownFormatCommand.BULLET_LIST ->
                toggleLines(text, start, end, ::bulletTransform)

            MarkdownFormatCommand.NUMBERED_LIST ->
                toggleLines(text, start, end, ::numberedTransform)

            MarkdownFormatCommand.QUOTE -> toggleLines(text, start, end, ::quoteTransform)

            MarkdownFormatCommand.TABLE -> applyTable(text, start, end)

            MarkdownFormatCommand.TASK_LIST -> applyTaskList(text, start, end)
        }
    }

    private fun toggleInline(
        text: String,
        start: Int,
        end: Int,
        prefix: String,
        suffix: String,
        placeholder: String,
    ): FormatResult {
        val selected = text.substring(start, end)

        if (selected.isEmpty()) {
            val insertion = prefix + placeholder + suffix
            val newText = text.replaceRange(start, end, insertion)
            val innerStart = start + prefix.length
            return FormatResult(newText, innerStart, innerStart + placeholder.length)
        }

        if (selected.length >= prefix.length + suffix.length &&
            selected.startsWith(prefix) && selected.endsWith(suffix)
        ) {
            val inner = selected.removePrefix(prefix).removeSuffix(suffix)
            val newText = text.replaceRange(start, end, inner)
            return FormatResult(newText, start, start + inner.length)
        }

        val beforeHasMarker = start >= prefix.length &&
            text.substring(start - prefix.length, start) == prefix
        val afterHasMarker = end + suffix.length <= text.length &&
            text.substring(end, end + suffix.length) == suffix
        if (beforeHasMarker && afterHasMarker) {
            val newText = text
                .replaceRange(end, end + suffix.length, "")
                .replaceRange(start - prefix.length, start, "")
            return FormatResult(newText, start - prefix.length, end - prefix.length)
        }

        val newText = text.replaceRange(start, end, prefix + selected + suffix)
        return FormatResult(newText, start + prefix.length, end + prefix.length)
    }

    private fun toggleItalic(text: String, start: Int, end: Int): FormatResult {
        val selected = text.substring(start, end)

        if (selected.isEmpty()) {
            val insertion = "*italic text*"
            val newText = text.replaceRange(start, end, insertion)
            return FormatResult(newText, start + 1, start + 1 + "italic text".length)
        }

        if (selected.length >= 2 &&
            selected.startsWith("*") && selected.endsWith("*") &&
            !selected.startsWith("**") && !selected.endsWith("**")
        ) {
            val inner = selected.substring(1, selected.length - 1)
            val newText = text.replaceRange(start, end, inner)
            return FormatResult(newText, start, start + inner.length)
        }

        val beforeSingle = start >= 1 && text[start - 1] == '*' &&
            !(start >= 2 && text[start - 2] == '*')
        val afterSingle = end < text.length && text[end] == '*' &&
            !(end + 1 < text.length && text[end + 1] == '*')
        if (beforeSingle && afterSingle) {
            val newText = text
                .replaceRange(end, end + 1, "")
                .replaceRange(start - 1, start, "")
            return FormatResult(newText, start - 1, end - 1)
        }

        val newText = text.replaceRange(start, end, "*$selected*")
        return FormatResult(newText, start + 1, end + 1)
    }

    private fun toggleCode(text: String, start: Int, end: Int): FormatResult {
        val selected = text.substring(start, end)
        if (!selected.contains('\n')) {
            return toggleInline(text, start, end, "`", "`", "code")
        }
        val trimmed = selected.trim()
        if (trimmed.startsWith("```") && trimmed.endsWith("```") && trimmed.length > 6) {
            val inner = trimmed
                .removePrefix("```")
                .removeSuffix("```")
                .trim('\n')
            val newText = text.replaceRange(start, end, inner)
            return FormatResult(newText, start, start + inner.length)
        }
        val fenceBeforeLen = when {
            start >= 4 && text.substring(start - 4, start) == "```\n" -> 4
            start >= 3 && text.substring(start - 3, start) == "```" -> 3
            else -> 0
        }
        val fenceAfterLen = when {
            end + 4 <= text.length && text.substring(end, end + 4) == "\n```" -> 4
            end + 3 <= text.length && text.substring(end, end + 3) == "```" -> 3
            else -> 0
        }
        if (fenceBeforeLen > 0 && fenceAfterLen > 0) {
            val newText = text
                .replaceRange(end, end + fenceAfterLen, "")
                .replaceRange(start - fenceBeforeLen, start, "")
            return FormatResult(
                newText,
                start - fenceBeforeLen,
                end - fenceBeforeLen,
            )
        }
        val wrapped = "```\n$selected\n```"
        val newText = text.replaceRange(start, end, wrapped)
        return FormatResult(newText, start + 4, start + 4 + selected.length)
    }

    private fun applyLink(text: String, start: Int, end: Int): FormatResult {
        val selected = text.substring(start, end)
        if (selected.isEmpty()) {
            val insertion = "[link text](https://example.com)"
            val newText = text.replaceRange(start, end, insertion)
            return FormatResult(newText, start + 1, start + 1 + "link text".length)
        }
        val replacement = "[$selected](https://example.com)"
        val newText = text.replaceRange(start, end, replacement)
        return FormatResult(newText, start + 1, start + 1 + selected.length)
    }

    private fun lineRange(text: String, start: Int, end: Int): Pair<Int, Int> {
        val lineStart = if (start <= 0) {
            0
        } else {
            val prevNewline = text.lastIndexOf('\n', start - 1)
            if (prevNewline >= 0) prevNewline + 1 else 0
        }
        val newlineAfterEnd = text.indexOf('\n', end)
        val lineEnd = if (newlineAfterEnd >= 0) newlineAfterEnd else text.length
        return lineStart to lineEnd
    }

    private fun toggleLines(
        text: String,
        start: Int,
        end: Int,
        transform: (List<String>) -> List<String>,
    ): FormatResult {
        val (lineStart, lineEnd) = lineRange(text, start, end)
        val lines = text.substring(lineStart, lineEnd).split('\n')
        val newBlock = transform(lines).joinToString("\n")
        val newText = text.replaceRange(lineStart, lineEnd, newBlock)
        return FormatResult(newText, lineStart, lineStart + newBlock.length)
    }

    private val headingRegex = Regex("^#{1,6}\\s+")

    private fun headingTransform(lines: List<String>): List<String> {
        val nonBlank = lines.filter { it.isNotBlank() }
        val allHeadings = nonBlank.isNotEmpty() && nonBlank.all { headingRegex.containsMatchIn(it) }
        return lines.map { line ->
            when {
                line.isBlank() -> line
                allHeadings -> headingRegex.replaceFirst(line, "")
                else -> "## " + headingRegex.replaceFirst(line, "")
            }
        }
    }

    private fun bulletTransform(lines: List<String>): List<String> {
        val nonBlank = lines.filter { it.isNotBlank() }
        val allBullets = nonBlank.isNotEmpty() && nonBlank.all { it.startsWith("- ") }
        return lines.map { line ->
            when {
                line.isBlank() -> line
                allBullets -> line.removePrefix("- ")
                else -> "- $line"
            }
        }
    }

    private val numberedRegex = Regex("^\\d+\\.\\s+")

    private fun numberedTransform(lines: List<String>): List<String> {
        val nonBlank = lines.filter { it.isNotBlank() }
        val allNumbered = nonBlank.isNotEmpty() && nonBlank.all { numberedRegex.containsMatchIn(it) }
        var counter = 1
        return lines.map { line ->
            when {
                line.isBlank() -> line
                allNumbered -> numberedRegex.replaceFirst(line, "")
                else -> {
                    val stripped = numberedRegex.replaceFirst(line, "")
                    "${counter++}. $stripped"
                }
            }
        }
    }

    private fun quoteTransform(lines: List<String>): List<String> {
        val nonBlank = lines.filter { it.isNotBlank() }
        val allQuotes = nonBlank.isNotEmpty() && nonBlank.all { it.startsWith("> ") }
        return lines.map { line ->
            when {
                line.isBlank() -> line
                allQuotes -> line.removePrefix("> ")
                else -> "> $line"
            }
        }
    }

    private fun applyTable(text: String, start: Int, end: Int): FormatResult {
        val selected = text.substring(start, end)
        val template = "| Column 1 | Column 2 | Column 3 |\n| --- | --- | --- |\n|  |  |  |"
        if (selected.isBlank()) {
            val newText = text.replaceRange(start, end, template)
            return FormatResult(newText, start + 2, start + 2 + "Column 1".length)
        }
        val rows = selected.lines()
            .filter { it.isNotBlank() }
            .map { line -> line.split(',').map { it.trim() } }
        if (rows.isEmpty()) {
            val newText = text.replaceRange(start, end, template)
            return FormatResult(newText, start + 2, start + 2 + "Column 1".length)
        }
        val columnCount = rows.maxOf { it.size }
        fun rowLine(cells: List<String>): String =
            "| " + (0 until columnCount).joinToString(" | ") { cells.getOrElse(it) { "" } } + " |"

        val table = buildString {
            append(rowLine(rows.first()))
            append('\n')
            append("| ")
            append(List(columnCount) { "---" }.joinToString(" | "))
            append(" |")
            rows.drop(1).forEach { row ->
                append('\n')
                append(rowLine(row))
            }
        }
        val newText = text.replaceRange(start, end, table)
        return FormatResult(newText, start, start + table.length)
    }

    private val taskRegex = Regex("^- \\[[ xX]\\]\\s+")

    private fun applyTaskList(text: String, start: Int, end: Int): FormatResult {
        val selected = text.substring(start, end)
        val (lineStart, lineEnd) = lineRange(text, start, end)
        val currentLine = text.substring(lineStart, lineEnd)
        if (selected.isEmpty() && currentLine.isBlank()) {
            val insertion = "- [ ] Task item"
            val newText = text.replaceRange(start, end, insertion)
            val innerStart = start + "- [ ] ".length
            return FormatResult(newText, innerStart, innerStart + "Task item".length)
        }
        return toggleLines(text, start, end) { lines ->
            val nonBlank = lines.filter { it.isNotBlank() }
            val allTasks = nonBlank.isNotEmpty() && nonBlank.all { taskRegex.containsMatchIn(it) }
            lines.map { line ->
                when {
                    line.isBlank() -> line
                    allTasks -> taskRegex.replaceFirst(line, "")
                    line.startsWith("- ") -> "- [ ] " + line.removePrefix("- ")
                    else -> "- [ ] $line"
                }
            }
        }
    }
}
