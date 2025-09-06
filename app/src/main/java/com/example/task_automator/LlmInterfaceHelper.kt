package com.example.task_automator.helpers

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.core.graphics.scale
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.genai.llminference.GraphOptions
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import java.io.File

object LlmInferenceHelper {
    private const val TAG = "LlmInferenceHelper"
    private var llmInference: LlmInference? = null
    private var session: LlmInferenceSession? = null

    // --- Initialization ---

    /**
     * Initializes the text-only instruction model.
     * Returns true if successful, false otherwise.
     */
    fun initInstructionModel(context: Context): Boolean {
        cleanup() // Clean up any existing session before initializing a new one

        Log.d(TAG, "Starting instruction model initialization...")

        return try {
            // Try multiple possible locations
            val possiblePaths = listOf(
                "/data/local/tmp/llm/gemma3-1b-it-int4.task",
                "/data/local/tmp/gemma3-1b-it-int4.task",
                "${context.filesDir}/gemma3-1b-it-int4.task",
                "${context.getExternalFilesDir(null)}/gemma3-1b-it-int4.task"
            )

            var modelPath: String? = null
            for (path in possiblePaths) {
                Log.d(TAG, "Checking path: $path")
                if (File(path).exists()) {
                    modelPath = path
                    Log.d(TAG, "Model found at: $path")
                    break
                }
            }

            if (modelPath == null) {
                // Try to copy from assets
                Log.d(TAG, "Model not found in any location, trying to copy from assets...")
                modelPath = copyModelFromAssets(context, "gemma3-1b-it-int4.task")
            }

            if (modelPath == null) {
                Log.e(TAG, "Model file not found in any location!")
                return false
            }

            Log.d(TAG, "Using model path: $modelPath")

            val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(128) // Reduced for Android 11
                .setPreferredBackend(LlmInference.Backend.CPU) // CPU for compatibility
                .build()

            Log.d(TAG, "Creating LlmInference...")
            llmInference = LlmInference.createFromOptions(context, inferenceOptions)
            Log.d(TAG, "LlmInference created successfully")

            Log.d(TAG, "Creating LlmInferenceSession...")
            session = LlmInferenceSession.createFromOptions(
                llmInference!!,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTopP(0.95f)
                    .setTemperature(0.7f)
                    .build()
            )

            Log.d(TAG, "Instruction model initialized successfully!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize instruction model", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Initializes the multimodal (text and image) model.
     * Returns true if successful, false otherwise.
     */
    fun initMultiModalModel(context: Context): Boolean {
        cleanup() // Clean up any existing session before initializing a new one

        Log.d(TAG, "Starting multimodal model initialization...")

        return try {
            // Try multiple possible locations
            val possiblePaths = listOf(
                "/data/local/tmp/llm/gemma-3n-E2B-it-int4.task",
                "/data/local/tmp/gemma-3n-E2B-it-int4.task",
                "${context.filesDir}/gemma-3n-E2B-it-int4.task",
                "${context.getExternalFilesDir(null)}/gemma-3n-E2B-it-int4.task"
            )

            var modelPath: String? = null
            for (path in possiblePaths) {
                Log.d(TAG, "Checking path: $path")
                if (File(path).exists()) {
                    modelPath = path
                    Log.d(TAG, "Model found at: $path")
                    break
                }
            }

            if (modelPath == null) {
                // Try to copy from assets
                Log.d(TAG, "Model not found in any location, trying to copy from assets...")
                modelPath = copyModelFromAssets(context, "gemma-3n-E2B-it-int4.task")
            }

            if (modelPath == null) {
                Log.e(TAG, "Model file not found in any location!")
                return false
            }

            Log.d(TAG, "Using model path: $modelPath")

            val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512) // Reduced for Android 11
                .setMaxNumImages(1)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()

            Log.d(TAG, "Creating LlmInference...")
            llmInference = LlmInference.createFromOptions(context, inferenceOptions)
            Log.d(TAG, "LlmInference created successfully")

            Log.d(TAG, "Creating LlmInferenceSession...")
            session = LlmInferenceSession.createFromOptions(
                llmInference!!,
                LlmInferenceSession.LlmInferenceSessionOptions.builder()
                    .setTopK(40)
                    .setTopP(0.95f)
                    .setTemperature(0.8f)
                    .setGraphOptions(
                        GraphOptions.builder()
                            .setEnableVisionModality(true)
                            .build()
                    )
                    .build()
            )

            Log.d(TAG, "Multimodal model initialized successfully!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize multimodal model", e)
            e.printStackTrace()
            false
        }
    }

    /**
     * Copies a model from assets to internal storage if it exists
     */
    private fun copyModelFromAssets(context: Context, modelFileName: String): String? {
        return try {
            val modelFile = File(context.filesDir, modelFileName)

            // Check if file already exists
            if (modelFile.exists()) {
                Log.d(TAG, "Model already exists in internal storage: ${modelFile.absolutePath}")
                return modelFile.absolutePath
            }

            // Check if asset exists
            val assetList = context.assets.list("") ?: emptyArray()
            if (!assetList.contains(modelFileName)) {
                Log.e(TAG, "Model not found in assets: $modelFileName")
                return null
            }

            Log.d(TAG, "Copying model from assets to internal storage...")
            context.assets.open(modelFileName).use { inputStream ->
                modelFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            Log.d(TAG, "Model copied successfully to: ${modelFile.absolutePath}")
            modelFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model from assets", e)
            null
        }
    }

    // --- Synchronous Inference ---

    fun generateResponse(prompt: String): String {
        Log.d(TAG, "generateResponse called with prompt: $prompt")

        if (session == null) {
            Log.e(TAG, "Session is null!")
            return "Error: Model not initialized. Please ensure model files are available."
        }

        return try {
            Log.d(TAG, "Adding query chunk...")
            session?.addQueryChunk(prompt)

            Log.d(TAG, "Generating response...")
            val response = session?.generateResponse() ?: "No response generated"

            Log.d(TAG, "Response generated: ${response.take(100)}...")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            e.printStackTrace()
            "Error: ${e.message}"
        }
    }

    fun generateMultiModalResponse(
        prompt: String,
        bitmap: Bitmap
    ): String {
        Log.d(TAG, "generateMultiModalResponse called")

        if (session == null) {
            Log.e(TAG, "Session is null!")
            return "Error: Model not initialized. Please ensure model files are available."
        }

        return try {
            // Scale bitmap to 224x224 for better compatibility
            Log.d(TAG, "Scaling bitmap...")
            val scaledBitmap = bitmap.scale(224, 224)
            val mpImage = BitmapImageBuilder(scaledBitmap).build()

            Log.d(TAG, "Adding query and image...")
            session?.addQueryChunk(prompt)
            session?.addImage(mpImage)

            Log.d(TAG, "Generating response...")
            val response = session?.generateResponse() ?: "No response generated"

            Log.d(TAG, "Response generated: ${response.take(100)}...")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Error generating multimodal response", e)
            e.printStackTrace()
            "⚠️ Failed to process image: ${e.message}"
        }
    }

    // --- Resource Management ---

    fun reset() {
        Log.d(TAG, "Resetting session...")
        session?.close()
        session = null
    }

    fun cleanup() {
        Log.d(TAG, "Cleaning up resources...")
        session?.close()
        session = null
        llmInference?.close()
        llmInference = null
    }
}