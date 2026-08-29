package com.cfks.goosedroid.download

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Manages local GGUF model files.
 * Priority: 1) Bundled assets (copied on first run) 2) User-downloaded/imported models in internal storage
 */
class ModelRepository(private val context: Context) {

    private val TAG = "ModelRepository"
    val modelsDir = File(context.filesDir, "models")
    private val BUNDLED_MODEL_NAME = "SmolLM-135M-Instruct-v0.2-Q4_K_M.gguf"

    init {
        modelsDir.mkdirs()
        copyBundledModelIfNeeded()
    }

    data class LocalModel(
        val file: File,
        val name: String,
        val sizeBytes: Long,
        val catalogInfo: ModelCatalog.ModelInfo?
    )

    fun getLocalModels(): List<LocalModel> {
        val list = mutableListOf<LocalModel>()

        // 1) Internal storage (modelsDir) - Includes bundled and imported
        if (modelsDir.exists()) {
            modelsDir.listFiles()?.filter { it.extension == "gguf" }?.forEach { file ->
                val catalogModel = ModelCatalog.getModelById(file.nameWithoutExtension)
                    ?: ModelCatalog.allModels.find { it.filename == file.name }
                list.add(LocalModel(
                    file = file,
                    name = file.name,
                    sizeBytes = file.length(),
                    catalogInfo = catalogModel
                ))
            }
        }

        // 2) User-downloaded models (external storage / download dir) - Optional legacy support
        val downloadDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
        if (downloadDir != null && downloadDir.exists()) {
            downloadDir.listFiles()?.filter { it.extension == "gguf" }?.forEach { file ->
                // Avoid duplicates if already in internal modelsDir
                if (list.none { it.file.name == file.name }) {
                    val catalogModel = ModelCatalog.getModelById(file.nameWithoutExtension)
                        ?: ModelCatalog.allModels.find { it.filename == file.name }
                    list.add(LocalModel(
                        file = file,
                        name = file.name,
                        sizeBytes = file.length(),
                        catalogInfo = catalogModel
                    ))
                }
            }
        }

        return list.distinctBy { it.file.absolutePath }
    }

    fun deleteModel(file: File): Boolean {
        return if (file.exists() && file.delete()) {
            Log.i(TAG, "Deleted model: ${file.name}")
            true
        } else false
    }

    /**
     * Imports a model from a Uri (usually from File Picker) into internal storage.
     * Native llama.cpp requires a direct file path.
     */
    fun importModelFromUri(uri: Uri): File? {
        try {
            var fileName = "imported_model_${System.currentTimeMillis()}.gguf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            val destFile = File(modelsDir, fileName)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Imported model to: ${destFile.absolutePath} (${destFile.length()} bytes)")
            return destFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import model from Uri: $uri", e)
            return null
        }
    }

    /**
     * Copy bundled model from assets to internal storage on first run.
     * This ensures the model is available offline immediately after install.
     */
    private fun copyBundledModelIfNeeded() {
        val dest = File(modelsDir, BUNDLED_MODEL_NAME)
        if (dest.exists()) return // already copied

        Log.i(TAG, "Copying bundled model from assets...")
        try {
            context.assets.open("models/$BUNDLED_MODEL_NAME").use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Bundled model ready: ${dest.length()} bytes")
        } catch (e: Exception) {
            Log.w(TAG, "Bundled model not found in assets (expected if not added yet): ${e.message}")
            dest.delete() // cleanup partial
        }
    }
}