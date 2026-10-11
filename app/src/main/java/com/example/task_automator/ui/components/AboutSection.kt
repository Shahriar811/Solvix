package com.example.task_automator.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

const val DEVELOPER_GITHUB_URL = "https://github.com/Shahriar811"

@Composable
fun AboutSection(onOpenGitHub: () -> Unit, onOpenLicense: () -> Unit) {
    Text("About Solvix", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    Text("Md. Shahriar Hossain is the developer of this app.", style = MaterialTheme.typography.bodyMedium)
    TextButton(onClick = onOpenGitHub, contentPadding = PaddingValues(vertical = 8.dp)) {
        Text("GitHub · @Shahriar811")
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open GitHub profile", modifier = Modifier.size(16.dp))
    }
    TextButton(onClick = onOpenLicense, contentPadding = PaddingValues(vertical = 8.dp)) {
        Text("MIT License")
    }
    Text("© 2026 Md. Shahriar Hossain", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
