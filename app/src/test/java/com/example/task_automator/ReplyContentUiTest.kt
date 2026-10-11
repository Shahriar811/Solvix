package com.example.task_automator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
class ReplyContentUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var clipboard: ClipboardManager
    private fun answer(text: String, darkTheme: Boolean = false) {
        compose.setContent {
            clipboard = LocalClipboardManager.current
            TaskAutomatorTheme(darkTheme) { Column(Modifier.verticalScroll(rememberScrollState())) { AssistantText(text) } }
        }
    }
    @Test fun codeCopiesOnlyExactCodeAndAnswerCopiesEntireReply() {
        val code = "  const value = 42;  \n  return value;"
        val reply = "Here is the code:\n```typescript\n$code\n```\nUse it in your app."
        answer(reply)
        compose.onNodeWithContentDescription("Copy code").performClick()
        compose.runOnIdle { assertEquals(code, clipboard.getText()?.text) }
        compose.onNodeWithText("Copied").assertExists()
        compose.onNodeWithContentDescription("Copy answer").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(reply, clipboard.getText()?.text) }
    }
    @Test fun plainAnswersHaveCopyWithConfirmation() {
        answer("A normal answer without code.")
        compose.onNodeWithContentDescription("Copy code").assertDoesNotExist()
        compose.onNodeWithContentDescription("Copy answer").performClick()
        compose.runOnIdle { assertEquals("A normal answer without code.", clipboard.getText()?.text) }
        compose.onNodeWithText("Copied").assertExists()
    }
    @Test fun eachCodeBlockCopiesItsOwnContentAndLongLinesCanScroll() {
        val longLine = "print(\"" + "long line ".repeat(30) + "\")"
        answer("```python\n$longLine\n```\n```json\n{}\n```", darkTheme = true)
        val buttons = compose.onAllNodesWithContentDescription("Copy code")
        buttons.assertCountEquals(2)
        buttons[1].performScrollTo().performClick()
        compose.runOnIdle { assertEquals("{}", clipboard.getText()?.text) }
        compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().also { nodes ->
            assertTrue(nodes.any { it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange) })
        }
        val displayed = compose.onNodeWithText(longLine, useUnmergedTree = true).fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsProperties.Text].single()
        assertEquals(longLine, displayed.text)
        assertTrue(displayed.spanStyles.isNotEmpty())
    }
}
