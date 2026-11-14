package chirprea.core.bitwise

import chirprea.core.memmap.MemoryMap

/** Integer storage types of the bitwise language. */
enum class IntType(val bytes: Int, val signed: Boolean, val bigEndian: Boolean) {
    U8(1, false, true), U16(2, false, true), UL16(2, false, false),
    U24(3, false, true), UL24(3, false, false), U32(4, false, true), UL32(4, false, false),
    I8(1, true, true), I16(2, true, true), IL16(2, true, false),
    I24(3, true, true), IL24(3, true, false), I32(4, true, true), IL32(4, true, false);

    fun readUnsigned(data: MemoryMap, offset: Int): Long {
        var v = 0L
        for (i in 0 until bytes) {
            val b = data[offset + if (bigEndian) i else bytes - 1 - i].toLong()
            v = (v shl 8) or b
        }
        return v
    }

    fun read(data: MemoryMap, offset: Int): Long {
        val u = readUnsigned(data, offset)
        if (!signed) return u
        val bits = bytes * 8
        return (u shl (64 - bits)) shr (64 - bits)
    }

    fun write(data: MemoryMap, offset: Int, value: Long) {
        var v = value
        for (i in 0 until bytes) {
            val idx = offset + if (bigEndian) bytes - 1 - i else i
            data[idx] = (v and 0xFF).toInt()
            v = v ushr 8
        }
    }

    companion object {
        fun of(name: String): IntType? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/**
 * A view onto part of a [MemoryMap]. Reading/writing goes straight through to
 * the underlying bytes, exactly like CHIRP's `DataElement`.
 */
abstract class DataElement(internal val data: MemoryMap, val offset: Int) {
    /** Size in bits. */
    abstract val sizeBits: Int

    /** Integer value (ints, bitfields, BCD, BCD arrays). */
    open var value: Long
        get() = throw UnsupportedOperationException("${this::class.simpleName} has no integer value")
        set(@Suppress("UNUSED_PARAMETER") v) =
            throw UnsupportedOperationException("${this::class.simpleName} has no integer value")

    /** String value (char and char arrays). */
    open var string: String
        get() = throw UnsupportedOperationException("${this::class.simpleName} has no string value")
        set(@Suppress("UNUSED_PARAMETER") v) =
            throw UnsupportedOperationException("${this::class.simpleName} has no string value")

    open operator fun get(name: String): DataElement =
        throw NoSuchElementException("${this::class.simpleName} has no field '$name'")

    open operator fun get(index: Int): DataElement =
        throw UnsupportedOperationException("${this::class.simpleName} is not indexable")

    open operator fun contains(name: String): Boolean = false

    /** Number of items for arrays; 1 otherwise. */
    open val count: Int get() = 1

    var int: Int
        get() = value.toInt()
        set(v) { value = v.toLong() }

    open fun getRaw(): ByteArray = data.getBytes(offset, sizeBits / 8)

    open fun setRaw(bytes: ByteArray) {
        require(bytes.size == sizeBits / 8) { "Raw size mismatch: ${bytes.size} != ${sizeBits / 8}" }
        data.setBytes(offset, bytes)
    }

    /** Fill this element's bytes with [byte] (e.g. `0xFF` to blank a memory). */
    fun fillRaw(byte: Int) = setRaw(ByteArray(sizeBits / 8) { byte.toByte() })

    /** Resolve a symbolic path like `.memory[3].rxfreq`. */
    fun path(path: String): DataElement {
        var el: DataElement = this
        val re = Regex("""\.?([A-Za-z_]\w*)|\[(\d+)]""")
        var pos = 0
        while (pos < path.length) {
            val m = re.matchAt(path, pos) ?: throw IllegalArgumentException("Bad path '$path' at $pos")
            el = m.groups[1]?.let { el[it.value] } ?: el[m.groups[2]!!.value.toInt()]
            pos = m.range.last + 1
        }
        return el
    }
}

class IntElement(data: MemoryMap, offset: Int, val type: IntType) : DataElement(data, offset) {
    override val sizeBits get() = type.bytes * 8
    override var value: Long
        get() = type.read(data, offset)
        set(v) = type.write(data, offset, v)

    override fun toString() = "0x" + type.readUnsigned(data, offset).toString(16).uppercase().padStart(type.bytes * 2, '0')
}

/**
 * [nbits] bits ending (exclusive) at bit position [shift] of the [base] integer
 * located at [offset]. Bit positions count from the LSB.
 */
class BitElement(
    data: MemoryMap,
    offset: Int,
    private val base: IntType,
    val nbits: Int,
    private val shift: Int,
) : DataElement(data, offset) {
    override val sizeBits get() = nbits
    private val mask: Long get() = ((1L shl nbits) - 1) shl (shift - nbits)

    override var value: Long
        get() = (base.readUnsigned(data, offset) and mask) ushr (shift - nbits)
        set(v) {
            val cur = base.readUnsigned(data, offset) and mask.inv()
            base.write(data, offset, ((v shl (shift - nbits)) and mask) or cur)
        }

    /** Treat the bits as big-endian BCD (e.g. 0x243 -> 243). */
    var bbcd: Long
        get() = Bcd.bbcdToDec(value)
        set(dec) { value = Bcd.decToBbcd(dec) }

    override fun getRaw(): ByteArray = data.getBytes(offset, base.bytes)
    override fun toString() = "0x" + value.toString(16).uppercase()
}

class CharElement(data: MemoryMap, offset: Int) : DataElement(data, offset) {
    override val sizeBits get() = 8
    /** Raw byte as a Latin-1 character (CHIRP's "straight" decode). */
    override var string: String
        get() = data[offset].toChar().toString()
        set(v) { data[offset] = if (v.isEmpty()) 0 else v[0].code and 0xFF }
    override var value: Long
        get() = data[offset].toLong()
        set(v) { data[offset] = v.toInt() }
    override fun toString() = string
}

/** One BCD byte; [bigEndian] only matters when it's part of an array. */
class BcdElement(data: MemoryMap, offset: Int, val bigEndian: Boolean) : DataElement(data, offset) {
    override val sizeBits get() = 8
    var ignoreMask: Int = 0

    /** Raw byte, minus ignored bits. */
    val rawValue: Int get() = data[offset] and ignoreMask.inv()

    override var value: Long
        get() = Bcd.bbcdToDec(rawValue.toLong())
        set(v) {
            val preserve = data[offset] and ignoreMask
            data[offset] = (Bcd.decToBbcd(v and 0xFF).toInt() and ignoreMask.inv()) or preserve
        }

    fun setBits(mask: Int) { data[offset] = data[offset] or mask }
    fun clrBits(mask: Int) { data[offset] = data[offset] and mask.inv() }
    fun getBits(mask: Int): Int = data[offset] and mask

    override fun toString() = value.toString()
}

class ArrayElement(data: MemoryMap, offset: Int) : DataElement(data, offset), Iterable<DataElement> {
    private val items = ArrayList<DataElement>()

    internal fun add(item: DataElement) { items += item }

    override val count get() = items.size
    override val sizeBits get() = items.sumOf { it.sizeBits }
    override fun get(index: Int): DataElement = items[index]
    override fun iterator(): Iterator<DataElement> = items.iterator()

    private val first get() = items.firstOrNull()

    override var value: Long
        get() = when (val f = first) {
            is BcdElement -> {
                val ordered = if (f.bigEndian) items else items.asReversed()
                ordered.fold(0L) { acc, it -> acc * 100 + it.value }
            }
            else -> throw UnsupportedOperationException("Cannot coerce this array to int")
        }
        set(v) {
            when (val f = first) {
                is BcdElement -> {
                    var rest = v
                    val ordered = if (f.bigEndian) items.asReversed() else items
                    for (it in ordered) { it.value = rest % 100; rest /= 100 }
                }
                else -> throw UnsupportedOperationException("Cannot set this array from int")
            }
        }

    override var string: String
        get() = if (first is CharElement) items.joinToString("") { it.string }
        else throw UnsupportedOperationException("Not a char array")
        set(v) {
            if (first !is CharElement) throw UnsupportedOperationException("Not a char array")
            require(v.length == items.size) { "String expects exactly ${items.size} characters, not ${v.length}" }
            v.forEachIndexed { i, c -> items[i].string = c.toString() }
        }

    override fun getRaw(): ByteArray {
        val first = first ?: return ByteArray(0)
        return if (first is BitElement) data.getBytes(offset, (sizeBits + 7) / 8) else data.getBytes(offset, sizeBits / 8)
    }

    override fun setRaw(bytes: ByteArray) {
        val itemSize = (first?.sizeBits ?: 8) / 8
        require(itemSize > 0 && bytes.size == itemSize * items.size) { "Invalid raw data length" }
        items.forEachIndexed { i, it -> it.setRaw(bytes.copyOfRange(i * itemSize, (i + 1) * itemSize)) }
    }

    override fun toString() = when (first) {
        is CharElement -> string
        is BcdElement -> value.toString()
        else -> items.toString()
    }
}

open class StructElement(data: MemoryMap, offset: Int, val name: String = "(anonymous)") :
    DataElement(data, offset) {
    internal val fields = LinkedHashMap<String, DataElement>()

    val keys: Set<String> get() = fields.keys
    override val sizeBits get() = fields.values.sumOf { it.sizeBits }
    override fun get(name: String): DataElement =
        fields[name] ?: throw NoSuchElementException("No attribute $name in struct ${this.name}")
    override fun contains(name: String) = name in fields
    internal fun put(name: String, el: DataElement) { fields[name] = el }

    fun items(): List<Pair<String, DataElement>> = fields.entries.map { it.key to it.value }

    override fun toString() = "struct $name {" + fields.entries.joinToString(", ") { "${it.key}=${it.value}" } + "}"
}

class UnionElement(data: MemoryMap, offset: Int, name: String) : StructElement(data, offset, name) {
    internal var unionSizeBits: Int = 0
    override val sizeBits get() = unionSizeBits
}

object Bcd {
    fun bbcdToDec(bbcd: Long, packed: Boolean = true): Long {
        var v = bbcd
        var result = 0L
        var mul = 1L
        val shift = if (packed) 4 else 8
        while (v > 0) {
            result += (v and 0xF) * mul
            v = v ushr shift
            mul *= 10
        }
        return result
    }

    fun decToBbcd(dec: Long, packed: Boolean = true): Long {
        var d = dec
        var result = 0L
        var shift = 0
        val step = if (packed) 4 else 8
        while (d > 0) {
            result += (d % 10) shl shift
            d /= 10
            shift += step
        }
        return result
    }
}
