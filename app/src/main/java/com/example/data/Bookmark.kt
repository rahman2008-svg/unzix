package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey val path: String,
    val name: String,
    val timestamp: Long = System.currentTimeMillis()
)
