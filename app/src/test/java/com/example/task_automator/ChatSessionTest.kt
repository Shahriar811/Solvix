package com.example.task_automator

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionTest {
    @Test fun successStreamsThenSavesOneConversation() = runTest {
        val done = CompletableDeferred<Unit>()
        var saved = emptyList<SavedChat>()
        val session = ChatSession(this, { _, _, _, emit -> emit("First words"); done.await(); "Complete answer" }, { chats, _ -> saved = chats })
        session.restore(emptyList()); session.updateDraft("Hello"); session.send(); runCurrent()
        assertEquals("First words", session.state.value.partialReply)
        assertTrue(session.state.value.sending)
        assertTrue(saved.isEmpty())
        done.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("Hello", "Complete answer"), saved.single().messages.map { it.text })
        assertEquals("", session.state.value.draft)
        assertFalse(session.state.value.busy)
    }
    @Test fun failureKeepsDraftAndDoesNotCreateEmptyHistory() = runTest {
        var attempts = 0
        val session = ChatSession(this, { _, _, _, _ -> if (++attempts == 1) throw IOException("Retry"); "Answer" }, { _, _ -> })
        session.restore(emptyList()); session.updateDraft("Keep this"); session.send(); advanceUntilIdle()
        assertEquals("Keep this", session.state.value.draft)
        assertTrue(session.state.value.chats.isEmpty())
        assertFalse(session.state.value.busy)
        session.send(); advanceUntilIdle()
        assertEquals(2, session.state.value.current.messages.size)
        assertNull(session.state.value.error)
    }
    @Test fun repeatedSendAndSwitchDuringInferenceAreBlocked() = runTest {
        val done = CompletableDeferred<String>()
        var calls = 0
        val session = ChatSession(this, { _, _, _, _ -> calls++; done.await() }, { _, _ -> })
        session.restore(emptyList()); session.updateDraft("Question")
        val id = session.state.value.current.id
        session.send(); session.send(); session.newChat(); runCurrent()
        assertEquals(1, calls); assertEquals(id, session.state.value.current.id)
        done.complete("Answer"); advanceUntilIdle()
        assertEquals(1, session.state.value.chats.size)
    }
    @Test fun reopeningHistoryPassesOnlyThatConversationsContext() = runTest {
        val first = SavedChat(id = "first", title = "First", messages = listOf(ChatMessage(role = ChatRole.USER, text = "Earlier"), ChatMessage(role = ChatRole.ASSISTANT, text = "Old reply")))
        val other = SavedChat(id = "other", title = "Other")
        var received: SavedChat? = null
        val session = ChatSession(this, { chat, _, _, _ -> received = chat; "New reply" }, { _, _ -> })
        session.restore(listOf(first, other)); session.open(first.id); session.updateDraft("Follow up"); session.send(); advanceUntilIdle()
        assertEquals(first, received)
        assertEquals(4, session.state.value.current.messages.size)
    }
    @Test fun pinAndDeletePersistAndExplicitDeletionRemovesPinnedChat() = runTest {
        var saved = emptyList<SavedChat>()
        val first = SavedChat(title = "Saved", messages = listOf(ChatMessage(role = ChatRole.USER, text = "Question")))
        val session = ChatSession(this, { _, _, _, _ -> "Reply" }, { chats, _ -> saved = chats })
        session.restore(listOf(first)); session.open(first.id); session.pin(first.id); advanceUntilIdle()
        assertTrue(saved.single().pinned)
        session.delete(first.id); advanceUntilIdle()
        assertTrue(saved.isEmpty()); assertTrue(session.state.value.current.messages.isEmpty())
    }
    @Test fun failedPersistenceKeepsHistoryAndDraft() = runTest {
        val session = ChatSession(this, { _, _, _, _ -> "Answer" }, { _, _ -> throw IOException("Disk full") })
        session.restore(emptyList()); session.updateDraft("Unsaved"); session.send(); advanceUntilIdle()
        assertEquals("Unsaved", session.state.value.draft)
        assertTrue(session.state.value.chats.isEmpty()); assertFalse(session.state.value.busy)
        assertTrue(session.state.value.error!!.contains("save"))
    }
    @Test fun stopKeepsPartialAnswerAndAllowsAnotherTurn() = runTest {
        val session = ChatSession(this, { _, _, _, emit -> emit("Partial answer"); awaitCancellation() }, { _, _ -> })
        session.restore(emptyList()); session.updateDraft("Question"); session.send(); runCurrent()
        session.stop(); advanceUntilIdle()
        assertEquals("Partial answer", session.state.value.current.messages.last().text)
        assertFalse(session.state.value.busy)
    }
    @Test fun sendsAreBlockedBeforeHistoryLoadAndWhilePickingImage() = runTest {
        var calls = 0
        val session = ChatSession(this, { _, _, _, _ -> calls++; "Answer" }, { _, _ -> })
        session.updateDraft("Question"); session.send(); advanceUntilIdle(); assertEquals(0, calls)
        session.restore(emptyList()); session.setLoadingImage(true); session.send(); advanceUntilIdle(); assertEquals(0, calls)
    }
    @Test fun stoppingImmediatelyDoesNotLeaveComposerDisabled() = runTest {
        val session = ChatSession(this, { _, _, _, _ -> awaitCancellation() }, { _, _ -> })
        session.restore(emptyList()); session.updateDraft("Question"); session.send(); session.stop(); advanceUntilIdle()
        assertFalse(session.state.value.busy)
        assertEquals("Question", session.state.value.draft)
    }
}
