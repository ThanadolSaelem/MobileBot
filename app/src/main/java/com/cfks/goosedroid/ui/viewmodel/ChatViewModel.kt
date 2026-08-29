package com.cfks.goosedroid.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cfks.goosedroid.ai.AiManager
import com.cfks.goosedroid.ai.ChatEngine
import com.cfks.goosedroid.ai.EngineLogBus
import com.cfks.goosedroid.brain.MemoryManager
import com.cfks.goosedroid.brain.PetBrain
import com.cfks.goosedroid.data.ChatRepository
import com.cfks.goosedroid.data.local.ChatMessageEntity
import com.cfks.goosedroid.data.local.ConversationEntity
import com.cfks.goosedroid.notify.SystemNotifier
import com.cfks.goosedroid.ui.alert.AlertBus
import com.cfks.goosedroid.ui.alert.AlertType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 1 chat ViewModel — persistent conversations via [ChatRepository].
 * Messages survive process death; UI collects via StateFlow (MVVM/MVI hybrid
 * pattern observed across MAID-Native / LocalMind / Jetchat research).
 */
class ChatViewModel(
    private val appContext: Context,
    private val repo: ChatRepository,
    private val characterName: String,
    initialConversationId: Long?
) : ViewModel() {

    val conversations: StateFlow<List<ConversationEntity>> =
        repo.observeConversations(characterName)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeConversationId = MutableStateFlow(initialConversationId)
    val activeConversationId: StateFlow<Long?> = _activeConversationId.asStateFlow()

    init {
        // Auto-resume the most recent conversation so history is visible
        // when opening COMM LINK without an explicit conversation id.
        if (initialConversationId == null) {
            viewModelScope.launch {
                val latest = repo.getLatestConversation(characterName)
                if (latest != null && _activeConversationId.value == null) {
                    _activeConversationId.value = latest.id
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessageEntity>> = _activeConversationId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repo.observeMessages(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectConversation(id: Long) {
        _activeConversationId.value = id
    }

    /** Creates a fresh conversation seeded with the unit's welcome message. */
    fun startNewChat() {
        viewModelScope.launch {
            val id = repo.createConversation(characterName, defaultTitle())
            repo.addMessage(
                conversationId = id,
                sender = characterName,
                text = welcomeText(),
                actionBadge = "SYSTEM // READY",
                isFromUser = false
            )
            _activeConversationId.value = id
        }
    }

    /**
     * Sends a user message through PetBrain and persists both turns.
     * The LLM round-trip runs on the app-lifetime [ChatEngine.scope], so it
     * keeps going even if the user leaves the chat screen — the reply lands
     * in Room and shows up when they return.
     */
    fun send(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val convId = _activeConversationId.value ?: run {
                val id = repo.createConversation(characterName, defaultTitle())
                _activeConversationId.value = id
                id
            }
            // One in-flight request per conversation.
            if (ChatEngine.isTyping(convId)) return@launch

            repo.addMessage(convId, "Commander", trimmed, null, true)

            ChatEngine.scope.launch {
                ChatEngine.setTyping(convId, true)
                var assistantMsgId: Long? = null
                try {
                    var lastResult: com.cfks.goosedroid.brain.LlmActionResult? = null
                    var capturedToolCall: com.cfks.goosedroid.ai.ToolCall? = null
                    
                    PetBrain.processCommandStream(appContext, trimmed, characterName, convId)
                        .collect { result ->
                            lastResult = result
                            if (result.toolCall != null) {
                                capturedToolCall = result.toolCall
                            }
                            if (assistantMsgId == null) {
                                assistantMsgId = repo.addMessage(
                                    convId,
                                    characterName,
                                    result.displayReply,
                                    result.actionBadge,
                                    false
                                )
                            } else {
                                repo.updateMessage(
                                    assistantMsgId!!,
                                    result.displayReply,
                                    result.actionBadge
                                )
                            }
                        }
                    
                    // Handle Tool Call if present in the stream at any point
                    val tool = capturedToolCall ?: lastResult?.toolCall
                    EngineLogBus.debug("ChatViewModel", "Stream finished. capturedToolCall=${tool?.name}")
                    
                    tool?.let { t ->
                        EngineLogBus.info("ChatViewModel", "EXECUTING TOOL: ${t.name}")
                        val aiManager = AiManager(appContext)
                        val toolResult = aiManager.toolRegistry.executeTool(appContext, t.name, t.args)
                        EngineLogBus.info("ChatViewModel", "TOOL RESULT: $toolResult")
                        
                        // Append tool result to the message
                        val currentText = lastResult?.displayReply ?: ""
                        val newText = if (currentText.isBlank()) toolResult else "$currentText\n\n[TOOL: ${t.name.uppercase()}]\n$toolResult"
                        repo.updateMessage(assistantMsgId!!, newText, "TOOL // ${t.name.uppercase()}")
                    }

                    // Final steps after stream ends
                    val latestMsg = assistantMsgId?.let { repo.getAllMessages(convId).find { m -> m.id == it } }
                    val finalReply = latestMsg?.text ?: ""
                    
                    if (ChatEngine.visibleConversationId.value != convId) {
                        SystemNotifier.notifyReply(appContext, characterName, finalReply, convId)
                    }
                    MemoryManager.summarizeIfNeeded(appContext, repo, characterName, convId)
                } catch (e: Exception) {
                    EngineLogBus.error("ChatViewModel", "SEND FAILED: ${e.message?.take(140)}")
                    repo.addMessage(
                        convId,
                        characterName,
                        "[SYSTEM ERROR] ${e.message?.take(140) ?: "unknown"}",
                        "ERROR",
                        false
                    )
                } finally {
                    ChatEngine.setTyping(convId, false)
                    ChatEngine.setStatus(convId, null)
                }
            }
        }
    }

    fun renameConversation(id: Long, title: String) {
        viewModelScope.launch { repo.renameConversation(id, title) }
    }

    fun deleteConversation(id: Long) {
        viewModelScope.launch {
            repo.deleteConversation(id)
            EngineLogBus.info("ChatViewModel", "CONVERSATION DELETED id=$id")
            if (_activeConversationId.value == id) _activeConversationId.value = null
        }
    }

    fun clearCurrentChat() {
        val id = _activeConversationId.value ?: return
        viewModelScope.launch {
            repo.clearMessages(id)
            EngineLogBus.info("ChatViewModel", "CHAT CLEARED id=$id")
            // Optional: re-seed with welcome message or leave empty
            repo.addMessage(
                conversationId = id,
                sender = characterName,
                text = "Chat cleared. Previous context removed.",
                actionBadge = "SYSTEM // RESET",
                isFromUser = false
            )
        }
    }

    fun exportConversation(uri: Uri, conversationId: Long) {
        viewModelScope.launch {
            try {
                val count = repo.exportToJson(appContext, conversationId, uri)
                AlertBus.show(AlertType.SUCCESS, "EXPORT COMPLETE", "$count messages saved")
                EngineLogBus.info("ChatRepository", "EXPORTED conversation=$conversationId ($count messages)")
            } catch (e: Exception) {
                AlertBus.show(AlertType.ERROR, "EXPORT FAILED", e.message?.take(120))
                EngineLogBus.error("ChatRepository", "EXPORT FAILED: ${e.message}")
            }
        }
    }

    fun importConversation(uri: Uri) {
        viewModelScope.launch {
            try {
                val count = repo.importFromJson(appContext, uri, characterName)
                AlertBus.show(AlertType.SUCCESS, "IMPORT COMPLETE", "$count messages imported")
                EngineLogBus.info("ChatRepository", "IMPORTED $count messages for $characterName")
            } catch (e: Exception) {
                AlertBus.show(AlertType.ERROR, "IMPORT FAILED", e.message?.take(120))
                EngineLogBus.error("ChatRepository", "IMPORT FAILED: ${e.message}")
            }
        }
    }

    private fun defaultTitle(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        return "CHAT ${fmt.format(Date())}"
    }

    private fun welcomeText(): String =
        "สวัสดีครับผู้บัญชาการ $characterName ออนไลน์และพร้อมรับคำสั่งภาษาไทยแล้วครับ"

    companion object {
        fun factory(context: Context, characterName: String, conversationId: Long?): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    val app = context.applicationContext
                    ChatViewModel(app, ChatRepository(app), characterName, conversationId)
                }
            }
    }
}
