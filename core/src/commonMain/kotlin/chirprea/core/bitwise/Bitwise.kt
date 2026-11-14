package chirprea.core.bitwise

import chirprea.core.memmap.MemoryMap

/**
 * Applies a parsed `MEM_FORMAT` to a [MemoryMap] and returns the root struct.
 * Port of `chirp/bitwise.py`'s `Processor`.
 *
 * ```kotlin
 * val obj = Bitwise.parse(MEM_FORMAT, mmap)
 * val freq = obj["memory"][0]["rxfreq"].value * 10
 * ```
 */
object Bitwise {
    /** Warnings the processor emits (duplicate names, trailing bits, ...). */
    var warningSink: (String) -> Unit = {}

    fun parse(spec: String, data: MemoryMap, offset: Int = 0): StructElement =
        Processor(data, offset).run(BitwiseGrammar.parse(spec))
}

private class Processor(private val data: MemoryMap, private var offset: Int) {
    private val userTypes = HashMap<String, List<Stmt>>()
    private lateinit var current: StructElement

    fun run(ast: List<Stmt>): StructElement {
        current = StructElement(data, offset)
        parseBlock(ast)
        return current
    }

    private fun parseBlock(stmts: List<Stmt>) = stmts.forEach(::statement)

    private fun statement(s: Stmt) {
        when (s) {
            is Definition -> definition(s)
            is StructDefn -> userTypes[s.name] = s.body
            is StructDecl -> structDecl(s)
            is UnionStmt -> union(s)
            is Directive -> directive(s)
        }
    }

    private fun uniqueName(name: String): String {
        if (name !in current) return name
        val renamed = name + "_" + offset.toString(16).padStart(6, '0')
        Bitwise.warningSink("Duplicate definition for $name; renaming to $renamed")
        return renamed
    }

    private fun definition(d: Definition) {
        when (val t = d.target) {
            is BitfieldTarget -> bitfield(d.type, t)
            is SymbolTarget -> current.put(uniqueName(t.name), elements(d.type, 1)[0])
            is ArrayTarget -> {
                val name = uniqueName(t.name)
                val arr = ArrayElement(data, offset)
                elements(d.type, t.count).forEach(arr::add)
                current.put(name, arr)
            }
        }
    }

    /** Create [count] consecutive elements of [type], advancing the offset. */
    private fun elements(type: String, count: Int): List<DataElement> {
        val out = ArrayList<DataElement>(count)
        when (type) {
            "bit", "lbit" -> {
                if (count != 1 && count % 8 != 0) throw ParseError("bit array must be divisible by 8.")
                val big = type == "bit"
                for (i in 0 until count) {
                    val shift = if (big) 8 - i % 8 else i % 8 + 1
                    out += BitElement(data, offset, IntType.U8, 1, shift)
                    if ((i + 1) % 8 == 0) offset += 1
                }
            }
            "char" -> repeat(count) { out += CharElement(data, offset); offset += 1 }
            "lbcd", "bbcd" -> repeat(count) { out += BcdElement(data, offset, bigEndian = type == "bbcd"); offset += 1 }
            else -> {
                val intType = IntType.of(type) ?: throw ParseError("Unknown type $type")
                repeat(count) { out += IntElement(data, offset, intType); offset += intType.bytes }
            }
        }
        return out
    }

    private fun bitfield(type: String, t: BitfieldTarget) {
        val base = IntType.of(type) ?: throw ParseError("Bitfields require an integer type, not $type")
        var bitsLeft = base.bytes * 8
        for ((rawName, bits) in t.fields) {
            if (bitsLeft < 0) throw ParseError("Invalid bitfield spec")
            current.put(uniqueName(rawName), BitElement(data, offset, base, bits, bitsLeft))
            bitsLeft -= bits
        }
        if (bitsLeft < 0) throw ParseError("Bitfield exceeds ${base.bytes * 8} bits")
        if (bitsLeft > 0) Bitwise.warningSink("$bitsLeft trailing bits unaccounted for in ${t.fields.last().first}")
        offset += base.bytes
    }

    private fun structDecl(s: StructDecl) {
        val body = s.body ?: userTypes[s.typeName] ?: throw ParseError("Unknown struct type ${s.typeName}")
        val arr = ArrayElement(data, offset)
        repeat(s.count ?: 1) {
            val el = StructElement(data, offset, s.name)
            val saved = current
            current = el
            parseBlock(body)
            current = saved
            arr.add(el)
        }
        current.put(s.name, if (s.count == null) arr[0] else arr)
    }

    private fun union(u: UnionStmt) {
        val arr = ArrayElement(data, offset)
        val saved = current
        var unionSize = 0
        repeat(u.count ?: 1) {
            val start = offset
            val el = UnionElement(data, start, u.name)
            current = el
            for (member in u.body) {
                if (member is Directive || member is StructDefn) throw ParseError("Not supported in union: $member")
                offset = start
                statement(member)
                val size = offset - start
                if (unionSize == 0) unionSize = size
                else if (unionSize != size) {
                    throw ParseError("Union members must be the same size (found $size, expected $unionSize)")
                }
                el.unionSizeBits = unionSize * 8
            }
            if (unionSize == 0) throw ParseError("Empty union ${u.name} is not valid")
            offset = start + unionSize
            arr.add(el)
        }
        current = saved
        current.put(u.name, if (u.count == null) arr[0] else arr)
    }

    private fun directive(d: Directive) {
        when (d.kind) {
            "seekto" -> offset = parseCount(d.value)
            "seek" -> offset += parseCount(d.value)
            "printoffset" -> Bitwise.warningSink("${d.value.trim('"')}: $offset (0x${offset.toString(16).uppercase()})")
        }
    }
}
