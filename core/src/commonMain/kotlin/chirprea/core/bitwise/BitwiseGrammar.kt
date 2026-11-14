package chirprea.core.bitwise

/**
 * Parser for CHIRP's memory-layout language (`MEM_FORMAT`), ported from
 * `chirp/bitwise_grammar.py` so driver definitions can be reused verbatim.
 *
 * ```
 * #seekto 0x0010;
 * struct {
 *   lbcd rxfreq[4];
 *   u8 unknown:4,
 *      txpower:4;
 *   char name[7];
 * } memory[128];
 * ```
 */
class ParseError(message: String) : Exception(message)

val BITWISE_TYPES = setOf(
    "bit", "lbit", "u8", "u16", "ul16", "u24", "ul24", "u32", "ul32",
    "i8", "i16", "il16", "i24", "il24", "i32", "il32", "char", "lbcd", "bbcd",
)

sealed interface Stmt { val line: Int }

sealed interface DefTarget
data class SymbolTarget(val name: String) : DefTarget
data class ArrayTarget(val name: String, val count: Int) : DefTarget
data class BitfieldTarget(val fields: List<Pair<String, Int>>) : DefTarget

data class Definition(val type: String, val target: DefTarget, override val line: Int) : Stmt

/** `struct name { ... };` — declares a reusable struct type. */
data class StructDefn(val name: String, val body: List<Stmt>, override val line: Int) : Stmt

/** `struct name var;`, `struct { ... } var[N];` */
data class StructDecl(
    val typeName: String?,
    val body: List<Stmt>?,
    val name: String,
    val count: Int?,
    override val line: Int,
) : Stmt

data class UnionStmt(val body: List<Stmt>, val name: String, val count: Int?, override val line: Int) : Stmt

data class Directive(val kind: String, val value: String, override val line: Int) : Stmt

private enum class Tok { WORD, STRING, PUNCT, EOF }

private data class Token(val kind: Tok, val text: String, val line: Int)

private fun lex(spec: String): List<Token> {
    val out = ArrayList<Token>()
    var i = 0
    var line = 0
    val n = spec.length
    while (i < n) {
        val c = spec[i]
        when {
            c == '\n' -> { line++; i++ }
            c.isWhitespace() -> i++
            c == '/' && i + 1 < n && spec[i + 1] == '/' -> {
                while (i < n && spec[i] != '\n') i++
            }
            c == '/' && i + 1 < n && spec[i + 1] == '*' -> {
                i += 2
                while (i < n && !(spec[i] == '*' && i + 1 < n && spec[i + 1] == '/')) {
                    if (spec[i] == '\n') line++
                    i++
                }
                i += 2
            }
            c == '"' -> {
                val start = i
                i++
                while (i < n && spec[i] != '"') i++
                i++
                out += Token(Tok.STRING, spec.substring(start, minOf(i, n)), line)
            }
            c.isLetterOrDigit() || c == '_' -> {
                val start = i
                while (i < n && (spec[i].isLetterOrDigit() || spec[i] == '_')) i++
                out += Token(Tok.WORD, spec.substring(start, i), line)
            }
            c in "{}[];:,#" -> { out += Token(Tok.PUNCT, c.toString(), line); i++ }
            else -> throw ParseError("Unexpected character '$c' on line $line")
        }
    }
    out += Token(Tok.EOF, "", line)
    return out
}

private val COUNT_RE = Regex("^([1-9][0-9]*|0x[0-9a-fA-F]+)$")

fun parseCount(text: String): Int =
    if (text.startsWith("0x")) text.substring(2).toInt(16) else text.toInt()

object BitwiseGrammar {
    fun parse(spec: String): List<Stmt> = Parser(lex(spec)).parseTop()
}

private class Parser(private val toks: List<Token>) {
    private var p = 0

    private fun peek(ahead: Int = 0) = toks[minOf(p + ahead, toks.size - 1)]
    private fun next() = toks[p++]
    private fun isPunct(t: Token, s: String) = t.kind == Tok.PUNCT && t.text == s

    private fun expect(s: String): Token {
        val t = next()
        if (!isPunct(t, s)) fail(t, "expected '$s' but found '${t.text}'")
        return t
    }

    private fun fail(t: Token, msg: String): Nothing = throw ParseError("Line ${t.line}: $msg")

    private fun symbol(): String {
        val t = next()
        if (t.kind != Tok.WORD) fail(t, "expected identifier but found '${t.text}'")
        return t.text
    }

    private fun count(): Int {
        val t = next()
        if (t.kind != Tok.WORD || !COUNT_RE.matches(t.text)) fail(t, "invalid count '${t.text}'")
        return parseCount(t.text)
    }

    fun parseTop(): List<Stmt> {
        val body = parseInner()
        if (peek().kind != Tok.EOF) fail(peek(), "unexpected '${peek().text}'")
        return body
    }

    private fun parseInner(): List<Stmt> {
        val out = ArrayList<Stmt>()
        while (true) {
            val t = peek()
            when {
                t.kind == Tok.EOF || isPunct(t, "}") -> break
                isPunct(t, "#") -> out += directive()
                t.kind == Tok.WORD && t.text == "struct" -> out += struct()
                t.kind == Tok.WORD && t.text == "union" -> out += union()
                t.kind == Tok.WORD && t.text in BITWISE_TYPES -> out += definition()
                else -> fail(t, "unexpected '${t.text}'")
            }
        }
        if (out.isEmpty()) fail(peek(), "empty block")
        return out
    }

    private fun block(): List<Stmt> {
        expect("{")
        val body = parseInner()
        expect("}")
        return body
    }

    /** `name` or `name[count]` */
    private fun declTarget(): Pair<String, Int?> {
        val name = symbol()
        if (isPunct(peek(), "[")) {
            next()
            val c = count()
            expect("]")
            return name to c
        }
        return name to null
    }

    private fun definition(): Stmt {
        val typeTok = next()
        val name = symbol()
        val target: DefTarget = when {
            isPunct(peek(), "[") -> {
                next(); val c = count(); expect("]")
                ArrayTarget(name, c)
            }
            isPunct(peek(), ":") -> {
                next()
                val fields = mutableListOf(name to count())
                while (isPunct(peek(), ",")) {
                    next()
                    val fname = symbol()
                    expect(":")
                    fields += fname to count()
                }
                BitfieldTarget(fields)
            }
            else -> SymbolTarget(name)
        }
        expect(";")
        return Definition(typeTok.text, target, typeTok.line)
    }

    private fun struct(): Stmt {
        val kw = next()
        val stmt: Stmt = if (peek().kind == Tok.WORD && isPunct(peek(1), "{")) {
            val name = symbol()
            StructDefn(name, block(), kw.line)
        } else if (isPunct(peek(), "{")) {
            val body = block()
            val (name, c) = declTarget()
            StructDecl(null, body, name, c, kw.line)
        } else {
            val typeName = symbol()
            val (name, c) = declTarget()
            StructDecl(typeName, null, name, c, kw.line)
        }
        expect(";")
        return stmt
    }

    private fun union(): Stmt {
        val kw = next()
        val body = block()
        val (name, c) = declTarget()
        expect(";")
        return UnionStmt(body, name, c, kw.line)
    }

    private fun directive(): Stmt {
        val hash = next()
        val kind = symbol()
        val value = when (kind) {
            "seekto", "seek" -> {
                val t = next()
                if (t.kind != Tok.WORD || !COUNT_RE.matches(t.text)) fail(t, "invalid offset '${t.text}'")
                t.text
            }
            "printoffset" -> {
                val t = next()
                if (t.kind != Tok.STRING) fail(t, "expected string")
                t.text
            }
            else -> fail(hash, "unknown directive '$kind'")
        }
        expect(";")
        return Directive(kind, value, hash.line)
    }
}
