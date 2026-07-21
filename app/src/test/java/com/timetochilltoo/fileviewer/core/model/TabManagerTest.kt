package com.timetochilltoo.fileviewer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TabManagerTest {

    private fun markdownTab(uri: String?, name: String = "note.md", text: String = "hello") =
        DocumentTab(
            document = ViewerDocument.Markdown(
                uri = uri,
                displayName = name,
                text = text,
                savedText = text,
            ),
        )

    @Test
    fun `duplicate uri is found so the existing tab can be selected`() {
        val manager = TabManager()
        val tab = markdownTab(uri = "content://doc/1")
        manager.add(tab)
        manager.add(markdownTab(uri = "content://doc/2"))

        assertSame(tab, manager.findByUri("content://doc/1"))
        assertNull(manager.findByUri("content://doc/3"))
    }

    @Test
    fun `add selects the new tab`() {
        val manager = TabManager()
        val first = manager.add(markdownTab(uri = "content://doc/1"))
        val second = manager.add(markdownTab(uri = "content://doc/2"))

        assertEquals(second.id, manager.selectedTabId)
        manager.select(first.id)
        assertEquals(first.id, manager.selectedTabId)
    }

    @Test
    fun `closing the selected tab selects its right neighbour`() {
        val manager = TabManager()
        manager.add(markdownTab(uri = "content://doc/1"))
        val middle = manager.add(markdownTab(uri = "content://doc/2"))
        val last = manager.add(markdownTab(uri = "content://doc/3"))
        manager.select(middle.id)

        manager.remove(middle.id)
        assertEquals(last.id, manager.selectedTabId)
    }

    @Test
    fun `closing the last tab clears the selection`() {
        val manager = TabManager()
        val tab = manager.add(markdownTab(uri = "content://doc/1"))

        manager.remove(tab.id)
        assertNull(manager.selectedTabId)
        assertTrue(manager.tabs.isEmpty())
    }

    @Test
    fun `closing a non-selected tab keeps the selection`() {
        val manager = TabManager()
        val first = manager.add(markdownTab(uri = "content://doc/1"))
        val second = manager.add(markdownTab(uri = "content://doc/2"))
        manager.select(first.id)

        manager.remove(second.id)
        assertEquals(first.id, manager.selectedTabId)
    }

    @Test
    fun `markdown is dirty only when text differs from saved text`() {
        val clean = markdownTab(uri = "content://doc/1")
        assertFalse(clean.hasUnsavedChanges)
        assertFalse(TabManager.needsCloseConfirmation(clean))

        val dirty = clean.copy(
            document = (clean.document as ViewerDocument.Markdown).copy(text = "changed"),
        )
        assertTrue(dirty.hasUnsavedChanges)
        assertTrue(TabManager.needsCloseConfirmation(dirty))
    }

    @Test
    fun `new untitled empty document is not dirty`() {
        val untitled = DocumentTab(
            document = ViewerDocument.Markdown(
                uri = null,
                displayName = "Untitled",
                text = "",
                savedText = "",
            ),
        )
        assertFalse(untitled.hasUnsavedChanges)
    }

    @Test
    fun `untitled with content is dirty`() {
        val untitled = DocumentTab(
            document = ViewerDocument.Markdown(
                uri = null,
                displayName = "Untitled",
                text = "shared text",
                savedText = "",
            ),
        )
        assertTrue(untitled.hasUnsavedChanges)
    }

    @Test
    fun `saved markdown is clean again`() {
        val tab = markdownTab(uri = "content://doc/1")
        val dirty = tab.copy(
            document = (tab.document as ViewerDocument.Markdown).copy(text = "changed"),
        )
        val saved = dirty.copy(
            document = (dirty.document as ViewerDocument.Markdown)
                .copy(savedText = dirty.document.text),
        )
        assertFalse(saved.hasUnsavedChanges)
    }
}
