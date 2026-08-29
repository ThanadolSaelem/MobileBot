package com.cfks.goosedroid.brain

import android.content.Context
import com.cfks.goosedroid.ai.AiManager
import com.cfks.goosedroid.ai.EngineLogBus
import com.cfks.goosedroid.data.ChatRepository

/**
 * Phase 2 memory system:
 *
 * - **Sliding window**: only the last [WINDOW_SIZE] messages are packed
 *   into the LLM prompt (from Room, not RAM).
 * - **Rolling summaries**: once total messages exceed [SUMMARIZE_THRESHOLD],
 *   the oldest un-summarized batch (>= [MIN_BATCH] messages) is condensed
 *   by the LLM into a summary row. Summaries are always injected, so the
 *   bot "remembers" old events without paying for every old token.
 */
object MemoryManager {

    const val WINDOW_SIZE = 10
    const val SUMMARIZE_THRESHOLD = 25
    private const val MIN_BATCH = 8

    data class MemoryContext(
        val promptBlock: String,
        val totalMessages: Int,
        val windowCount: Int,
        val summaryCount: Int,
    )

    /** Builds the memory block injected into the system prompt. */
    suspend fun buildContext(repo: ChatRepository, conversationId: Long): MemoryContext {
        val summaries = repo.getSummaries(conversationId)
        val recent = repo.getLatestMessages(conversationId, WINDOW_SIZE).asReversed()

        val sb = StringBuilder()
        if (summaries.isNotEmpty()) {
            sb.append("EARLIER CONVERSATION SUMMARY (condensed long-term memory):\n")
            summaries.forEach { s -> sb.append("- ").append(s.summaryText).append('\n') }
            sb.append('\n')
        }
        if (recent.isNotEmpty()) {
            sb.append("RECENT MESSAGES (oldest to newest):\n")
            recent.forEach { m ->
                val who = if (m.isFromUser) "USER" else m.sender.uppercase()
                sb.append(who).append(": ").append(m.text.replace('\n', ' ')).append('\n')
            }
        }

        val ctx = MemoryContext(
            promptBlock = sb.toString().trim(),
            totalMessages = repo.messageCount(conversationId),
            windowCount = recent.size,
            summaryCount = summaries.size
        )
        EngineLogBus.debug(
            "MemoryManager",
            "CONTEXT BUILT (total=${ctx.totalMessages}, window=${ctx.windowCount}, summaries=${ctx.summaryCount})"
        )
        return ctx
    }

    /**
     * Call AFTER a reply has been persisted. When the conversation grows
     * past [SUMMARIZE_THRESHOLD], condenses the oldest uncovered batch via
     * the LLM and stores it. Returns true when a new summary was created.
     */
    suspend fun summarizeIfNeeded(
        context: Context,
        repo: ChatRepository,
        characterName: String,
        conversationId: Long
    ): Boolean {
        val total = repo.messageCount(conversationId)
        if (total < SUMMARIZE_THRESHOLD) return false

        val coveredUpTo = repo.getLatestSummary(conversationId)?.coversUpToMessageId ?: 0L
        val window = repo.getLatestMessages(conversationId, WINDOW_SIZE)
        val windowFloorId = window.minOfOrNull { it.id } ?: return false

        val candidates = repo.getAllMessages(conversationId)
            .filter { it.id > coveredUpTo && it.id < windowFloorId }
        if (candidates.size < MIN_BATCH) return false

        val transcript = candidates.joinToString("\n") { m ->
            val who = if (m.isFromUser) "USER" else m.sender.uppercase()
            "${who}: ${m.text.replace('\n', ' ')}"
        }

        EngineLogBus.info("MemoryManager", "SUMMARIZING ${candidates.size} messages...")
        val summary = AiManager(context).summarizeTranscript(transcript, characterName)
        return if (summary.isNullOrBlank()) {
            EngineLogBus.warn("MemoryManager", "SUMMARY FAILED — retry on next turn")
            false
        } else {
            repo.insertSummary(conversationId, summary, candidates.maxOf { it.id })
            EngineLogBus.info("MemoryManager", "SUMMARY SAVED (${candidates.size} msgs condensed)")
            true
        }
    }
}
