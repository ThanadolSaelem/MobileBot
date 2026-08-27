package com.cfks.goosedroid.download

/**
 * Curated catalog of GGUF models for easy one-tap downloads.
 * Inspired by MAID's model catalog approach.
 */
object ModelCatalog {

    data class ModelInfo(
        val id: String,
        val displayName: String,
        val repo: String, // Hugging Face repo ID
        val filename: String, // GGUF filename in the repo
        val sizeBytes: Long,
        val quantization: String, // e.g., "Q4_K_M", "Q8_0"
        val description: String,
        val category: Category,
        val sha256: String? = null // Optional checksum for verification
    )

    enum class Category(val displayName: String) {
        RECOMMENDED("RECOMMENDED"),
        SMALL_FAST("SMALL / FAST"),
        HIGH_QUALITY("HIGH QUALITY"),
        CODE("CODE / REASONING"),
        MULTILINGUAL("MULTILINGUAL")
    }

    val allModels = listOf(
        // === RECOMMENDED ===
        ModelInfo(
            id = "llama-3.2-3b-q4",
            displayName = "Llama 3.2 3B Instruct",
            repo = "bartowski/Llama-3.2-3B-Instruct-GGUF",
            filename = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            sizeBytes = 2_000_000_000L, // ~2 GB
            quantization = "Q4_K_M",
            description = "Meta's latest small model. Excellent quality/size balance for mobile.",
            category = Category.RECOMMENDED,
            sha256 = "A1B2C3D4E5F6..." // Placeholder - would be real in production
        ),
        ModelInfo(
            id = "phi-3.5-mini-q4",
            displayName = "Phi-3.5 Mini Instruct",
            repo = "microsoft/Phi-3.5-mini-instruct-GGUF",
            filename = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
            sizeBytes = 2_200_000_000L,
            quantization = "Q4_K_M",
            description = "Microsoft's efficient small model. Strong reasoning for its size.",
            category = Category.RECOMMENDED,
            sha256 = "F6E5D4C3B2A1..."
        ),
        ModelInfo(
            id = "gemma-2-2b-q4",
            displayName = "Gemma 2 2B Instruct",
            repo = "bartowski/gemma-2-2b-it-GGUF",
            filename = "gemma-2-2b-it-Q4_K_M.gguf",
            sizeBytes = 1_600_000_000L,
            quantization = "Q4_K_M",
            description = "Google's lightweight model. Fast inference, good for on-device.",
            category = Category.RECOMMENDED,
            sha256 = "1A2B3C4D5E6F..."
        ),

        // === SMALL / FAST (< 1.5 GB) ===
        ModelInfo(
            id = "smollm-1.7b-q4",
            displayName = "SmolLM 1.7B Instruct",
            repo = "HuggingFaceTB/SmolLM-1.7B-Instruct-GGUF",
            filename = "SmolLM-1.7B-Instruct-Q4_K_M.gguf",
            sizeBytes = 1_100_000_000L,
            quantization = "Q4_K_M",
            description = "Tiny but capable. Good for very limited RAM devices.",
            category = Category.SMALL_FAST,
            sha256 = "2B3C4D5E6F1A..."
        ),
        ModelInfo(
            id = "qwen-2.5-1.5b-q4",
            displayName = "Qwen 2.5 1.5B Instruct",
            repo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF",
            filename = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            sizeBytes = 1_000_000_000L,
            quantization = "Q4_K_M",
            description = "Alibaba's compact multilingual model. Strong Chinese/English.",
            category = Category.SMALL_FAST,
            sha256 = "3C4D5E6F1A2B..."
        ),

        // === HIGH QUALITY (3-5 GB) ===
        ModelInfo(
            id = "llama-3.1-8b-q4",
            displayName = "Llama 3.1 8B Instruct",
            repo = "bartowski/Meta-Llama-3.1-8B-Instruct-GGUF",
            filename = "Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf",
            sizeBytes = 4_800_000_000L,
            quantization = "Q4_K_M",
            description = "Flagship open model. Best quality for mobile if RAM allows.",
            category = Category.HIGH_QUALITY,
            sha256 = "4D5E6F1A2B3C..."
        ),
        ModelInfo(
            id = "nemotron-3-8b-q4",
            displayName = "Nemotron 3 Ultra 8B",
            repo = "nvidia/Nemotron-3-Ultra-GGUF",
            filename = "Nemotron-3-Ultra-Q4_K_M.gguf",
            sizeBytes = 4_900_000_000L,
            quantization = "Q4_K_M",
            description = "NVIDIA's high-quality model. Excellent instruction following.",
            category = Category.HIGH_QUALITY,
            sha256 = "5E6F1A2B3C4D..."
        ),

        // === CODE / REASONING ===
        ModelInfo(
            id = "deepseek-coder-6.7b-q4",
            displayName = "DeepSeek Coder 6.7B",
            repo = "deepseek-ai/DeepSeek-Coder-6.7B-Instruct-GGUF",
            filename = "DeepSeek-Coder-6.7B-Instruct-Q4_K_M.gguf",
            sizeBytes = 3_900_000_000L,
            quantization = "Q4_K_M",
            description = "Specialized for code generation and technical reasoning.",
            category = Category.CODE,
            sha256 = "6F1A2B3C4D5E..."
        ),
        ModelInfo(
            id = "codegemma-7b-q4",
            displayName = "CodeGemma 7B",
            repo = "google/codegemma-7b-GGUF",
            filename = "codegemma-7b-Q4_K_M.gguf",
            sizeBytes = 4_200_000_000L,
            quantization = "Q4_K_M",
            description = "Google's code-focused Gemma variant.",
            category = Category.CODE,
            sha256 = "7A8B9C0D1E2F..."
        ),

        // === MULTILINGUAL ===
        ModelInfo(
            id = "aya-23-8b-q4",
            displayName = "Aya 23 8B (Multilingual)",
            repo = "CohereForAI/aya-23-8B-GGUF",
            filename = "aya-23-8B-Q4_K_M.gguf",
            sizeBytes = 4_800_000_000L,
            quantization = "Q4_K_M",
            description = "Cohere's multilingual model. 23 languages including Thai.",
            category = Category.MULTILINGUAL,
            sha256 = "8B9C0D1E2F3A..."
        ),
        ModelInfo(
            id = "qwen-2.5-7b-q4",
            displayName = "Qwen 2.5 7B Instruct",
            repo = "Qwen/Qwen2.5-7B-Instruct-GGUF",
            filename = "Qwen2.5-7B-Instruct-Q4_K_M.gguf",
            sizeBytes = 4_400_000_000L,
            quantization = "Q4_K_M",
            description = "Strong multilingual (29 langs), great for Thai/Chinese/English.",
            category = Category.MULTILINGUAL,
            sha256 = "9C0D1E2F3A4B..."
        )
    )

    fun getModelsByCategory(category: Category): List<ModelInfo> =
        allModels.filter { it.category == category }

    fun getModelById(id: String): ModelInfo? =
        allModels.find { it.id == id }

    val categories = Category.values().toList()
}