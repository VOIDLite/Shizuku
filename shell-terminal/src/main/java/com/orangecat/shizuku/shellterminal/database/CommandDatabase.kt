package com.orangecat.shizuku.shellterminal.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CommandTemplate::class, CommandHistory::class], version = 1, exportSchema = false)
abstract class CommandDatabase : RoomDatabase() {

    abstract fun commandDao(): CommandDao

    companion object {
        @Volatile
        private var INSTANCE: CommandDatabase? = null

        fun getDatabase(context: Context): CommandDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CommandDatabase::class.java,
                    "shizuku_terminal_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
