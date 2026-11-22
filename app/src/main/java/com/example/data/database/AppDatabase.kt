package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ChatMessageEntity::class, ConversationEntity::class], version = 4, exportSchema = false)
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

        /**
         * Historial de varias conversaciones: tabla `conversations` y columna `conversationId`.
         * Los mensajes que ya existían pasan a una primera conversación, titulada con el primer mensaje del usuario.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE chat_messages ADD COLUMN `conversationId` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_chat_messages_conversationId` ON `chat_messages` (`conversationId`)")
                db.execSQL(
                    "INSERT INTO conversations (id, title, createdAt, updatedAt) " +
                        "SELECT 1, COALESCE((SELECT substr(text, 1, 48) FROM chat_messages WHERE sender = 'USER' ORDER BY id LIMIT 1), 'Chat anterior'), " +
                        "MIN(timestamp), MAX(timestamp) FROM chat_messages HAVING COUNT(*) > 0"
                )
                db.execSQL("UPDATE chat_messages SET conversationId = 1")
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
