package com.example.task_automator

import org.junit.Assert.*
import org.junit.Test

class ReplyFormattingTest {
    @Test fun codeKeepsIndentationAndTrailingSpacesAndSeparatesProse() {
        val code = "  const value = 42;  \n\n  return value;  "
        assertEquals(listOf(ReplyBlock.Prose("Example:\n"), ReplyBlock.Code("typescript", code), ReplyBlock.Prose("Done.")),
            parseReply("Example:\n```typescript\n$code\n```\nDone."))
    }
    @Test fun inlineBackticksNeverBecomeCodeBlocks() {
        val text = "Use `name` or ``` inside prose."
        assertEquals(listOf(ReplyBlock.Prose(text)), parseReply(text))
    }
    @Test fun longerFenceAllowsEmbeddedShorterFences() {
        val code = "```python\nprint('hello')\n```"
        assertEquals(listOf(ReplyBlock.Code("markdown", code)), parseReply("````markdown\n$code\n````"))
    }
    @Test fun streamingPartialCodeIsDisplayedWithoutDroppingItsLastLine() {
        assertEquals(listOf(ReplyBlock.Code("python", "  print(\"Hi")), parseReply("```python\n  print(\"Hi"))
        assertEquals(listOf(ReplyBlock.Code("python", "")), parseReply("```python"))
    }
    @Test fun multipleTildeAndUnlabelledBlocksAreIndependent() {
        assertEquals(listOf(ReplyBlock.Code("json", "{}"), ReplyBlock.Code("", "x")), parseReply("~~~json\n{}\n~~~\n```\nx\n```"))
    }
    @Test fun windowsLineEndingsRemainInCopiedCode() {
        assertEquals(listOf(ReplyBlock.Code("java", "a();\r\nb();")), parseReply("```java\r\na();\r\nb();\r\n```\r\n"))
    }
    @Test fun stringsAndCommentsTakePrecedenceOverKeywords() {
        val code = "const text = \"return 42\"; // class 5\nrun(42)"
        val tokens = CodeHighlighting.tokens(code, "ts")
        assertEquals(listOf("const", "\"return 42\"", "// class 5", "run", "42"), tokens.map { code.substring(it.start, it.end) })
        assertEquals(listOf(CodeTokenKind.KEYWORD, CodeTokenKind.STRING, CodeTokenKind.COMMENT, CodeTokenKind.FUNCTION, CodeTokenKind.NUMBER), tokens.map { it.kind })
        tokens.zipWithNext().forEach { (a, b) -> assertTrue(a.end <= b.start) }
    }
    @Test fun pythonMultilineStringAndHashCommentAreSingleTokens() {
        val code = "def run():\n  text = \"\"\"return\n42\"\"\"\n  # import 5\n  return text"
        val tokens = CodeHighlighting.tokens(code, "py")
        assertEquals("\"\"\"return\n42\"\"\"", code.substring(tokens.first { it.kind == CodeTokenKind.STRING }.start,
            tokens.first { it.kind == CodeTokenKind.STRING }.end))
        assertEquals(1, tokens.count { it.kind == CodeTokenKind.COMMENT })
    }
    @Test fun unknownLanguagesArePlainAndUnfinishedStringsAreSafe() {
        assertTrue(CodeHighlighting.tokens("class Foo", "unknown").isEmpty())
        val code = "val x = \"unterminated\\"
        val last = CodeHighlighting.tokens(code, "kotlin").last()
        assertEquals(CodeTokenKind.STRING, last.kind)
        assertEquals(code.length, last.end)
    }
    @Test fun markupAndSqlCommentsAreRecognized() {
        assertEquals(CodeTokenKind.COMMENT, CodeHighlighting.tokens("<!-- return 5 -->", "html").single().kind)
        assertEquals(CodeTokenKind.KEYWORD, CodeHighlighting.tokens("SELECT", "sql").single().kind)
        assertEquals(CodeTokenKind.COMMENT, CodeHighlighting.tokens("-- SELECT", "sql").single().kind)
    }
}
