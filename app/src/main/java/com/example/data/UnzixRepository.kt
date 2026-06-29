package com.example.data

import kotlinx.coroutines.flow.Flow

class UnzixRepository(private val dao: UnzixDao) {
    val recentOperations: Flow<List<RecentOperation>> = dao.getRecentOperations()
    val bookmarks: Flow<List<Bookmark>> = dao.getAllBookmarks()

    suspend fun insertOperation(operation: RecentOperation) {
        dao.insertOperation(operation)
    }

    suspend fun clearAllOperations() {
        dao.clearAllOperations()
    }

    suspend fun addBookmark(path: String, name: String) {
        dao.insertBookmark(Bookmark(path, name))
    }

    suspend fun removeBookmark(path: String) {
        dao.deleteBookmarkByPath(path)
    }

    suspend fun isBookmarked(path: String): Boolean {
        return dao.isBookmarked(path)
    }
}
