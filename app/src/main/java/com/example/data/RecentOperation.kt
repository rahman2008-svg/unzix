package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_operations")
data class RecentOperation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val actionName: String, // "Compressed" or "Extracted" or "Deleted"
    val fileName: String,
    val filePath: String,
    val fileSize: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isSuccess: Boolean = true
)
