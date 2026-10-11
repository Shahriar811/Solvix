package com.example.task_automator

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.*
import org.junit.Test

class ProseFormattingTest {
    private fun content(text: String, streaming: Boolean = false) = ProseFormatting.parse(text, streaming)
        .filterIsInstance<ProseElement.Text>().joinToString("\n") { it.value.text }

    @Test fun singleDoubleAndTripleEmphasisMarkersBecomeStyles() {
        val elements = ProseFormatting.parse("*Italic* **Bold** ***Both*** _also italic_ __also bold__")
        val value = (elements.single() as ProseElement.Text).value
        assertEquals("Italic Bold Both also italic also bold", value.text)
        assertTrue(value.spanStyles.any { it.item.fontStyle == FontStyle.Italic })
        assertTrue(value.spanStyles.any { it.item.fontWeight == FontWeight.SemiBold })
    }
    @Test fun nestedEmphasisDoesNotLeaveDanglingSymbols() {
        assertEquals("Bold with italic inside", content("**Bold with _italic_ inside**"))
        assertEquals("italic with bold inside", content("*italic with **bold** inside*"))
    }
    @Test fun bulletListsUseCleanBulletsAndKeepItemFormatting() {
        assertEquals("• First\n• Second", content("* **First**\n* _Second_"))
        assertEquals("3. Third\n4. Fourth", content("3. Third\n4. Fourth"))
    }
    @Test fun decorativeRunsBecomeDividers() {
        val elements = ProseFormatting.parse("Before\n\n***\n\n___\n\n* * *\n\nAfter")
        assertEquals(3, elements.count { it == ProseElement.Divider })
        assertEquals("Before\nAfter", content("Before\n\n***\n\n___\n\nAfter"))
    }
    @Test fun headingsAndBodyAreSeparateWithoutHashMarkers() {
        val elements = ProseFormatting.parse("### **Summary**\nRegular answer")
        assertEquals(3, (elements[0] as ProseElement.Text).headingLevel)
        assertEquals("Summary", (elements[0] as ProseElement.Text).value.text)
        assertEquals(0, (elements[1] as ProseElement.Text).headingLevel)
        assertEquals("Regular answer", (elements[1] as ProseElement.Text).value.text)
    }
    @Test fun literalMathIdentifiersAndEscapedAsterisksAreNotDeleted() {
        assertEquals("user_name and x_y_z; 2 * 3 = 6; *literal*", content("user_name and x_y_z; 2 * 3 = 6; \\*literal\\*"))
        assertEquals("_hidden_file", content("_hidden_file"))
    }
    @Test fun inlineCodePreservesOperatorsUnderscoresAndStarsExactly() {
        val element = ProseFormatting.parse("Use `__init__` and `x_y *= 2; ***`. ").single() as ProseElement.Text
        assertEquals("Use __init__ and x_y *= 2; ***.", element.value.text)
        assertTrue(element.value.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }
    @Test fun incompleteStreamingEmphasisIsFormattedWithoutChangingTheInput() {
        val text = "**Hello"
        assertEquals("Hello", content(text, streaming = true))
        assertEquals("Hello", content("***Hello**", streaming = true))
        assertEquals("**Hello", text)
        assertEquals("Hello", content("**Hello**", streaming = true))
    }
    @Test fun streamingDoesNotAlterEscapedMarkersOrInlineCode() {
        assertEquals("*literal* and user_name", content("\\*literal\\* and user_name", streaming = true))
        assertEquals("x_y *= 2; ***", content("`x_y *= 2; ***`", streaming = true))
        assertEquals("2 * 3", content("2 * 3", streaming = true))
        assertEquals("_hidden_file", content("_hidden_file", streaming = true))
    }
    @Test fun partialBulletMarkersDoNotFlashAtTheEndOfAStream() {
        assertEquals("", content("**", streaming = true))
        assertEquals("Answer\n", content("Answer\n\n*", streaming = true) + "\n")
        assertEquals("• First", content("* First", streaming = true))
    }
    @Test fun nestedListsAndBlockQuotesStayReadable() {
        val elements = ProseFormatting.parse("* First\n  * Nested\n\n> **Quoted** answer")
        val nested = elements.filterIsInstance<ProseElement.Text>().first { it.value.text == "• Nested" }
        assertEquals(1, nested.indent)
        val quoted = elements.filterIsInstance<ProseElement.Text>().last()
        assertEquals("Quoted answer", quoted.value.text)
        assertTrue(quoted.quote)
    }
    @Test fun indentedCodeIsProtectedRatherThanTreatedAsProse() {
        assertEquals(ProseElement.Code("x_y *= 2; ***"), ProseFormatting.parse("    x_y *= 2; ***").single())
    }
}
