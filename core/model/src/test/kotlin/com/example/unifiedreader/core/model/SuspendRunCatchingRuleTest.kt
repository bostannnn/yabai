package com.example.unifiedreader.core.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Inside suspend code, catch with [runSuspendCatching], never `runCatching`.
 *
 * `runCatching` also catches the cancellation of the coroutine, which then carries on as if a call had merely
 * failed. detekt's `SuspendFunSwallowedCancellation` knows which calls suspend, but its type resolution crashes
 * on this project's Compose modules, so this rule reads the source instead: a `runCatching {` inside a
 * `suspend fun` or a coroutine block (`launch`, `async`, `withContext`, `flow`, ...) fails. [runSuspendCatching]
 * is inline, so it costs nothing where the block happens not to suspend.
 */
class SuspendRunCatchingRuleTest {

    @Test
    fun `no runCatching inside suspend code`() {
        val root = repositoryRoot()
        val sourceRoots = listOf("app", "core", "data", "feature")
            .flatMap { top ->
                if (top == "app") listOf(root.resolve("app")) else root.resolve(top).listFiles(File::isDirectory)?.toList().orEmpty()
            }
            .map { module -> module.resolve("src/main") }
            .filter(File::isDirectory)
        val violations = sourceRoots
            .flatMap { sourceRoot -> sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" } }
            .flatMap { file -> runCatchingInSuspendCode(file.readText()).map { line -> "${file.relativeTo(root)}:$line" } }
        assertTrue(
            "Use runSuspendCatching inside suspend code; runCatching swallows cancellation:\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `the scanner finds runCatching in suspend functions and coroutine blocks only`() {
        val source = """
            fun plain() = runCatching { parse() }
            suspend fun load() {
                val x = runCatching { fetch() }
            }
            suspend fun expression(): Result<Int> = runCatching { fetch() }
            interface Api {
                suspend fun get(): Int
                fun other() = runCatching { parse() }
            }
            fun start() {
                scope.launch {
                    runCatching { fetch() }
                }
                val s = "launch { runCatching {"
                // suspend fun commented() { runCatching { } }
                runCatching { parse() }
            }
        """.trimIndent()
        assertEquals(listOf(3, 5, 12), runCatchingInSuspendCode(source))
    }

    private fun repositoryRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !dir.resolve("settings.gradle.kts").isFile) dir = dir.parentFile
        return requireNotNull(dir) { "settings.gradle.kts not found above ${System.getProperty("user.dir")}" }
    }
}

private val suspendStart = Regex(
    """\bsuspend\s+fun\b|\b(launch|async|withContext|coroutineScope|supervisorScope|flow|channelFlow|callbackFlow|""" +
        """withTimeout|withTimeoutOrNull|produce|runBlocking)\s*(\([^)]*\))?\s*\{""",
)
private val runCatchingCall = Regex("""\brunCatching\s*\{""")
private val nextDeclaration = Regex(
    """\s*(}|@|(private|internal|public|protected|override|suspend|abstract|open|fun|val|var|class|interface|object)\b)""",
)
private val nextDeclarationOrBlankLine = Regex("""\n\s*\n|\n\s*(private |internal |override |public )?(suspend )?fun """)

/** The 1-based lines of `runCatching {` calls that sit inside suspend code in [source]. */
internal fun runCatchingInSuspendCode(source: String): List<Int> {
    val code = blankStringsAndComments(source)
    val regions = suspendStart.findAll(code).mapNotNull { match -> suspendRegion(code, match) }.toList()
    return runCatchingCall.findAll(code)
        .filter { call -> regions.any { call.range.first in it } }
        .map { call -> code.substring(0, call.range.first).count { it == '\n' } + 1 }
        .toList()
}

private fun suspendRegion(code: String, match: MatchResult): IntRange? =
    if (code[match.range.last] == '{') {
        match.range.last..closingBrace(code, match.range.last)
    } else {
        suspendFunBody(code, match.range.last + 1)
    }

/** A suspend fun's body after its name: a block, an expression, or nothing (an interface member). */
private fun suspendFunBody(code: String, from: Int): IntRange? {
    var depth = 0
    var body: IntRange? = null
    var index = from
    while (body == null && index < code.length) {
        val char = code[index]
        val atTop = depth == 0
        depth += bracketDepthChange(code, index)
        when {
            atTop && char == '{' -> body = index..closingBrace(code, index)
            atTop && char == '\n' && nextDeclaration.matchesAt(code, index + 1) -> index = code.length
            atTop && isExpressionBodyStart(code, index) ->
                body = index..(nextDeclarationOrBlankLine.find(code, index)?.range?.first ?: code.length)
        }
        index++
    }
    return body
}

/** +1 for an opening parenthesis or angle bracket, -1 for a closing one (not the arrow in `->`). */
private fun bracketDepthChange(code: String, index: Int): Int = when (code[index]) {
    '(', '<' -> 1
    ')' -> -1
    '>' -> if (code[index - 1] == '-') 0 else -1
    else -> 0
}

private fun isExpressionBodyStart(code: String, index: Int): Boolean =
    code[index] == '=' && code.getOrNull(index + 1) != '=' && code[index - 1] !in "!<>="

private fun closingBrace(code: String, open: Int): Int {
    var depth = 0
    var index = open
    while (index < code.length) {
        when (code[index]) {
            '{' -> depth++
            '}' -> if (--depth == 0) break
        }
        index++
    }
    return index
}

/** [source] with string literals and comments replaced by spaces, so line numbers and offsets stay put. */
private fun blankStringsAndComments(source: String): String {
    val out = StringBuilder(source.length)
    var index = 0
    while (index < source.length) {
        val end = literalOrCommentEnd(source, index)
        if (end == null) {
            out.append(source[index])
            index++
        } else {
            for (i in index until end) out.append(if (source[i] == '\n') '\n' else ' ')
            index = end
        }
    }
    return out.toString()
}

/** Where the string literal or comment starting at [index] ends, or null if none starts there. */
private fun literalOrCommentEnd(source: String, index: Int): Int? {
    fun endOf(marker: String, from: Int, past: Int) = source.indexOf(marker, from).let { if (it < 0) source.length else it + past }
    return when {
        source.startsWith("//", index) -> endOf("\n", index, 0)
        source.startsWith("/*", index) -> endOf("*/", index + 2, 2)
        source.startsWith("\"\"\"", index) -> endOf("\"\"\"", index + 3, 3)
        source[index] == '"' -> stringEnd(source, index)
        source[index] == '\'' && source.getOrNull(index + 2) == '\'' -> index + 3
        source[index] == '\'' && source.getOrNull(index + 1) == '\\' && source.getOrNull(index + 3) == '\'' -> index + 4
        else -> null
    }
}

private fun stringEnd(source: String, open: Int): Int {
    var end = open + 1
    while (end < source.length && source[end] != '"' && source[end] != '\n') end += if (source[end] == '\\') 2 else 1
    return minOf(end + 1, source.length)
}
