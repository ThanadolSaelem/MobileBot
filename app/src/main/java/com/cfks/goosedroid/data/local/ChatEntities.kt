package com.cfks.goosedroid.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A conversation thread bound to one character unit.
 * Character units can hold multiple conversations (MAID parity).
 */
@Entity(
    tableName = "conversations",
    indices = [Index("characterName")]
)
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "characterName") val characterName: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updatedAt") val updatedAt: Long = System.currentTimeMillis()
)

/**
 * One chat message inside a conversation.
 * Persisted so conversations survive process death (Phase 1 goal).
 */
@Entity(
    tableName = "chat_messages",
    indices = [Index("conversationId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conversationId") val conversationId: Long,
    @ColumnInfo(name = "sender") val sender: String,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "actionBadge") val actionBadge: String? = null,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "isFromUser") val isFromUser: Boolean
)

/**
 * Condensed long-term memory (Phase 2). All messages with
 * id <= coversUpToMessageId are represented by summaryText,
 * so old turns can be dropped from the LLM window without
 * losing the gist.
 */
@Entity(
    tableName = "conversation_summaries",
    indices = [Index("conversationId")]
)
data class ConversationSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conversationId") val conversationId: Long,
    @ColumnInfo(name = "summaryText") val summaryText: String,
    @ColumnInfo(name = "coversUpToMessageId") val coversUpToMessageId: Long,
    @ColumnInfo(name = "createdAt") val createdAt: Long = System.currentTimeMillis()
)
