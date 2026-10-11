package com.example.task_automator

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.task_automator.helpers.*
import com.example.task_automator.ui.components.ModelLibraryPanel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ModelSetupPanelTest {
    @get:Rule val compose = createComposeRule()
    private val phone = DeviceHardware("Google Pixel 7 Pro", "Tensor G2", 35, listOf("arm64-v8a"), true, 12 * GIB, 10 * GIB, 100 * GIB, 8, false)
    @Test fun importIsPreflightedAndPassesSelectedModelToPicker() {
        var imported: String? = null
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ModelLibraryPanel(ModelInstallState(initialized = true, hardware = phone), ChatModelState(), false,
                onImport = { imported = it }, onDownload = { _, _ -> }, onUse = {}, onRemove = {}, onCancel = {}, onRefresh = {}, onSource = {})
        } } }
        compose.onNodeWithText("Import").performScrollTo().performClick()
        assertNull(imported)
        compose.onNodeWithText("Choose file").performClick()
        assertEquals(ModelCatalog.DEFAULT_ID, imported)
    }
    @Test fun downloadUsesWifiAndUnsupportedPhoneCannotInstall() {
        var wifi: Boolean? = null
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ModelLibraryPanel(ModelInstallState(initialized = true, hardware = phone), ChatModelState(), false,
                onImport = {}, onDownload = { _, value -> wifi = value }, onUse = {}, onRemove = {}, onCancel = {}, onRefresh = {}, onSource = {})
        } } }
        compose.onNodeWithText("Download").performScrollTo().performClick()
        compose.onNodeWithText("Start download").performClick()
        assertEquals(true, wifi)
    }
    @Test fun inProgressTransferDisablesDuplicateActionsAndCanCancel() {
        var cancelled = false
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ModelLibraryPanel(ModelInstallState(initialized = true, hardware = phone, busy = true, canCancel = true,
                operationModelId = "default", progress = 0.5f, label = "Downloading…"), ChatModelState(), false,
                onImport = {}, onDownload = { _, _ -> }, onUse = {}, onRemove = {}, onCancel = { cancelled = true }, onRefresh = {}, onSource = {})
        } } }
        compose.onNodeWithText("Import").assertIsNotEnabled()
        compose.onNodeWithText("Download").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertTrue(cancelled)
    }
}
