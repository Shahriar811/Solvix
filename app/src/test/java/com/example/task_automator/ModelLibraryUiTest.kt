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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.LEGACY)
@LooperMode(LooperMode.Mode.PAUSED)
class ModelLibraryUiTest {
    @get:Rule val compose = createComposeRule()
    private val phone = DeviceHardware("Google Pixel 7 Pro", "Tensor G2", 35, listOf("arm64-v8a"), true, 12 * GIB, 10 * GIB, 100 * GIB, 8, false)
    @Test fun importChecksHardwareAndUsesTheExpandedModelsIdentity() {
        var imported: String? = null
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ModelLibraryPanel(ModelInstallState(initialized = true, hardware = phone), ChatModelState(), false,
                onImport = { imported = it }, onDownload = { _, _ -> }, onUse = {}, onRemove = {}, onCancel = {}, onRefresh = {}, onSource = {})
        } } }
        compose.onNodeWithText("Qwen3.5-4B").performScrollTo().performClick()
        compose.onNodeWithText("Import").performScrollTo().performClick()
        assertNull(imported)
        compose.onNodeWithText("Choose file").performClick()
        assertEquals("qwen35-4b", imported)
    }
    @Test fun insufficientHardwareDisablesBothInstallRoutes() {
        var installed = false
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ModelLibraryPanel(ModelInstallState(initialized = true, hardware = phone.copy(process64Bit = false)), ChatModelState(), false,
                onImport = { installed = true }, onDownload = { _, _ -> installed = true }, onUse = {}, onRemove = {}, onCancel = {}, onRefresh = {}, onSource = {})
        } } }
        compose.onNodeWithText("Import").assertIsNotEnabled()
        compose.onNodeWithText("Download").assertIsNotEnabled()
        assertFalse(installed)
        compose.onNodeWithText("Requires a 64-bit ARM Android device.").assertExists()
    }
    @Test fun activeModelHasNoRemovalActionAndDefaultsRemainIdentified() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            ModelLibraryPanel(ModelInstallState(initialized = true, hardware = phone, installedModels = mapOf("default" to "/default")),
                ChatModelState(ready = true, supportsImages = true, modelId = "default"), false,
                onImport = {}, onDownload = { _, _ -> }, onUse = {}, onRemove = {}, onCancel = {}, onRefresh = {}, onSource = {})
        } } }
        compose.onNodeWithText("Solvix Default · Default").assertExists()
        compose.onNodeWithText("Active").assertIsNotEnabled()
        compose.onNodeWithText("Remove").assertDoesNotExist()
    }
}
