package com.orangecat.shizuku.shellterminal

import android.content.Context
import com.orangecat.shizuku.shellterminal.database.CommandDatabase
import com.orangecat.shizuku.shellterminal.database.CommandHistory
import com.orangecat.shizuku.shellterminal.database.CommandTemplate
import org.json.JSONArray
import java.io.IOException

class TemplateCommandManager private constructor() {

    companion object {
        val instance: TemplateCommandManager by lazy { TemplateCommandManager() }
    }

    /**
     * Load presets from assets JSON file.
     */
    fun loadPresets(context: Context): List<CommandTemplate> {
        val presets = mutableListOf<CommandTemplate>()
        try {
            val jsonString = context.assets.open("presets.json").bufferedReader().use { it.readText() }
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val name = obj.getString("name")
                val command = obj.getString("command")
                // Use negative ids for presets so they don't conflict with database ids
                presets.add(CommandTemplate(id = -(i + 1), name = name, command = command, isCustom = false))
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return presets
    }

    /**
     * Get all templates: presets combined with custom templates from database.
     */
    suspend fun getAllTemplates(context: Context): List<CommandTemplate> {
        val presets = loadPresets(context)
        val customTemplates = CommandDatabase.getDatabase(context).commandDao().getAllTemplates()
        return presets + customTemplates
    }

    /**
     * Save a custom template to the local database.
     */
    suspend fun saveCustomTemplate(context: Context, name: String, command: String): Long {
        val dao = CommandDatabase.getDatabase(context).commandDao()
        val template = CommandTemplate(name = name, command = command, isCustom = true)
        return dao.insertTemplate(template)
    }

    /**
     * Update an existing custom template in the database.
     */
    suspend fun updateCustomTemplate(context: Context, template: CommandTemplate) {
        if (!template.isCustom) return // Prevent editing presets
        val dao = CommandDatabase.getDatabase(context).commandDao()
        dao.updateTemplate(template)
    }

    /**
     * Delete a custom template from the database.
     */
    suspend fun deleteCustomTemplate(context: Context, template: CommandTemplate) {
        if (!template.isCustom) return // Prevent deleting presets
        val dao = CommandDatabase.getDatabase(context).commandDao()
        dao.deleteTemplate(template)
    }

    // --- Command History Management ---

    /**
     * Add a command to history in Room DB.
     */
    suspend fun addToHistory(context: Context, command: String) {
        if (command.isBlank()) return
        val dao = CommandDatabase.getDatabase(context).commandDao()
        // Check if last command is identical to avoid duplicate consecutive history
        val existing = dao.getHistory()
        if (existing.isNotEmpty() && existing.first().command == command) {
            return
        }
        dao.insertHistory(CommandHistory(command = command))
    }

    /**
     * Get the history of executed commands.
     */
    suspend fun getHistory(context: Context): List<String> {
        val dao = CommandDatabase.getDatabase(context).commandDao()
        return dao.getHistory().map { it.command }
    }

    /**
     * Clear all command history.
     */
    suspend fun clearHistory(context: Context) {
        val dao = CommandDatabase.getDatabase(context).commandDao()
        dao.clearHistory()
    }
}
