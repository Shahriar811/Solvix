package com.example.task_automator

import com.example.task_automator.helpers.*
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModelRuntimeControllerTest {
    @Test fun selectionIsCommittedOnlyAfterLoadAndCapabilitiesChange() = runTest {
        val committed = mutableListOf<String>()
        val finish = CompletableDeferred<Unit>()
        val controller = ModelRuntimeController(this, { "/$it.litertlm" }, { _, _ -> finish.await(); false }, { committed += it })
        controller.use("qwen35-4b"); runCurrent()
        assertTrue(controller.state.value.loading)
        assertTrue(committed.isEmpty())
        finish.complete(Unit); advanceUntilIdle()
        assertEquals(listOf("qwen35-4b"), committed)
        assertTrue(controller.state.value.ready)
        assertFalse(controller.state.value.supportsImages)
    }
    @Test fun failedSwitchReloadsAndRecommitsPreviousModel() = runTest {
        val loads = mutableListOf<String>()
        val commits = mutableListOf<String>()
        val controller = ModelRuntimeController(this, { "/$it.litertlm" }, { model, _ ->
            loads += model.id
            if (model.id == "qwen3-8b") throw IOException("Not enough memory")
            model.supportsImages
        }, { commits += it })
        controller.use(ModelCatalog.DEFAULT_ID); advanceUntilIdle()
        controller.use("qwen3-8b"); advanceUntilIdle()
        assertEquals(listOf("default", "qwen3-8b", "default"), loads)
        assertEquals(listOf("default", "default"), commits)
        assertEquals("default", controller.state.value.modelId)
        assertTrue(controller.state.value.ready && controller.state.value.supportsImages)
        assertNotNull(controller.state.value.error)
    }
    @Test fun rapidSwitchRequestsCannotLoadTwoEnginesAtOnce() = runTest {
        val done = CompletableDeferred<Unit>()
        var loads = 0
        val controller = ModelRuntimeController(this, { "/$it" }, { _, _ -> loads++; done.await(); true }, {})
        controller.use("default"); controller.use("gemma-e4b"); runCurrent()
        assertEquals(1, loads)
        done.complete(Unit); advanceUntilIdle()
        assertEquals("default", controller.state.value.modelId)
    }
    @Test fun failedInitialLoadAndMissingInstallationDoNotCommit() = runTest {
        var commits = 0
        val controller = ModelRuntimeController(this, { if (it == "default") "/default" else null }, { _, _ -> throw UnsatisfiedLinkError("Unsupported runtime") }, { commits++ })
        controller.use("qwen3-8b"); assertFalse(controller.state.value.loading)
        controller.use("default"); advanceUntilIdle()
        assertEquals(0, commits)
        assertFalse(controller.state.value.ready)
        assertNotNull(controller.state.value.error)
    }
    @Test fun requestingActiveModelKeepsWarmEngine() = runTest {
        var loads = 0
        val controller = ModelRuntimeController(this, { "/$it" }, { _, _ -> loads++; true }, {})
        controller.use("default"); advanceUntilIdle()
        controller.use("default"); advanceUntilIdle()
        assertEquals(1, loads)
    }
}
