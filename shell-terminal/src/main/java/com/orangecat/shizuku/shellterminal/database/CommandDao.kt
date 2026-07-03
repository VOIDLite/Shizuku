package com.orangecat.shizuku.shellterminal.database

import androidx.room.*

@Dao
interface CommandDao {

    // Template Operations
    @Query("SELECT * FROM command_templates ORDER BY isCustom ASC, name ASC")
    suspend fun getAllTemplates(): List<CommandTemplate>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: CommandTemplate): Long

    @Update
    suspend fun updateTemplate(template: CommandTemplate)

    @Delete
    suspend fun deleteTemplate(template: CommandTemplate)

    // History Operations
    @Query("SELECT * FROM command_history ORDER BY timestamp DESC")
    suspend fun getHistory(): List<CommandHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: CommandHistory)

    @Query("DELETE FROM command_history")
    suspend fun clearHistory()
}
