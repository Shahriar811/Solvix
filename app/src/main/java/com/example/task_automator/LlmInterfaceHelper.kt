package com.example.task_automator.helpers

import android.content.Context
import android.graphics.Bitmap
import com.example.task_automator.ChatMessage
import com.example.task_automator.ChatRole
import com.google.ai.edge.litertlm.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One warm engine; native context is reused only for the selected conversation. */
class LlmInferenceHelper {
    private val mutex = Mutex()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var chatId: String? = null
    private var cachedHistory: List<Triple<ChatRole, String, String?>> = emptyList()
    var supportsImages = false
        private set

    suspend fun initialize(context: Context, modelPath: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            closeResources()
            try {
                openEngine(context, modelPath, true)
                supportsImages = true
            } catch (visionError: Exception) {
                closeResources()
                try { openEngine(context, modelPath, false) }
                catch (textError: Exception) { closeResources(); textError.addSuppressed(visionError); throw textError }
            }
        }
    }
    private fun openEngine(context: Context, path: String, vision: Boolean) {
        val candidate = Engine(EngineConfig(
            modelPath = path, backend = Backend.CPU(numOfThreads = 4),
            visionBackend = if (vision) Backend.GPU() else null,
            maxNumTokens = 4096, maxNumImages = 1,
            cacheDir = context.cacheDir.resolve("gemma4").apply { mkdirs() }.absolutePath
        ))
        try { candidate.initialize(); engine = candidate }
        catch (error: Exception) { if (candidate.isInitialized()) candidate.close(); throw error }
    }
    private fun newConversation(history: List<Message> = emptyList()) = checkNotNull(engine).createConversation(ConversationConfig(
        systemInstruction = Contents.of("You are Solvix, a helpful on-device assistant. Give clear, concise answers."),
        initialMessages = history,
        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7),
        extraContext = mapOf("enable_thinking" to false)
    ))
    private fun contents(text: String, image: ByteArray?): Contents {
        val values = mutableListOf<Content>()
        image?.let { values.add(Content.ImageBytes(it)) }
        values.add(Content.Text(text))
        return Contents.of(values)
    }

    suspend fun streamResponse(
        selectedChatId: String, history: List<ChatMessage>, prompt: String,
        imageName: String?, imageFile: (String) -> File, onPartial: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            checkNotNull(engine) { "Download or import the offline model first." }
            if (imageName != null) check(supportsImages) { "Images are unavailable on this device. Text chat is ready." }
            val signature = history.map { Triple(it.role, it.text, it.imageName) }
            if (chatId != selectedChatId || cachedHistory != signature || conversation == null) {
                conversation?.close()
                conversation = null
                val initial = history.map { message ->
                    if (message.role == ChatRole.ASSISTANT) Message.model(message.text)
                    else {
                        val bytes = message.imageName?.let { name ->
                            check(supportsImages) { "This conversation contains images that this device cannot process." }
                            imageFile(name).readBytes()
                        }
                        Message.user(contents(message.text, bytes))
                    }
                }
                conversation = newConversation(initial)
                chatId = selectedChatId
                cachedHistory = signature
            }
            val active = checkNotNull(conversation)
            val chunks = Channel<Message>(Channel.UNLIMITED)
            val terminal = CompletableDeferred<Unit>()
            val answer = StringBuilder()
            var lastEmission = 0L
            try {
                active.sendMessageAsync(contents(prompt, imageName?.let { imageFile(it).readBytes() }), object : MessageCallback {
                    override fun onMessage(message: Message) { chunks.trySend(message) }
                    override fun onDone() { terminal.complete(Unit); chunks.close() }
                    override fun onError(throwable: Throwable) { terminal.complete(Unit); chunks.close(throwable) }
                })
                for (chunk in chunks) {
                    answer.append(chunk.toString())
                    val now = System.nanoTime()
                    if (now - lastEmission >= 50_000_000L) { onPartial(answer.toString()); lastEmission = now }
                }
                onPartial(answer.toString())
                cachedHistory = signature + listOf(Triple(ChatRole.USER, prompt, imageName), Triple(ChatRole.ASSISTANT, answer.toString(), null))
                answer.toString()
            } catch (error: CancellationException) {
                // Keep the mutex until the native callback confirms termination. Closing early can crash JNI.
                withContext(NonCancellable) {
                    active.cancelProcess()
                    terminal.await()
                    onPartial(answer.toString())
                    active.close()
                    conversation = null
                    chatId = null
                }
                throw error
            } catch (error: Exception) {
                active.close()
                conversation = null
                chatId = null
                throw error
            } finally { chunks.cancel() }
        }
    }

    // Kept for the opt-in native smoke test.
    suspend fun generateResponse(prompt: String, bitmap: Bitmap? = null): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val active = conversation ?: newConversation().also { conversation = it }
            val bytes = bitmap?.let { ByteArrayOutputStream().use { output -> it.compress(Bitmap.CompressFormat.JPEG, 90, output); output.toByteArray() } }
            active.sendMessage(contents(prompt, bytes)).toString()
        }
    }
    suspend fun cleanup() = withContext(Dispatchers.IO) { mutex.withLock { closeResources() } }
    private fun closeResources() {
        try { conversation?.close() } finally {
            conversation = null; chatId = null; cachedHistory = emptyList()
            engine?.let { if (it.isInitialized()) it.close() }
            engine = null; supportsImages = false
        }
    }
}
