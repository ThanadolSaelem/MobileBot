package com.cfks.goosedroid.download

/**
 * Curated catalog of GGUF models.
 * Models are hosted on GitHub Releases (via jsDelivr CDN for fast global access).
 * The bundled SmolLM 135M model is included in the APK assets.
 */
object ModelCatalog {

    data class ModelInfo(
        val id: String,
        val displayName: String,
        val repo: String, // Base URL for downloads (GitHub Releases or CDN)
        val filename: String, // GGUF filename
        val branch: String = "", // Deprecated - kept for compatibility
        val sizeBytes: Long,
        val quantization: String,
        val description: String,
        val category: Category,
        val sha256: String? = null
    )

    enum class Category(val displayName: String) {
        BUNDLED("BUNDLED (OFFLINE)"),
        RECOMMENDED("RECOMMENDED"),
        SMALL_FAST("SMALL / FAST"),
        HIGH_QUALITY("HIGH QUALITY"),
        CODE("CODE / REASONING"),
        MULTILINGUAL("MULTILINGUAL")
    }

    // Base URL for GitHub Releases downloads via jsDelivr CDN
    // Format: https://cdn.jsdelivr.net/gh/ThanadolSaelem/MobileBot-Model@v1.0.0/
    private const val CDN_BASE = "https://cdn.jsdelivr.net/gh/ThanadolSaelem/MobileBot-Model@v1.0.0/"

    val allModels = listOf(
        // === BUNDLED (in APK assets) ===
        ModelInfo(
            id = "smollm-135m-q4",
            displayName = "SmolLM 135M Instruct",
            repo = "https://huggingface.co/HuggingFaceTB/SmolLM-135M-Instruct-GGUF/resolve/main/",
            filename = "SmolLM-135M-Instruct-v0.2-Q4_K_M.gguf",
            sizeBytes = 91_000_000L,
            quantization = "Q4_K_M",
            description = "Ultra-tiny model. Works offline. Limited reasoning, English-focused. Auto-downloads from Hugging Face.",
            category = Category.BUNDLED,
            sha256 = null
        ),

        // === RECOMMENDED (downloaded from GitHub Releases via jsDelivr CDN) ===
        ModelInfo(
            id = "llama-3.2-3b-q4",
            displayName = "Llama 3.2 3B Instruct",
            repo = CDN_BASE,
            filename = "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            sizeBytes = 2_000_000_000L,
            quantization = "Q4_K_M",
            description = "Meta's latest small model. Excellent quality/size balance for mobile. Supports Thai.",
            category = Category.RECOMMENDED,
            sha256 = null
        ),
        ModelInfo(
            id = "phi-3.5-mini-q4",
            displayName = "Phi-3.5 Mini Instruct",
            repo = CDN_BASE,
            filename = "Phi-3.5-mini-instruct-Q4_K_M.gguf",
            sizeBytes = 2_200_000_000L,
            quantization = "Q4_K_M",
            description = "Microsoft's efficient small model. Strong reasoning. Supports Thai.",
            category = Category.RECOMMENDED,
            sha256 = null
        ),
        ModelInfo(
            id = "gemma-2-2b-q4",
            displayName = "Gemma 2 2B Instruct",
            repo = CDN_BASE,
            filename = "gemma-2-2b-it-Q4_K_M.gguf",
            sizeBytes = 1_600_000_000L,
            quantization = "Q4_K_M",
            description = "Google's lightweight model. Fast inference, good Thai support.",
            category = Category.RECOMMENDED,
            sha256 = null
        ),

        // === SMALL / FAST (< 1.5 GB) ===
        ModelInfo(
            id = "smollm-1.7b-q4",
            displayName = "SmolLM 1.7B Instruct",
            repo = CDN_BASE,
            filename = "SmolLM-1.7B-Instruct-Q4_K_M.gguf",
            sizeBytes = 1_100_000_000L,
            quantization = "Q4_K_M",
            description = "Tiny but capable. Good for limited RAM. English-focused.",
            category = Category.SMALL_FAST,
            sha256 = null
        ),
        ModelInfo(
            id = "qwen-2.5-1.5b-q4",
            displayName = "Qwen 2.5 1.5B Instruct",
            repo = CDN_BASE,
            filename = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            sizeBytes = 1_000_000_000L,
            quantization = "Q4_K_M",
            description = "Alibaba's compact multilingual model. Strong Chinese/English/Thai support.",
            category = Category.SMALL_FAST,
            sha256 = null
        ),

        // === HIGH QUALITY (3-5 GB) ===
        ModelInfo(
            id = "llama-3.1-8b-q4",
            displayName = "Llama 3.1 8B Instruct",
            repo = CDN_BASE,
            filename = "Meta-Llama-3.1-8B-Instruct-Q4_K_M.gguf",
            sizeBytes = 4_800_000_000L,
            quantization = "Q4_K_M",
            description = "Flagship open model. Best quality for mobile if RAM allows. Excellent Thai support.",
            category = Category.HIGH_QUALITY,
            sha256 = null
        ),
        ModelInfo(
            id = "nemotron-3-8b-q4",
            displayName = "Nemotron 3 Ultra 8B",
            repo = CDN_BASE,
            filename = "Nemotron-3-Ultra-Q4_K_M.gguf",
            sizeBytes = 4_900_000_000L,
            quantization = "Q4_K_M",
            description = "NVIDIA's high-quality model. Excellent instruction following. Good Thai support.",
            category = Category.HIGH_QUALITY,
            sha256 = null
        ),

        // === CODE / REASONING ===
        ModelInfo(
            id = "deepseek-coder-6.7b-q4",
            displayName = "DeepSeek Coder 6.7B",
            repo = CDN_BASE,
            filename = "DeepSeek-Coder-6.7B-Instruct-Q4_K_M.gguf",
            sizeBytes = 3_900_000_000L,
            quantization = "Q4_K_M",
            description = "Specialized for code generation and technical reasoning. English-focused.",
            category = Category.CODE,
            sha256 = null
        ),
        ModelInfo(
            id = "codegemma-7b-q4",
            displayName = "CodeGemma 7B",
            repo = CDN_BASE,
            filename = "codegemma-7b-Q4_K_M.gguf",
            sizeBytes = 4_200_000_000L,
            quantization = "Q4_K_M",
            description = "Google's code-focused Gemma variant. Good code/Thai support.",
            category = Category.CODE,
            sha256 = null
        ),

        // === MULTILINGUAL ===
        ModelInfo(
            id = "aya-23-8b-q4",
            displayName = "Aya 23 8B (Multilingual)",
            repo = CDN_BASE,
            filename = "aya-23-8B-Q4_K_M.gguf",
            sizeBytes = 4_800_000_000L,
            quantization = "Q4_K_M",
            description = "Cohere's multilingual model. 23 languages including excellent Thai support.",
            category = Category.MULTILINGUAL,
            sha256 = null
        ),
        ModelInfo(
            id = "qwen-2.5-7b-q4",
            displayName = "Qwen 2.5 7B Instruct",
            repo = CDN_BASE,
            filename = "Qwen2.5-7B-Instruct-Q4_K_M.gguf",
            sizeBytes = 4_400_000_000L,
            quantization = "Q4_K_M",
            description = "Strong multilingual (29 langs), great for Thai/Chinese/English.",
            category = Category.MULTILINGUAL,
            sha256 = null
        )
    )

    fun getModelsByCategory(category: Category): List<ModelInfo> =
        allModels.filter { it.category == category }

    fun getModelById(id: String): ModelInfo? =
        allModels.find { it.id == id }

    val categories = Category.values().toList()

    /**
     * Get the full download URL for a model.
     * Uses jsDelivr CDN for GitHub Releases.
     */
    fun getDownloadUrl(model: ModelInfo): String {
        return if (model.repo.isNotBlank()) {
            "${model.repo}${model.filename}"
        } else {
            "" // Bundled model - no download needed
        }
    }
}