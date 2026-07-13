package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String, // USER, AI, TERMUX_SYS, TERMUX_OUT, TERMUX_ERR
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String? = null
)
