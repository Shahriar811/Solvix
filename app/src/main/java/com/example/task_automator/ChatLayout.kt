package com.example.task_automator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** One inset owner and a weighted message area keep the composer at the visible bottom. */
@Composable
internal fun ChatLayout(
    modifier: Modifier = Modifier,
    insets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout).union(WindowInsets.ime),
    header: @Composable () -> Unit,
    messages: @Composable () -> Unit,
    composer: @Composable () -> Unit
) {
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(insets)) {
        header()
        Box(Modifier.weight(1f).fillMaxWidth()) { messages() }
        composer()
    }
}
