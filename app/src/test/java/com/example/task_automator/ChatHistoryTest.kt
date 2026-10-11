package com.example.task_automator

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChatHistoryTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun keepsExactlyTenNewestUnpinnedChats() {
        val chats = (1..15).map { SavedChat(id = "$it", updatedAt = it.toLong()) }
        assertEquals((15 downTo 6).map { "$it" }, ChatRetention.keep(chats).map { it.id })
    }
    @Test fun oldPinnedChatsAreExemptFromRecentLimit() {
        val chats = (1..18).map { SavedChat(id = "$it", updatedAt = it.toLong(), pinned = it <= 3) }
        val kept = ChatRetention.keep(chats)
        assertEquals(13, kept.size)
        assertEquals(setOf("1", "2", "3"), kept.filter { it.pinned }.map { it.id }.toSet())
        assertEquals((18 downTo 9).map { "$it" }, kept.filterNot { it.pinned }.map { it.id })
    }
    @Test fun unpinningOldChatMakesItEligibleForRemoval() {
        val chats = (1..12).map { SavedChat(id = "$it", updatedAt = it.toLong(), pinned = it == 1) }
        val kept = ChatRetention.keep(chats)
        assertTrue(kept.any { it.id == "1" })
        assertFalse(ChatRetention.keep(kept.map { it.copy(pinned = false) }).any { it.id == "1" })
    }
    @Test fun historyAndPhotoSurviveReopeningTheStore() {
        val directory = folder.newFolder()
        val store = ChatHistoryStore(directory)
        val image = store.putImage(byteArrayOf(1, 2, 3))
        val chat = SavedChat(title = "বাংলা chat", pinned = true, messages = listOf(ChatMessage(role = ChatRole.USER, text = "Hello", imageName = image), ChatMessage(role = ChatRole.ASSISTANT, text = "Answer")))
        store.save(listOf(chat))
        val reopened = ChatHistoryStore(directory)
        assertEquals(listOf(chat), reopened.load())
        assertArrayEquals(byteArrayOf(1, 2, 3), reopened.imageFile(image).readBytes())
    }
    @Test fun prunedPhotosAreRemovedButPinnedAndDraftPhotosStay() {
        val store = ChatHistoryStore(folder.newFolder())
        val pinned = store.putImage(byteArrayOf(1))
        val removed = store.putImage(byteArrayOf(2))
        val draft = store.putImage(byteArrayOf(3))
        val chats = listOf(SavedChat(pinned = true, messages = listOf(ChatMessage(role = ChatRole.USER, text = "Image", imageName = pinned))))
        store.save(chats); store.removeUnusedImages(chats, draft)
        assertTrue(store.imageFile(pinned).exists()); assertTrue(store.imageFile(draft).exists()); assertFalse(store.imageFile(removed).exists())
    }
    @Test fun unfinishedWriteCannotReplaceSavedHistory() {
        val directory = folder.newFolder()
        val store = ChatHistoryStore(directory)
        val chat = SavedChat(title = "Keep me", pinned = true)
        store.save(listOf(chat)); directory.resolve("history.json.tmp").writeText("broken")
        assertEquals(listOf(chat), store.load())
    }
    @Test fun corruptHistoryAndUnsafeAttachmentPathsAreRejected() {
        val directory = folder.newFolder()
        val store = ChatHistoryStore(directory)
        directory.resolve("history.json").writeText("not JSON")
        assertThrows(Exception::class.java) { store.load() }
        assertThrows(IllegalArgumentException::class.java) { store.imageFile("../private.jpg") }
        assertEquals("not JSON", directory.resolve("history.json").readText())
    }
}
