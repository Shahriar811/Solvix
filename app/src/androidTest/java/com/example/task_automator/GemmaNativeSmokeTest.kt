package com.example.task_automator

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.example.task_automator.helpers.GemmaModelRepository
import com.example.task_automator.helpers.LlmInferenceHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in device test: downloads 2.59 GB and leaves the verified model installed for the app. */
class GemmaNativeSmokeTest {
    @Test fun downloadsAndRunsTextAndImageWithOneBundle() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runModelSmokeTest") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        GemmaModelRepository.initialize(context)
        withTimeout(60_000) { GemmaModelRepository.state.first { !it.busy } }
        if (GemmaModelRepository.state.value.installedPath == null) {
            GemmaModelRepository.download(wifiOnly = true)
        }
        val installation = withTimeout(20 * 60_000) {
            GemmaModelRepository.state.first { !it.busy }
        }
        checkNotNull(installation.installedPath) { installation.error ?: "Model is not installed." }
        val inference = LlmInferenceHelper()
        try {
            inference.initialize(context, installation.installedPath)
            val text = inference.generateResponse("What is 2 plus 2? Answer in one short sentence.")
            assertTrue("Unexpected text response: $text", text.contains("4") || text.contains("four", ignoreCase = true))
            assertTrue("Vision backend did not initialize on the test device", inference.supportsImages)
            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            try {
                val answer = inference.generateResponse("What color is this image? Answer in one word.", bitmap)
                assertTrue("Unexpected image response: $answer", answer.contains("red", ignoreCase = true))
            } finally {
                bitmap.recycle()
            }
        } finally {
            inference.cleanup()
        }
    }
}
