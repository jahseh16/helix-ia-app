package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ChatMessageEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao

    companion object {
        /**
         * Limpia filas heredadas de versiones anteriores (salidas de terminal externa y
         * marcadores "Pensando…" sin resolver). El esquema no cambia.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM chat_messages WHERE sender NOT IN ('USER', 'AI', 'SYSTEM')")
                db.execSQL("DELETE FROM chat_messages WHERE sender = 'AI' AND text = 'Pensando…'")
            }
        }

        /** Añade las columnas para tool calling (todas opcionales: las filas existentes siguen válidas). */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("toolCallsJson", "reasoning", "toolCallId", "toolName", "toolArgs", "toolStatus").forEach { col ->
                    db.execSQL("ALTER TABLE chat_messages ADD COLUMN $col TEXT")
                }
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "helix_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
