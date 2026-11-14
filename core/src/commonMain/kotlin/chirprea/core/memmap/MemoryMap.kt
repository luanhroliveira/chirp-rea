package chirprea.core.memmap

/**
 * Mutable byte image of a radio's memory, the Kotlin counterpart of CHIRP's
 * `MemoryMapBytes`. All values are unsigned bytes (0..255).
 */
class MemoryMap(initial: ByteArray) {
    private var data: ByteArray = initial.copyOf()

    constructor(size: Int, fill: Int = 0xFF) : this(ByteArray(size) { fill.toByte() })

    val size: Int get() = data.size

    operator fun get(index: Int): Int = data[index].toInt() and 0xFF

    operator fun set(index: Int, value: Int) {
        data[index] = (value and 0xFF).toByte()
    }

    /** Returns [length] bytes starting at [start]; `-1` means "to the end". */
    fun getBytes(start: Int, length: Int = 1): ByteArray =
        if (length == -1) data.copyOfRange(start, data.size)
        else data.copyOfRange(start, start + length)

    /** Overwrites `value.size` bytes starting at [pos]. */
    fun setBytes(pos: Int, value: ByteArray) {
        value.copyInto(data, pos)
    }

    fun truncate(newSize: Int) {
        data = data.copyOf(newSize)
    }

    fun toByteArray(): ByteArray = data.copyOf()

    fun hexDump(start: Int = 0, end: Int = data.size): String = HexDump.format(data, start, end)
}

object HexDump {
    fun format(bytes: ByteArray, start: Int = 0, end: Int = bytes.size, width: Int = 16): String {
        val sb = StringBuilder()
        var pos = start
        while (pos < end) {
            val lineEnd = minOf(pos + width, end)
            sb.append(pos.toString(16).padStart(4, '0').uppercase()).append(": ")
            for (i in pos until pos + width) {
                if (i < lineEnd) sb.append(hex2(bytes[i].toInt() and 0xFF)).append(' ') else sb.append("   ")
            }
            sb.append("  ")
            for (i in pos until lineEnd) {
                val b = bytes[i].toInt() and 0xFF
                sb.append(if (b in 0x20..0x7E) b.toChar() else '.')
            }
            sb.append('\n')
            pos = lineEnd
        }
        return sb.toString()
    }

    private fun hex2(v: Int) = v.toString(16).padStart(2, '0').uppercase()
}
