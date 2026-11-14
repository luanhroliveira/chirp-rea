package chirprea.core.csv

/** RFC 4180-style CSV reader/writer (quote = `"`), matching Python's csv module defaults. */
object Csv {
    fun parse(text: String, sep: Char = ','): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        var sawAny = false
        val s = text.removePrefix("﻿")
        while (i < s.length) {
            val c = s[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> { inQuotes = true; sawAny = true }
                sep -> { row += field.toString(); field.clear(); sawAny = true }
                '\r' -> {}
                '\n' -> {
                    if (sawAny || field.isNotEmpty()) row += field.toString()
                    rows += row
                    row = ArrayList(); field.clear(); sawAny = false
                }
                else -> { field.append(c); sawAny = true }
            }
            i++
        }
        if (sawAny || field.isNotEmpty()) { row += field.toString(); rows += row }
        return rows
    }

    fun format(rows: List<List<String>>, sep: Char = ','): String = buildString {
        for (row in rows) {
            append(row.joinToString(sep.toString(), transform = ::quote))
            append("\r\n")
        }
    }

    private fun quote(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
