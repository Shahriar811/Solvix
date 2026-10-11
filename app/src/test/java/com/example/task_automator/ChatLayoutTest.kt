package com.example.task_automator

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** Headless Compose layout checks: no device, model engine or real app activity is launched. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class ChatLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val keyboardHeight = mutableStateOf(0)

    private fun render() {
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("viewport")) {
                    ChatLayout(
                        insets = WindowInsets(top = 24.dp, bottom = 24.dp)
                            .union(WindowInsets(bottom = keyboardHeight.value.dp)),
                        header = { Spacer(Modifier.fillMaxWidth().height(56.dp)) },
                        messages = { Box(Modifier.fillMaxSize().testTag("messages")) },
                        composer = { TextField("", {}, modifier = Modifier.fillMaxWidth().testTag("composer")) }
                    )
                }
            }
        }
    }

    private fun assertBottomInset(expected: Int) {
        val viewport = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        val composer = compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot
        assertEquals("Composer should sit directly above the bottom inset", expected.toFloat(), viewport.bottom - composer.bottom, 1f)
        val messages = compose.onNodeWithTag("messages").fetchSemanticsNode().boundsInRoot
        assertEquals("Messages must end at the composer", composer.top, messages.bottom, 1f)
    }

    @Test fun closedKeyboardKeepsComposerAtNavigationBar() {
        render()
        assertBottomInset(24)
    }

    @Test fun keyboardHeightIsAppliedOnceAndClosingRestoresBottom() {
        render()
        for (height in listOf(220, 300, 380, 0)) {
            compose.runOnIdle { keyboardHeight.value = height }
            assertBottomInset(maxOf(24, height))
        }
    }

    @Test fun focusingInputDoesNotIntroduceAnExtraKeyboardSizedGap() {
        keyboardHeight.value = 280
        render()
        compose.onNodeWithTag("composer").performClick()
        assertBottomInset(280)
    }
}
