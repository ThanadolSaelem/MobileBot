package com.cfks.goosedroid.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query(
        "SELECT * FROM conversations WHERE characterName = :characterName " +
            "ORDER BY updatedAt DESC"
    )
    fun observeByCharacter(characterName: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): ConversationEntity?

    @Query(
        "SELECT * FROM conversations WHERE characterName = :characterName " +
            "ORDER BY updatedAt DESC LIMIT 1"
    )
    suspend fun getLatestByCharacter(characterName: String): ConversationEntity?

    @Insert
    suspend fun insert(conversation: ConversationEntity): Long

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun rename(id: Long, title: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE conversations SET updatedAt = :updatedAt WHERE id = :id")
    suspend fun touch(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ChatMessageDao {

    @Query(
        "SELECT * FROM chat_messages WHERE conversationId = :conversationId " +
            "ORDER BY timestamp ASC, id ASC"
    )
    fun observeByConversation(conversationId: Long): Flow<List<ChatMessageEntity>>

    @Query(
        "SELECT * FROM chat_messages WHERE conversationId = :conversationId " +
            "ORDER BY timestamp DESC, id DESC LIMIT :limit"
    )
    suspend fun getLatest(conversationId: Long, limit: Int): List<ChatMessageEntity>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun countByConversation(conversationId: Long): Int

    @Query("SELECT * FROM chat_messages WHERE id = :id")
    suspend fun getById(id: Long): ChatMessageEntity?

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @Update
    suspend fun update(message: ChatMessageEntity)

    @Insert
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: Long)

    @Query(
        "SELECT * FROM chat_messages WHERE conversationId = :conversationId " +
            "ORDER BY timestamp ASC, id ASC"
    )
    suspend fun getAllByConversation(conversationId: Long): List<ChatMessageEntity>
}

@Dao
interface ConversationSummaryDao {

    @Query(
        "SELECT * FROM conversation_summaries WHERE conversationId = :conversationId " +
            "ORDER BY createdAt ASC, id ASC"
    )
    suspend fun getAll(conversationId: Long): List<ConversationSummaryEntity>

    @Query(
        "SELECT * FROM conversation_summaries WHERE conversationId = :conversationId " +
            "ORDER BY coversUpToMessageId DESC LIMIT 1"
    )
    suspend fun getLatest(conversationId: Long): ConversationSummaryEntity?

    @Insert
    suspend fun insert(summary: ConversationSummaryEntity): Long

    @Query("DELETE FROM conversation_summaries WHERE conversationId = :conversationId")
    suspend fun deleteByConversation(conversationId: Long)
}
