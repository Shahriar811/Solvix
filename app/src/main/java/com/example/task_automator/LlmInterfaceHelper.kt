package com.example.task_automator.helpers

import android.content.Context
import android.graphics.Bitmap
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes native operations so navigation cannot close an engine during inference. */
class LlmInferenceHelper {
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    var supportsImages: Boolean = false
        private set

    suspend fun initialize(context: Context, modelPath: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeResources()
            // The vision encoder requires a supported GPU. Keep text usable if it is unavailable.
            try {
                openEngine(context, modelPath, withVision = true)
                supportsImages = true
            } catch (visionError: Exception) {
                closeResources()
                try {
                    openEngine(context, modelPath, withVision = false)
                } catch (textError: Exception) {
                    closeResources()
                    textError.addSuppressed(visionError)
                    throw textError
                }
            }
        }
    }

    private fun openEngine(context: Context, modelPath: String, withVision: Boolean) {
        val candidate = Engine(
            EngineConfig(
                modelPath = modelPath,
                backend = Backend.CPU(numOfThreads = 4),
                visionBackend = if (withVision) Backend.GPU() else null,
                maxNumTokens = 4096,
                maxNumImages = 1,
                cacheDir = context.cacheDir.resolve("gemma4").apply { mkdirs() }.absolutePath
            )
        )
        try {
            candidate.initialize()
            engine = candidate
            conversation = newConversation(candidate)
        } catch (error: Exception) {
            if (candidate.isInitialized()) candidate.close()
            engine = null
            throw error
        }
    }

    private fun newConversation(engine: Engine): Conversation = engine.createConversation(
        ConversationConfig(
            systemInstruction = Contents.of("You are Solvix, a helpful on-device assistant. Give clear, concise answers."),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7),
            extraContext = mapOf("enable_thinking" to false)
        )
    )

    suspend fun generateResponse(prompt: String, bitmap: Bitmap? = null): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val activeEngine = checkNotNull(engine) { "Download or import Gemma 4 first." }
                if (bitmap != null) check(supportsImages) {
                    "Image analysis needs a compatible GPU. Text chat is available on this device."
                }
                val contents = mutableListOf<Content>()
                if (bitmap != null) {
                    val bytes = ByteArrayOutputStream().use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                            "Unable to read the attached image."
                        }
                        output.toByteArray()
                    }
                    contents.add(Content.ImageBytes(bytes))
                }
                contents.add(Content.Text(prompt.ifBlank { "Describe this image." }))
                try {
                    checkNotNull(conversation).sendMessage(Contents.of(contents)).toString()
                } catch (error: Exception) {
                    // A failed request (including a full context window) must not poison later turns.
                    conversation?.close()
                    conversation = newConversation(activeEngine)
                    throw error
                }
            }
        }

    suspend fun cleanup() = withContext(Dispatchers.IO) {
        mutex.withLock { closeResources() }
    }

    private fun closeResources() {
        conversation?.close()
        conversation = null
        engine?.let { if (it.isInitialized()) it.close() }
        engine = null
        supportsImages = false
    }
}
