package com.example.task_automator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Small renderer for readable prose, headings, emphasis and fenced code. */
@Composable internal fun ReplyContent(text: String) {
    val blocks = remember(text) { text.split("```") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEachIndexed { index, block ->
            if (index % 2 == 1) {
                val language = block.substringBefore('\n').trim()
                val code = if ('\n' in block) block.substringAfter('\n').trimEnd() else block
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        if (language.isNotBlank() && '\n' in block) Text(language, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(code, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            } else if (block.isNotBlank()) {
                block.trim().split("\n\n").forEach { paragraph ->
                    val heading = paragraph.startsWith("#")
                    val clean = if (heading) paragraph.trimStart('#', ' ') else paragraph
                    val formatted = buildAnnotatedString {
                        val pattern = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
                        var cursor = 0
                        pattern.findAll(clean).forEach { match ->
                            append(clean.substring(cursor, match.range.first))
                            if (match.groups[1] != null) withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(match.groupValues[1]) }
                            else withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(match.groupValues[2]) }
                            cursor = match.range.last + 1
                        }
                        append(clean.substring(cursor))
                    }
                    Text(formatted, fontSize = if (heading) 18.sp else 16.sp, lineHeight = 25.sp,
                        fontWeight = if (heading) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}
