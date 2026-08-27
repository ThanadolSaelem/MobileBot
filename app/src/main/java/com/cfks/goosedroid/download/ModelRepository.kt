package com.cfks.goosedroid.download

import android.content.Context
import com.cfks.goosedroid.ai.EngineLogBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Manages local GGUF model files - scanning, validation, and metadata.
 */
class ModelRepository(private val context: Context) {

    private val modelsDir: File = File(context.filesDir, "models").apply { mkdirs() }

    data class LocalModel(
        val file: File,
        val name: String,
        val sizeBytes: Long,
        val sha256: String?,
        val catalogInfo: ModelCatalog.ModelInfo?
    )

    /** Scan for downloaded GGUF models and match with catalog */
    suspend fun getLocalModels(): List<LocalModel> = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val files = modelsDir.listFiles { _, name -> name.endsWith(".gguf") } ?: emptyArray()
        files.map { file ->
            val catalogMatch = ModelCatalog.allModels.find { it.filename == file.name }
            LocalModel(
                file = file,
                name = catalogMatch?.displayName ?: file.name.removeSuffix(".gguf"),
                sizeBytes = file.length(),
                sha256 = null, // Will be calculated on demand
                catalogInfo = catalogMatch
            )
        }.sortedByDescending { it.file.lastModified() }
    }

    /** Get the default models directory path for settings */
    fun getModelsDir(): File = modelsDir

    /** Delete a downloaded model */
    suspend fun deleteModel(file: File): Boolean = withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            if (file.exists() && file.delete()) {
                EngineLogBus.info("ModelRepository", "Deleted model: ${file.name}")
                true
            } else false
        } catch (e: Exception) {
            EngineLogBus.error("ModelRepository", "Delete failed: ${e.message}")
            false
        }
    }

    /** Validate a model file (checksum if available) */
    suspend fun validateModel(file: File): Boolean = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val catalogMatch = ModelCatalog.allModels.find { it.filename == file.name }
        catalogMatch?.sha256?.let { expectedSha ->
            val actualSha = calculateSha256(file)
            return@withContext actualSha.equals(expectedSha, ignoreCase = true)
        }
        // No checksum in catalog, just verify it's a valid GGUF (magic bytes)
        isValidGguf(file)
    }

    /** Quick GGUF magic bytes check */
    private fun isValidGguf(file: File): Boolean {
        return try {
            file.inputStream().use { input ->
                val buffer = ByteArray(4)
                val read = input.read(buffer)
                read == 4 && buffer.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46)) // "GGUF"
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun calculateSha256(file: File): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02X".format(it) }
    }
}