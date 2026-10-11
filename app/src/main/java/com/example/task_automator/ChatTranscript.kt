package com.example.task_automator

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private data class ScrollActivity(val moving: Boolean, val dragging: Boolean, val automatic: Boolean, val atEnd: Boolean)
private data class FollowRequest(val follow: Boolean, val moving: Boolean, val count: Int, val remaining: Int?)

/** Follow the measured bottom, never the start of an oversized reply. User scrolling takes priority. */
@Composable internal fun ChatTranscript(
    chatId: String,
    messages: List<ChatMessage>,
    pendingMessages: List<ChatMessage> = emptyList(),
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    messageContent: @Composable (ChatMessage) -> Unit
) {
    var followLatest by rememberSaveable(chatId) { mutableStateOf(true) }
    var automaticScroll by remember { mutableStateOf(false) }
    val dragging by state.interactionSource.collectIsDraggedAsState()
    val userScroll = remember(state, chatId) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) followLatest = false
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(state, chatId) {
        snapshotFlow { ScrollActivity(state.isScrollInProgress, dragging, automaticScroll, !state.canScrollForward) }
            .collect { activity ->
                if (activity.dragging || (activity.moving && !activity.automatic)) followLatest = false
                else if (!activity.moving && activity.atEnd) followLatest = true
            }
    }
    LaunchedEffect(state, chatId) {
        snapshotFlow {
            val layout = state.layoutInfo
            val tail = layout.visibleItemsInfo.lastOrNull { it.index == layout.totalItemsCount - 1 }
            FollowRequest(followLatest, state.isScrollInProgress || dragging, layout.totalItemsCount,
                tail?.let { it.offset + it.size + layout.afterContentPadding - layout.viewportEndOffset })
        }.collect { request ->
            if (request.follow && !request.moving && request.count > 0 && followLatest && !state.isScrollInProgress && !dragging) {
                automaticScroll = true
                try {
                    if (request.remaining == null) state.scrollToItem(request.count - 1)
                    else if (request.remaining > 0) state.scrollBy(request.remaining.toFloat())
                } catch (_: CancellationException) {
                    // A user gesture may cancel this scroll; disposing the screen still cancels the effect.
                    currentCoroutineContext().ensureActive()
                } finally {
                    automaticScroll = false
                }
            }
        }
    }
    Box(modifier) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().nestedScroll(userScroll),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            // Append-only turn positions keep the same keys as a streamed turn becomes saved history.
            itemsIndexed(messages + pendingMessages, key = { index, _ -> "$chatId:message:$index" }) { _, message ->
                messageContent(message)
            }
        }
        if (!followLatest && state.canScrollForward) {
            SmallFloatingActionButton(onClick = { followLatest = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                Icon(Icons.Default.ArrowDownward, contentDescription = "Scroll to latest")
            }
        }
    }
}
