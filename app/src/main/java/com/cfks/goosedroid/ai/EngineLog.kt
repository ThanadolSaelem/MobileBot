package com.cfks.goosedroid.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class EngineLogLevel { DEBUG, INFO, WARN, ERROR }

data class EngineLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: EngineLogLevel,
    val source: String,
    val message: String
)

/**
 * Central in-memory log bus for AI engine events (init, requests, responses,
 * tool calls, errors). The AI settings screen renders this live as a
 * monochrome console. Ring buffer caps memory usage.
 */
object EngineLogBus {
    private const val MAX_ENTRIES = 200

    private val _entries = MutableStateFlow<List<EngineLogEntry>>(emptyList())
    val entries: StateFlow<List<EngineLogEntry>> = _entries.asStateFlow()

    fun log(level: EngineLogLevel, source: String, message: String) {
        _entries.update { current ->
            (current + EngineLogEntry(level = level, source = source, message = message))
                .takeLast(MAX_ENTRIES)
        }
    }

    fun debug(source: String, message: String) = log(EngineLogLevel.DEBUG, source, message)
    fun info(source: String, message: String) = log(EngineLogLevel.INFO, source, message)
    fun warn(source: String, message: String) = log(EngineLogLevel.WARN, source, message)
    fun error(source: String, message: String) = log(EngineLogLevel.ERROR, source, message)

    fun clear() {
        _entries.value = emptyList()
    }
}
