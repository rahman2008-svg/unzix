package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UnzixDao {
    // Recent Operations
    @Query("SELECT * FROM recent_operations ORDER BY timestamp DESC LIMIT 50")
    fun getRecentOperations(): Flow<List<RecentOperation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperation(operation: RecentOperation)

    @Query("DELETE FROM recent_operations")
    suspend fun clearAllOperations()

    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY timestamp DESC")
    fun getAllBookmarks(): Flow<List<Bookmark>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: Bookmark)

    @Query("DELETE FROM bookmarks WHERE path = :path")
    suspend fun deleteBookmarkByPath(path: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE path = :path LIMIT 1)")
    suspend fun isBookmarked(path: String): Boolean
}
