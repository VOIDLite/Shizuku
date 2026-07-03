package com.orangecat.shizuku.shellterminal.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_templates")
data class CommandTemplate(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val command: String,
    val isCustom: Boolean
)

@Entity(tableName = "command_history")
data class CommandHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val command: String,
    val timestamp: Long = System.currentTimeMillis()
)
