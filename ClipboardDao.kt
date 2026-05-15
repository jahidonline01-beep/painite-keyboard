package com.painite.keyboard.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {

    @Query("SELECT * FROM clipboard_items ORDER BY isPinned ASC, sortOrder ASC, timestamp DESC")
    fun getAllItems(): Flow<List<ClipboardItem>>

    @Query("SELECT * FROM clipboard_items WHERE isPinned = 1 ORDER BY sortOrder ASC, timestamp DESC")
    fun getPinnedItems(): Flow<List<ClipboardItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ClipboardItem): Long

    @Delete
    suspend fun delete(item: ClipboardItem)

    @Query("DELETE FROM clipboard_items WHERE isPinned = 0")
    suspend fun clearUnpinned()

    @Update
    suspend fun update(item: ClipboardItem)

    @Query("SELECT EXISTS(SELECT 1 FROM clipboard_items WHERE text = :text LIMIT 1)")
    suspend fun exists(text: String): Boolean

    @Query("DELETE FROM clipboard_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM clipboard_items")
    suspend fun maxSortOrder(): Int
}
