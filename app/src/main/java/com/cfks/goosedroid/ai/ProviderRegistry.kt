package com.cfks.goosedroid.ai

/**
 * Common AI Provider configurations (Presets).
 */
data class AiProvider(
    val name: String,
    val baseUrl: String,
    val defaultModel: String
)

object ProviderRegistry {
    val providers = listOf(
        AiProvider("OpenRouter", "https://openrouter.ai/api/v1/", "openai/gpt-4o-mini"),
        AiProvider("OpenAI", "https://api.openai.com/v1/", "gpt-4o-mini"),
        AiProvider("DeepSeek", "https://api.deepseek.com/", "deepseek-chat"),
        AiProvider("Mistral", "https://api.mistral.ai/v1/", "mistral-tiny"),
        AiProvider("Novita", "https://api.novita.ai/v3/openai/", "meta-llama/llama-3.1-8b-instruct"),
        AiProvider("Ollama (Local)", "http://10.0.2.2:11434/v1/", "llama3")
    )
}
