package com.example.task_automator

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.task_automator.helpers.ModelInstallState
import com.example.task_automator.ui.components.GemmaModelPanel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GemmaModelPanelTest {
    @get:Rule val compose = createComposeRule()

    @Test fun importOpensPickerAndDownloadUsesWifiByDefault() {
        var imports = 0
        var wifiOnly: Boolean? = null
        compose.setContent {
            MaterialTheme {
                GemmaModelPanel(ModelInstallState(), ChatModelState(),
                    onImport = { imports++ }, onDownload = { wifiOnly = it },
                    onCancel = {}, onRetryLoading = {})
            }
        }
        compose.onNodeWithText("Import").performClick()
        assertEquals(1, imports)
        compose.onNodeWithText("Download").performClick()
        compose.onNodeWithText("Download Gemma 4?").assertIsDisplayed()
        compose.onNodeWithText("Start download").performClick()
        assertEquals(true, wifiOnly)
    }

    @Test fun transferCanBeCancelledAndDuplicateInstallActionsAreHidden() {
        var cancelled = false
        compose.setContent {
            MaterialTheme {
                GemmaModelPanel(
                    ModelInstallState(busy = true, canCancel = true, label = "Downloading from Hugging Face…", progress = 0.5f),
                    ChatModelState(), onImport = {}, onDownload = {},
                    onCancel = { cancelled = true }, onRetryLoading = {})
            }
        }
        compose.onNodeWithText("50%").assertIsDisplayed()
        compose.onNodeWithText("Import").assertDoesNotExist()
        compose.onNodeWithText("Download").assertDoesNotExist()
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
    }

    @Test fun installedBundleShowsOfflineMultimodalStatus() {
        compose.setContent {
            MaterialTheme {
                GemmaModelPanel(ModelInstallState(installedPath = "/model.litertlm"),
                    ChatModelState(ready = true, supportsImages = true),
                    onImport = {}, onDownload = {}, onCancel = {}, onRetryLoading = {})
            }
        }
        compose.onNodeWithText("Ready for offline text chat and image analysis.").assertIsDisplayed()
        compose.onNodeWithText("Download").assertDoesNotExist()
    }
}
