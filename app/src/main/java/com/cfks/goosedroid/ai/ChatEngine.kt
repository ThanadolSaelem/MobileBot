package com.cfks.goosedroid.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * App-lifetime scope for chat engine work.
 *
 * Jobs launched in [scope] survive navigation and ViewModel clearing, so an
 * LLM request keeps running (and its reply lands in Room) even when the user
 * leaves the chat screen — the reply is waiting when they come back.
 *
 * Also tracks which conversations currently have an in-flight request so any
 * freshly recreated chat screen can show a correct typing indicator.
 */
object ChatEngine {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _typingConversationIds = MutableStateFlow<Set<Long>>(emptySet())
    val typingConversationIds: StateFlow<Set<Long>> = _typingConversationIds.asStateFlow()

    fun setTyping(conversationId: Long, typing: Boolean) {
        _typingConversationIds.update { ids ->
            if (typing) ids + conversationId else ids - conversationId
        }
    }

    fun isTyping(conversationId: Long): Boolean =
        _typingConversationIds.value.contains(conversationId)

    // ── Live engine status (shown in the typing bubble) ────────────────

    private val _statusText = MutableStateFlow<String?>(null)
    val statusText: StateFlow<String?> = _statusText.asStateFlow()

    /** e.g. "THINKING...", "RETRYING 1/3 — RATE LIMITED", null = idle. */
    fun setStatus(text: String?) {
        _statusText.value = text
    }

    // ── Visible-screen tracking (for reply notifications) ───────────────

    private val _visibleConversationId = MutableStateFlow<Long?>(null)
    val visibleConversationId: StateFlow<Long?> = _visibleConversationId.asStateFlow()

    /** Set by ChatScreen while open; reply notifications fire only when
     *  the conversation is NOT on screen. */
    fun setVisibleConversation(id: Long?) {
        _visibleConversationId.value = id
    }
}
