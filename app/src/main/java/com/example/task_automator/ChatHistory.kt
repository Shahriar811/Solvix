package com.example.task_automator

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

enum class ChatRole { USER, ASSISTANT }
data class ChatMessage(val id: String = UUID.randomUUID().toString(), val role: ChatRole, val text: String, val imageName: String? = null)
data class SavedChat(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New chat",
    val updatedAt: Long = System.currentTimeMillis(),
    val pinned: Boolean = false,
    val messages: List<ChatMessage> = emptyList()
)

object ChatRetention {
    const val RECENT_LIMIT = 10
    fun keep(chats: List<SavedChat>): List<SavedChat> {
        val ordered = chats.sortedByDescending { it.updatedAt }
        val recent = ordered.filterNot { it.pinned }.take(RECENT_LIMIT).map { it.id }.toSet()
        return ordered.filter { it.pinned || it.id in recent }
    }
}

/** Private, durable local history. Images follow their conversation's retention policy. */
class ChatHistoryStore(val directory: File) {
    private val gson = Gson()
    private val historyFile = directory.resolve("history.json")
    private val imageDirectory = directory.resolve("images")

    @Synchronized fun load(): List<SavedChat> {
        if (!historyFile.exists()) return emptyList()
        val type = object : TypeToken<List<SavedChat>>() {}.type
        val chats: List<SavedChat> = historyFile.reader().use { gson.fromJson(it, type) }
            ?: error("Saved history is unreadable.")
        require(chats.map { it.id }.distinct().size == chats.size)
        chats.forEach { chat ->
            require(chat.id.isNotBlank() && chat.title.isNotBlank())
            chat.messages.forEach { message ->
                require(message.id.isNotBlank())
                require(message.role == ChatRole.USER || message.role == ChatRole.ASSISTANT)
                require(message.text.isNotBlank() || message.imageName != null)
                message.imageName?.let { require(it.matches(Regex("[a-f0-9-]+\\.jpg"))) }
            }
        }
        return ChatRetention.keep(chats)
    }

    @Synchronized fun save(chats: List<SavedChat>) {
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create chat storage." }
        val staged = directory.resolve("history.json.tmp")
        FileOutputStream(staged).use { output ->
            output.write(gson.toJson(ChatRetention.keep(chats)).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        try {
            Files.move(staged.toPath(), historyFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(staged.toPath(), historyFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
    fun putImage(bytes: ByteArray): String {
        check(imageDirectory.isDirectory || imageDirectory.mkdirs()) { "Cannot store the image." }
        val name = "${UUID.randomUUID()}.jpg"
        imageFile(name).writeBytes(bytes)
        return name
    }
    fun imageFile(name: String): File {
        require(name.matches(Regex("[a-f0-9-]+\\.jpg")))
        return imageDirectory.resolve(name)
    }
    fun removeUnusedImages(chats: List<SavedChat>, draftImage: String? = null) {
        val used = chats.flatMap { it.messages }.mapNotNull { it.imageName }.toSet() + listOfNotNull(draftImage)
        imageDirectory.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }
}
