package com.example.task_automator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Native Markdown prose and code, with no remote HTML or image loading. */
@Composable internal fun ReplyContent(text: String, streaming: Boolean = false) {
    val blocks = remember(text) { parseReply(text) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block ->
            if (block is ReplyBlock.Code) {
                ReplyCodeBlock(block)
            } else if (block is ReplyBlock.Prose && block.text.isNotBlank()) {
                val elements = remember(block.text, streaming) { ProseFormatting.parse(block.text, streaming) }
                elements.forEach { element ->
                    when (element) {
                        is ProseElement.Text -> Text(element.value,
                            fontSize = if (element.headingLevel > 0) 18.sp else 16.sp, lineHeight = 25.sp,
                            fontWeight = if (element.headingLevel > 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (element.quote) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
                            modifier = Modifier.padding(start = (element.indent * 12).dp))
                        is ProseElement.Code -> ReplyCodeBlock(ReplyBlock.Code("", element.text))
                        ProseElement.Divider -> HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable private fun ReplyCodeBlock(block: ReplyBlock.Code) {
    val highlighted = remember(block.text, block.language) {
        buildAnnotatedString {
            append(block.text)
            CodeHighlighting.tokens(block.text, block.language).forEach { token ->
                val color = when (token.kind) {
                    CodeTokenKind.KEYWORD -> Color(0xFFC4A7FF)
                    CodeTokenKind.STRING -> Color(0xFFA6E3A1)
                    CodeTokenKind.COMMENT -> Color(0xFF9CA8BD)
                    CodeTokenKind.NUMBER -> Color(0xFFF5BC82)
                    CodeTokenKind.TYPE -> Color(0xFFF9E2AF)
                    CodeTokenKind.FUNCTION -> Color(0xFF89DCEB)
                }
                addStyle(SpanStyle(color = color), token.start, token.end)
            }
        }
    }
    Surface(color = Color(0xFF202231), contentColor = Color(0xFFE6EAF2), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Surface(color = Color(0xFF2A2D40), contentColor = Color(0xFFE6EAF2)) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(block.language.ifBlank { "Code" }, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    CopyTextButton(block.text, "Copy code", color = Color(0xFFE6EAF2))
                }
            }
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(14.dp)) {
                Text(highlighted, color = Color(0xFFE6EAF2), fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp, lineHeight = 21.sp, softWrap = false)
            }
        }
    }
}

/** The clipboard always receives the original text, without syntax styling. */
@Composable internal fun CopyTextButton(text: String, label: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(text) { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
    TextButton(onClick = { clipboard.setText(AnnotatedString(text)); copied = true }, enabled = text.isNotEmpty(),
        modifier = Modifier.semantics { contentDescription = label }, colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        Icon(if (copied) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(if (copied) "Copied" else label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable internal fun AssistantText(text: String, streaming: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        androidx.compose.foundation.text.selection.SelectionContainer { ReplyContent(text, streaming) }
        CopyTextButton(text, "Copy answer")
    }
}
