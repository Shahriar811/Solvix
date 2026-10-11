package com.example.task_automator

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Compiled locally; run on a device only when interactive testing is allowed. */
class ChatHistoryUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun savedConversationHasOpenPinAndDeleteActions() {
        var opened = false
        var pinned = false
        var deleted = false
        compose.setContent {
            MaterialTheme { HistoryRow(SavedChat(title = "Trip ideas"), false, true,
                { opened = true }, { pinned = true }, { deleted = true }) }
        }
        compose.onNodeWithText("Trip ideas").performClick()
        compose.onNodeWithContentDescription("Pin chat").performClick()
        compose.onNodeWithContentDescription("Delete chat").performClick()
        assertTrue(opened && pinned && deleted)
    }
    @Test fun pinnedChatHasExplicitUnpinAction() {
        var unpinned = false
        compose.setContent {
            MaterialTheme { HistoryRow(SavedChat(title = "Keep me", pinned = true), true, true,
                {}, { unpinned = true }, {}) }
        }
        compose.onNodeWithContentDescription("Unpin chat").performClick()
        assertTrue(unpinned)
    }
}
