package com.cfks.goosedroid.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * App database (Phase 1+).
 * Schema is exported to /app/schemas via KSP room.schemaLocation for
 * future migration authoring.
 *
 * v2 (Phase 2): adds conversation_summaries for the memory system.
 */
@Database(
    entities = [
        ConversationEntity::class,
        ChatMessageEntity::class,
        ConversationSummaryEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class GooseDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun conversationSummaryDao(): ConversationSummaryDao

    companion object {
        @Volatile
        private var INSTANCE: GooseDatabase? = null

        /** v1 → v2: new table only — existing chats are preserved. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `conversation_summaries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`conversationId` INTEGER NOT NULL, " +
                        "`summaryText` TEXT NOT NULL, " +
                        "`coversUpToMessageId` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS " +
                        "`index_conversation_summaries_conversationId` " +
                        "ON `conversation_summaries` (`conversationId`)"
                )
            }
        }

        fun get(context: Context): GooseDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GooseDatabase::class.java,
                    "goose_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
