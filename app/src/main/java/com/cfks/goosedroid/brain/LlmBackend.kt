package com.cfks.goosedroid.brain

interface LlmBackend {
    val backendName: String
    val backendType: LlmBackendType
    
    fun isAvailable(): Boolean
    fun getStatusDetails(): String
    
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 128,
        temp: Float = 0.7f,
        repeatPenalty: Float = 1.1f
    ): String?
}

enum class LlmBackendType(val displayName: String, val badge: String) {
    ON_DEVICE_GGUF("On-Device GGUF (PetLlama JNI)", "⚡ Native"),
    LOCAL_SERVER("Dev llama-server (HTTP)", "🌐 Network"),
    SMART_RULE_ENGINE("Smart Rule & Agent Brain", "🧠 Embedded")
}
