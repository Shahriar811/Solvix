package com.example.task_automator

import com.example.task_automator.helpers.ModelCatalog
import com.example.task_automator.helpers.ModelSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatModelState(
    val loading: Boolean = false, val ready: Boolean = false, val supportsImages: Boolean = false,
    val error: String? = null, val modelId: String? = null, val targetModelId: String? = null,
    val failedModelId: String? = null
)

/** Commit a selection only after load succeeds; restore the previous model on failure. */
class ModelRuntimeController(
    private val scope: CoroutineScope,
    private val path: (String) -> String?,
    private val load: suspend (ModelSpec, String) -> Boolean,
    private val commit: (String) -> Unit
) {
    private val mutableState = MutableStateFlow(ChatModelState())
    val state = mutableState.asStateFlow()
    fun use(id: String) {
        val previous = state.value
        val modelPath = path(id) ?: return
        if (previous.loading || (previous.ready && previous.modelId == id)) return
        val model = ModelCatalog.find(id)
        mutableState.value = previous.copy(loading = true, ready = false, supportsImages = false, error = null, targetModelId = id, failedModelId = null)
        scope.launch {
            try {
                val images = load(model, modelPath)
                commit(id)
                mutableState.value = ChatModelState(ready = true, supportsImages = images, modelId = id)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (error !is Exception && error !is LinkageError) throw error
                val oldId = previous.modelId
                val oldPath = oldId?.let(path)
                if (previous.ready && oldId != null && oldPath != null) {
                    try {
                        val images = load(ModelCatalog.find(oldId), oldPath)
                        commit(oldId)
                        mutableState.value = ChatModelState(ready = true, supportsImages = images, modelId = oldId, failedModelId = id,
                            error = "${model.name} could not load. Your previous model was restored. Close other apps or free storage, then retry. ${error.message.orEmpty().take(200)}")
                    } catch (restoreError: Throwable) {
                        if (restoreError is CancellationException) throw restoreError
                        if (restoreError !is Exception && restoreError !is LinkageError) throw restoreError
                        mutableState.value = ChatModelState(failedModelId = id, error = "Could not load a model. Close other apps, free storage, then choose a model and retry.")
                    }
                } else mutableState.value = ChatModelState(failedModelId = id, error = "${model.name} could not load on this device. Close other apps or free storage, then retry. ${error.message.orEmpty().take(200)}")
            }
        }
    }
}
