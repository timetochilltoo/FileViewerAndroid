package com.timetochilltoo.fileviewer.core.model

enum class MarkdownFormatCommand(val label: String) {
    BOLD("Bold"),
    ITALIC("Italic"),
    UNDERLINE("HTML Underline"),
    HEADING("Heading"),
    BULLET_LIST("Bullet List"),
    NUMBERED_LIST("Numbered List"),
    QUOTE("Quote"),
    LINK("Link"),
    CODE("Code"),
    TABLE("Insert Table"),
    TASK_LIST("Task List"),
}
