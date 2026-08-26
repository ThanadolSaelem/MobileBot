package com.cfks.goosedroid.data

import android.content.Context
import com.cfks.goosedroid.data.local.ChatMessageEntity
import com.cfks.goosedroid.data.local.ConversationEntity
import com.cfks.goosedroid.data.local.ConversationSummaryEntity
import com.cfks.goosedroid.data.local.GooseDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Repository for persistent conversations + messages (Phase 1).
 * Includes MAID-style JSON export/import via SAF uris.
 */
class ChatRepository(context: Context) {

    private val db = GooseDatabase.get(context)
    private val conversationDao = db.conversationDao()
    private val messageDao = db.chatMessageDao()
    private val summaryDao = db.conversationSummaryDao()

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // ── Observers ───────────────────────────────────────────────────────────

    fun observeConversations(characterName: String): Flow<List<ConversationEntity>> =
        conversationDao.observeByCharacter(characterName)

    fun observeMessages(conversationId: Long): Flow<List<ChatMessageEntity>> =
        messageDao.observeByConversation(conversationId)

    /** Most recently updated conversation for a character, or null. */
    suspend fun getLatestConversation(characterName: String): ConversationEntity? =
        conversationDao.getLatestByCharacter(characterName)

    // ── Commands ────────────────────────────────────────────────────────────

    suspend fun createConversation(characterName: String, title: String): Long {
        val id = conversationDao.insert(
            ConversationEntity(characterName = characterName, title = title)
        )
        return id
    }

    suspend fun renameConversation(id: Long, title: String) =
        conversationDao.rename(id, title.trim().ifEmpty { "UNTITLED" })

    suspend fun deleteConversation(id: Long) {
        messageDao.deleteByConversation(id)
        conversationDao.deleteById(id)
    }

    suspend fun addMessage(
        conversationId: Long,
        sender: String,
        text: String,
        actionBadge: String?,
        isFromUser: Boolean
    ): Long {
        val id = messageDao.insert(
            ChatMessageEntity(
                conversationId = conversationId,
                sender = sender,
                text = text,
                actionBadge = actionBadge,
                isFromUser = isFromUser
            )
        )
        conversationDao.touch(conversationId)
        return id
    }

    suspend fun messageCount(conversationId: Long): Int =
        messageDao.countByConversation(conversationId)

    suspend fun getLatestMessages(conversationId: Long, limit: Int): List<ChatMessageEntity> =
        messageDao.getLatest(conversationId, limit)

    // ── Memory (Phase 2) ───────────────────────────────────────────────────

    suspend fun getAllMessages(conversationId: Long): List<ChatMessageEntity> =
        messageDao.getAllByConversation(conversationId)

    suspend fun getSummaries(conversationId: Long): List<ConversationSummaryEntity> =
        summaryDao.getAll(conversationId)

    suspend fun getLatestSummary(conversationId: Long): ConversationSummaryEntity? =
        summaryDao.getLatest(conversationId)

    suspend fun insertSummary(conversationId: Long, summaryText: String, coversUpToMessageId: Long) {
        summaryDao.insert(
            ConversationSummaryEntity(
                conversationId = conversationId,
                summaryText = summaryText,
                coversUpToMessageId = coversUpToMessageId
            )
        )
    }

    // ── Export / Import (MAID parity: chats as JSON) ────────────────────────

    @Serializable
    private data class ExportedMessage(
        val sender: String,
        val text: String,
        val actionBadge: String? = null,
        val timestamp: Long,
        val isFromUser: Boolean
    )

    @Serializable
    private data class ExportedConversation(
        val format: String = FORMAT_TAG,
        val version: Int = 1,
        val title: String,
        val characterName: String,
        val exportedAt: Long,
        val messages: List<ExportedMessage>
    )

    /** Writes the conversation as pretty JSON to a SAF document uri. */
    suspend fun exportToJson(context: Context, conversationId: Long, uri: android.net.Uri): String {
        val conversation = conversationDao.getById(conversationId)
            ?: throw IllegalStateException("Conversation $conversationId not found")
        // chronological order for the file
        val messages = messageDao.getLatest(conversationId, Int.MAX_VALUE).asReversed()

        val payload = ExportedConversation(
            title = conversation.title,
            characterName = conversation.characterName,
            exportedAt = System.currentTimeMillis(),
            messages = messages.map {
                ExportedMessage(
                    sender = it.sender,
                    text = it.text,
                    actionBadge = it.actionBadge,
                    timestamp = it.timestamp,
                    isFromUser = it.isFromUser
                )
            }
        )

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.encodeToString(ExportedConversation.serializer(), payload).toByteArray())
        } ?: throw IllegalStateException("Cannot open output stream")

        return messages.size.toString()
    }

    /**
     * Reads a JSON chat file and stores it as a NEW conversation under
     * [characterName]. Returns the imported message count.
     */
    suspend fun importFromJson(
        context: Context,
        uri: android.net.Uri,
        characterName: String
    ): String {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?.toString(Charsets.UTF_8)
            ?: throw IllegalStateException("Cannot open input stream")

        val payload = json.decodeFromString(ExportedConversation.serializer(), raw)

        val conversationId = conversationDao.insert(
            ConversationEntity(
                characterName = characterName,
                title = payload.title.ifBlank { "IMPORTED CHAT" }
            )
        )

        messageDao.insertAll(
            payload.messages.map {
                ChatMessageEntity(
                    conversationId = conversationId,
                    sender = it.sender,
                    text = it.text,
                    actionBadge = it.actionBadge,
                    timestamp = it.timestamp,
                    isFromUser = it.isFromUser
                )
            }
        )
        return payload.messages.size.toString()
    }

    companion object {
        const val FORMAT_TAG = "goosedroid-chat"
    }
}
