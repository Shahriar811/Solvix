package com.example.task_automator

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionTest {
    @Test fun successfulReplyClearsDraftAndKeepsBothMessages() = runTest {
        val session = ChatSession(this) { prompt, _ -> "Reply to $prompt" }
        session.updateDraft("Hello")
        session.send()
        assertTrue(session.state.value.sending)
        advanceUntilIdle()
        assertEquals(listOf("You: Hello", "Bot: Reply to Hello"), session.state.value.messages)
        assertEquals("", session.state.value.draft)
        assertFalse(session.state.value.busy)
    }

    @Test fun failedReplyPreservesDraftAndRetryDoesNotDuplicateUserMessage() = runTest {
        var attempts = 0
        val session = ChatSession(this) { _, _ ->
            if (++attempts == 1) throw IOException("Try again")
            "Recovered"
        }
        session.updateDraft("Keep this")
        session.send()
        advanceUntilIdle()
        assertEquals("Keep this", session.state.value.draft)
        assertEquals("Try again", session.state.value.error)
        assertTrue(session.state.value.messages.isEmpty())
        assertFalse(session.state.value.busy)
        session.send()
        advanceUntilIdle()
        assertNull(session.state.value.error)
        assertEquals(listOf("You: Keep this", "Bot: Recovered"), session.state.value.messages)
    }

    @Test fun repeatedSendDuringInferenceStartsOnlyOneRequest() = runTest {
        val reply = CompletableDeferred<String>()
        var calls = 0
        val session = ChatSession(this) { _, _ -> calls++; reply.await() }
        session.updateDraft("Question")
        session.send()
        session.send()
        runCurrent()
        assertEquals(1, calls)
        session.addMessage("Notification from App: New message")
        reply.complete("Answer")
        advanceUntilIdle()
        assertEquals(3, session.state.value.messages.size)
        assertEquals("Bot: Answer", session.state.value.messages.last())
    }

    @Test fun emptyDraftAndImageLoadingDoNotStartInference() = runTest {
        var calls = 0
        val session = ChatSession(this) { _, _ -> calls++; "Answer" }
        session.updateDraft("   ")
        session.send()
        session.updateDraft("Question")
        session.setLoadingImage(true)
        session.send()
        advanceUntilIdle()
        assertEquals(0, calls)
        assertEquals("Question", session.state.value.draft)
    }
}
