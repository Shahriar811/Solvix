package com.example.task_automator

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.task_automator.ui.theme.TaskAutomatorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class ChatTranscriptTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var list: LazyListState
    private val chatId = mutableStateOf("first-chat")
    private val history = mutableStateOf(emptyList<ChatMessage>())
    private val sending = mutableStateOf(true)
    private val reply = mutableStateOf((1..90).joinToString("\n") { "Reply line $it" })

    private fun render() {
        compose.setContent {
            list = rememberLazyListState()
            TaskAutomatorTheme {
                val pending = if (sending.value) listOf(ChatMessage(role = ChatRole.USER, text = "Explain this"),
                    ChatMessage(role = ChatRole.ASSISTANT, text = reply.value)) else emptyList()
                ChatTranscript(chatId.value, history.value, pending,
                    modifier = Modifier.fillMaxWidth().height(400.dp).testTag("transcript"), state = list) {
                    if (it.role == ChatRole.ASSISTANT) AssistantText(it.text) else Text(it.text)
                }
            }
        }
        compose.waitForIdle()
    }
    private fun position(): Pair<Int, Int> {
        var result = 0 to 0
        compose.runOnIdle { result = list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }
        return result
    }
    private fun assertAtBottom() { compose.runOnIdle { assertFalse("The end of the reply should be visible", list.canScrollForward) } }
    private fun scrollAway() {
        compose.onNodeWithTag("transcript").performTouchInput { swipeDown(startY = 60f, endY = 310f, durationMillis = 500) }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("Gesture should move away from the bottom", list.canScrollForward) }
        compose.onNodeWithContentDescription("Scroll to latest").assertExists()
    }
    private fun appendChunks() {
        repeat(5) { chunk ->
            compose.runOnIdle { reply.value += "\nNew answer line $chunk" }
            compose.waitForIdle()
        }
    }

    @Test fun oversizedReplyFollowsItsEndInsteadOfJumpingToItsStart() {
        render()
        assertAtBottom()
        val initial = position()
        assertTrue(initial.second > 0)
        appendChunks()
        assertAtBottom()
        assertTrue(position().second > initial.second)
    }
    @Test fun manualReadingPositionSurvivesStreamingAndCompletion() {
        render()
        scrollAway()
        val reading = position()
        appendChunks()
        assertEquals(reading, position())
        compose.runOnIdle {
            history.value = listOf(ChatMessage(role = ChatRole.USER, text = "Explain this"),
                ChatMessage(role = ChatRole.ASSISTANT, text = reply.value))
            sending.value = false
        }
        compose.waitForIdle()
        assertEquals("Saving the reply must preserve the same reading position", reading, position())
    }
    @Test fun latestButtonResumesFollowingFutureText() {
        render()
        scrollAway()
        appendChunks()
        compose.onNodeWithContentDescription("Scroll to latest").performClick()
        compose.waitForIdle()
        assertAtBottom()
        appendChunks()
        assertAtBottom()
        compose.onNodeWithContentDescription("Scroll to latest").assertDoesNotExist()
    }
    @Test fun accessibilityScrollAlsoPausesFollowing() {
        render()
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(0f, -250f) }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(list.canScrollForward) }
        val reading = position()
        appendChunks()
        assertEquals(reading, position())
    }
    @Test fun openingAnotherConversationStartsAtItsLatestReply() {
        render()
        scrollAway()
        compose.runOnIdle { chatId.value = "second-chat"; reply.value = (1..110).joinToString("\n") { "Other reply $it" } }
        compose.waitForIdle()
        assertAtBottom()
    }
    @Test fun scrollingBackToTheBottomResumesFollowingWithoutPressingTheButton() {
        render()
        scrollAway()
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { scroll -> scroll(0f, 10000f) }
        compose.waitForIdle()
        assertAtBottom()
        appendChunks()
        assertAtBottom()
    }
}
