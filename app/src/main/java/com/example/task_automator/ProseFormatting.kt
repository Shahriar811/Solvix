package com.example.task_automator

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.commonmark.node.*
import org.commonmark.parser.Parser

internal sealed interface ProseElement {
    data class Text(val value: AnnotatedString, val headingLevel: Int = 0, val indent: Int = 0, val quote: Boolean = false) : ProseElement
    data class Code(val text: String) : ProseElement
    data object Divider : ProseElement
}

/** Parse presentation only. Stored replies and fenced code are never rewritten. */
internal object ProseFormatting {
    private val parser = Parser.builder().maxInlineNesting(32).maxOpenBlockParsers(32).build()

    fun parse(text: String, streaming: Boolean = false): List<ProseElement> {
        val result = mutableListOf<ProseElement>()
        val document = parser.parse(if (streaming) streamingPreview(text) else text)
        fun append(node: Node, indent: Int = 0, prefix: String = "", quote: Boolean = false) {
            when (node) {
                is Paragraph, is Heading -> result += ProseElement.Text(inline(node, prefix),
                    (node as? Heading)?.level ?: 0, indent, quote)
                is ThematicBreak -> result += ProseElement.Divider
                is IndentedCodeBlock -> result += ProseElement.Code(node.literal.removeSuffix("\n"))
                is FencedCodeBlock -> result += ProseElement.Code(node.literal.removeSuffix("\n"))
                is BulletList, is OrderedList -> {
                    var item = node.firstChild
                    var number = (node as? OrderedList)?.markerStartNumber ?: 1
                    while (item != null) {
                        var child = item.firstChild
                        var first = true
                        while (child != null) {
                            val nested = child is BulletList || child is OrderedList
                            append(child, indent + if (nested || !first) 1 else 0,
                                if (first && !nested) { if (node is OrderedList) "${number}. " else "• " } else "", quote)
                            first = false
                            child = child.next
                        }
                        number++
                        item = item.next
                    }
                }
                is HtmlBlock -> result += ProseElement.Text(AnnotatedString(node.literal.trimEnd()), indent = indent, quote = quote)
                else -> {
                    var child = node.firstChild
                    while (child != null) { append(child, indent, prefix, quote || node is BlockQuote); child = child.next }
                }
            }
        }
        append(document)
        return result
    }

    private fun inline(parent: Node, prefix: String): AnnotatedString = buildAnnotatedString {
        append(prefix)
        fun children(node: Node) {
            var child = node.firstChild
            while (child != null) {
                val current = child
                when (current) {
                    is Text -> append(current.literal)
                    is Code -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) { append(current.literal) }
                    is Emphasis -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { children(current) }
                    is StrongEmphasis -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { children(current) }
                    is SoftLineBreak, is HardLineBreak -> append('\n')
                    is HtmlInline -> append(current.literal)
                    is Link -> {
                        children(current)
                        // Keep a useful URL when copying a labelled link, without loading remote content.
                        if (current.destination.isNotBlank() && current.firstChild != null &&
                            !(current.firstChild is Text && (current.firstChild as Text).literal == current.destination)) {
                            append(" (${current.destination})")
                        }
                    }
                    else -> children(current)
                }
                child = current.next
            }
        }
        children(parent)
    }

    /** Temporarily complete emphasis while tokens arrive, without touching escaped text or inline code. */
    private fun streamingPreview(text: String): String {
        val pending = mutableListOf<String>()
        var ticks = 0
        var i = 0
        var tailStart = text.length
        while (i < text.length) {
            if (text[i] == '\\') { i = (i + 2).coerceAtMost(text.length); continue }
            val ch = text[i]
            if (ch == '`') {
                val start = i
                while (i < text.length && text[i] == '`') i++
                val width = i - start
                if (ticks == 0) ticks = width else if (ticks == width) ticks = 0
                continue
            }
            if (ticks > 0 || (ch != '*' && ch != '_')) { i++; continue }
            val start = i
            while (i < text.length && text[i] == ch) i++
            val width = i - start
            if (width > 3) continue
            val before = text.getOrNull(start - 1)
            val after = text.getOrNull(i)
            val canClose = before != null && !before.isWhitespace() && (ch == '*' || after == null || !after.isLetterOrDigit())
            val identifier = ch == '_' && text.substring(i).takeWhile { !it.isWhitespace() }
                .let { '_' in it && !it.trimEnd('.', ',', ';', ':', ')').endsWith('_') }
            val canOpen = after != null && !after.isWhitespace() && (before == null || !before.isLetterOrDigit()) && !identifier
            var left = width
            if (canClose) {
                while (left > 0 && pending.lastOrNull()?.first() == ch) {
                    val opener = pending.removeAt(pending.lastIndex)
                    val used = minOf(left, opener.length)
                    left -= used
                    if (used < opener.length) pending += opener.drop(used)
                }
            }
            if (left > 0 && canOpen) pending += ch.toString().repeat(left)
            else if (left > 0 && i == text.length && (before == null || before.isWhitespace() || pending.isNotEmpty())) tailStart = start + width - left
        }
        return text.substring(0, tailStart) + pending.asReversed().joinToString("")
    }
}
