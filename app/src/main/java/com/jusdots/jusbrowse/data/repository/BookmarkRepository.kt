package com.jusdots.jusbrowse.data.repository

import com.jusdots.jusbrowse.data.database.BookmarkDao
import com.jusdots.jusbrowse.data.models.Bookmark
import kotlinx.coroutines.flow.Flow

class BookmarkRepository(private val bookmarkDao: BookmarkDao) {
    
    fun getAllBookmarks(): Flow<List<Bookmark>> = bookmarkDao.getAllBookmarks()

    fun searchBookmarks(query: String): Flow<List<Bookmark>> = 
        bookmarkDao.searchBookmarks(query)

    suspend fun getBookmarkByUrl(url: String): Bookmark? =
        bookmarkDao.getBookmarkByUrl(url)

    suspend fun isBookmarked(url: String): Boolean =
        bookmarkDao.getBookmarkByUrl(url) != null

    suspend fun addBookmark(title: String, url: String) {
        val bookmark = Bookmark(
            title = title,
            url = url
        )
        bookmarkDao.insertBookmark(bookmark)
    }

    suspend fun deleteBookmark(bookmark: Bookmark) {
        bookmarkDao.deleteBookmark(bookmark)
    }

    suspend fun deleteAllBookmarks() {
        bookmarkDao.deleteAllBookmarks()
    }
}
