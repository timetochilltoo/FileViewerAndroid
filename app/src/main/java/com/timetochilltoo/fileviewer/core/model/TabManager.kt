package com.timetochilltoo.fileviewer.core.model

class TabManager {

    var tabs: List<DocumentTab> = emptyList()
        private set

    var selectedTabId: String? = null
        private set

    val selected: DocumentTab?
        get() = tabs.firstOrNull { it.id == selectedTabId }

    fun findByUri(uri: String): DocumentTab? = tabs.firstOrNull { it.document.uri == uri }

    fun findById(id: String): DocumentTab? = tabs.firstOrNull { it.id == id }

    fun add(tab: DocumentTab): DocumentTab {
        tabs = tabs + tab
        selectedTabId = tab.id
        return tab
    }

    fun select(id: String) {
        if (tabs.any { it.id == id }) selectedTabId = id
    }

    fun update(tab: DocumentTab) {
        tabs = tabs.map { if (it.id == tab.id) tab else it }
    }

    fun remove(id: String): DocumentTab? {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return null
        val removed = tabs[index]
        tabs = tabs.toMutableList().apply { removeAt(index) }
        if (selectedTabId == id) {
            selectedTabId = when {
                tabs.isEmpty() -> null
                index < tabs.size -> tabs[index].id
                else -> tabs.last().id
            }
        }
        return removed
    }

    companion object {
        fun needsCloseConfirmation(tab: DocumentTab): Boolean = tab.hasUnsavedChanges
    }
}
