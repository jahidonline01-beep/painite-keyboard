package com.painite.keyboard.data

import kotlinx.coroutines.flow.Flow

class ClipboardRepository(private val dao: ClipboardDao) {

    val allItems: Flow<List<ClipboardItem>> = dao.getAllItems()

    suspend fun addItem(text: String) {
        if (text.isBlank()) return
        val exists = dao.exists(text)
        if (!exists) {
            dao.insert(ClipboardItem(text = text, sortOrder = dao.maxSortOrder() + 1))
        }
    }

    suspend fun deleteItem(item: ClipboardItem) = dao.delete(item)

    suspend fun clearUnpinned() = dao.clearUnpinned()

    suspend fun togglePin(item: ClipboardItem) {
        dao.update(item.copy(isPinned = !item.isPinned, sortOrder = dao.maxSortOrder() + 1))
    }

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun updateOrder(items: List<ClipboardItem>) {
        items.forEachIndexed { index, item ->
            dao.update(item.copy(sortOrder = index))
        }
    }
}
