package com.example.task_automator

internal sealed interface ReplyBlock {
    data class Prose(val text: String) : ReplyBlock
    data class Code(val language: String, val text: String) : ReplyBlock
}

/** Only line fences delimit code; inline backticks and fences inside strings stay intact. */
internal fun parseReply(text: String): List<ReplyBlock> {
    val blocks = mutableListOf<ReplyBlock>()
    val opening = Regex("^ {0,3}(`{3,}|~{3,})([^\\r\\n]*)$")
    var fence: String? = null
    var language = ""
    var start = 0
    var offset = 0
    while (offset < text.length) {
        val end = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
        val line = text.substring(offset, end).removeSuffix("\r")
        val next = if (end < text.length) end + 1 else end
        val activeFence = fence
        if (activeFence == null) {
            val match = opening.matchEntire(line)
            if (match != null && !(match.groupValues[1][0] == '`' && '`' in match.groupValues[2])) {
                if (offset > start) blocks += ReplyBlock.Prose(text.substring(start, offset))
                fence = match.groupValues[1]
                language = match.groupValues[2].trim().substringBefore(' ').substringBefore('\t').take(40)
                start = next
            }
        } else {
            val trimmed = line.trimStart(' ')
            val count = trimmed.takeWhile { it == activeFence[0] }.length
            if (line.length - trimmed.length <= 3 && count >= activeFence.length && trimmed.drop(count).isBlank()) {
                // The line break before the closing fence belongs to Markdown, not the code.
                blocks += ReplyBlock.Code(language, text.substring(start, offset).removeSuffix("\n").removeSuffix("\r"))
                fence = null
                start = next
            }
        }
        offset = next
    }
    if (fence != null) blocks += ReplyBlock.Code(language, text.substring(start))
    else if (start < text.length) blocks += ReplyBlock.Prose(text.substring(start))
    return blocks
}

internal enum class CodeTokenKind { KEYWORD, STRING, COMMENT, NUMBER, TYPE, FUNCTION }
internal data class CodeToken(val start: Int, val end: Int, val kind: CodeTokenKind)

/** Lightweight offline lexical colors. Unknown languages remain readable plain code. */
internal object CodeHighlighting {
    private val keywords = ("abstract as async await break case catch class const continue data def default " +
        "delete do else enum export extends false final finally fn for from fun function if implements import " +
        "in interface internal is let match namespace new nil None null object override package private protected " +
        "public raise readonly return sealed static struct super suspend switch this throw throws trait true try " +
        "type typeof val var void when while with yield bool boolean byte char double float int long short string " +
        "select insert update delete create table into values where join on and or not order by group having " +
        "limit distinct set union primary key references alter drop True False").split(' ').toSet()
    private val aliases = mapOf("kt" to "kotlin", "kts" to "kotlin", "js" to "javascript", "jsx" to "javascript",
        "ts" to "typescript", "tsx" to "typescript", "py" to "python", "c++" to "cpp", "c#" to "csharp",
        "cs" to "csharp", "sh" to "bash", "shell" to "bash", "yml" to "yaml", "rb" to "ruby")
    private val supported = setOf("", "kotlin", "java", "javascript", "typescript", "python", "c", "cpp", "csharp",
        "swift", "rust", "go", "dart", "php", "ruby", "sql", "json", "html", "xml", "css", "bash", "yaml")

    fun tokens(code: String, language: String): List<CodeToken> {
        val lang = language.lowercase(java.util.Locale.ROOT).let { aliases[it] ?: it }
        if (lang !in supported) return emptyList()
        val hashComments = lang in setOf("python", "bash", "ruby", "yaml", "php")
        val slashComments = lang !in setOf("python", "bash", "ruby", "yaml", "sql", "json", "html", "xml")
        val markup = lang == "html" || lang == "xml"
        val result = mutableListOf<CodeToken>()
        var i = 0
        while (i < code.length) {
            val start = i
            val ch = code[i]
            val kind: CodeTokenKind? = when {
                markup && code.startsWith("<!--", i) -> {
                    i = code.indexOf("-->", i + 4).let { if (it < 0) code.length else it + 3 }; CodeTokenKind.COMMENT
                }
                (slashComments && code.startsWith("//", i)) || (hashComments && ch == '#') ||
                    (lang == "sql" && code.startsWith("--", i)) -> {
                    i = code.indexOf('\n', i).let { if (it < 0) code.length else it }; CodeTokenKind.COMMENT
                }
                (slashComments || lang in setOf("sql", "css")) && code.startsWith("/*", i) -> {
                    i = code.indexOf("*/", i + 2).let { if (it < 0) code.length else it + 2 }; CodeTokenKind.COMMENT
                }
                ch == '"' || ch == '\'' || (lang in setOf("javascript", "typescript", "bash", "go") && ch == '`') -> {
                    val triple = code.startsWith(ch.toString().repeat(3), i)
                    val delimiter = ch.toString().repeat(if (triple) 3 else 1)
                    i += delimiter.length
                    while (i < code.length) {
                        if (code[i] == '\\') i = (i + 2).coerceAtMost(code.length)
                        else if (code.startsWith(delimiter, i)) { i += delimiter.length; break }
                        else i++
                    }
                    CodeTokenKind.STRING
                }
                ch.isDigit() && (i == 0 || !(code[i - 1].isLetterOrDigit() || code[i - 1] == '_')) -> {
                    i++
                    while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '_' ||
                        (code[i] == '.' && i + 1 < code.length && code[i + 1].isDigit()))) i++
                    CodeTokenKind.NUMBER
                }
                ch.isLetter() || ch == '_' || ch == '$' -> {
                    i++
                    while (i < code.length && (code[i].isLetterOrDigit() || code[i] == '_' || code[i] == '$')) i++
                    val word = code.substring(start, i)
                    var following = i
                    while (following < code.length && code[following].isWhitespace()) following++
                    when {
                        word in keywords || (lang == "sql" && word.lowercase(java.util.Locale.ROOT) in keywords) -> CodeTokenKind.KEYWORD
                        markup && start > 0 && (code[start - 1] == '<' || (code[start - 1] == '/' && start > 1 && code[start - 2] == '<')) -> CodeTokenKind.KEYWORD
                        ch.isUpperCase() -> CodeTokenKind.TYPE
                        following < code.length && code[following] == '(' -> CodeTokenKind.FUNCTION
                        else -> null
                    }
                }
                else -> { i++; null }
            }
            if (kind != null) result += CodeToken(start, i, kind)
        }
        return result
    }
}
