package chirprea.core.bitwise

import chirprea.core.memmap.MemoryMap
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BitwiseTest {
    private fun map(vararg bytes: Int) = MemoryMap(ByteArray(bytes.size) { bytes[it].toByte() })

    @Test
    fun integersRespectEndianness() {
        val m = map(0x12, 0x34, 0x12, 0x34, 0x01, 0x02, 0x03, 0xFF, 0xFE, 0xFF)
        val o = Bitwise.parse("u16 be; ul16 le; u24 b24; i8 neg; il16 x;", m)
        assertEquals(0x1234, o["be"].int)
        assertEquals(0x3412, o["le"].int)
        assertEquals(0x010203, o["b24"].int)
        assertEquals(-1, o["neg"].int)
        assertEquals(8, o["x"].offset)
        assertEquals(-2, o["x"].int)
    }

    @Test
    fun writesGoThroughToMemory() {
        val m = map(0, 0, 0, 0)
        val o = Bitwise.parse("ul32 v;", m)
        o["v"].value = 0x11223344
        assertContentEquals(byteArrayOf(0x44, 0x33, 0x22, 0x11), m.toByteArray())
    }

    @Test
    fun bitfieldsAllocateFromMostSignificantBit() {
        val m = map(0b1010_0110)
        val o = Bitwise.parse("u8 a:1, b:3, c:4;", m)
        assertEquals(1, o["a"].int)
        assertEquals(0b010, o["b"].int)
        assertEquals(0b0110, o["c"].int)
        o["b"].int = 0b111
        assertEquals(0b1111_0110, m[0])
    }

    @Test
    fun bitArraysBigAndLittleEndian() {
        val m = map(0b1000_0001, 0b1000_0000)
        val o = Bitwise.parse("bit big[8]; lbit little[8];", m)
        assertEquals(1, o["big"][0].int)
        assertEquals(0, o["big"][1].int)
        assertEquals(1, o["big"][7].int)
        assertEquals(0, o["little"][0].int)
        assertEquals(1, o["little"][7].int)
        assertEquals(1, o["little"].offset)
    }

    @Test
    fun bcdArrays() {
        // 146.52000 MHz stored as lbcd[4] in units of 10 Hz: 14652000
        val m = map(0x00, 0x20, 0x65, 0x14, 0x14, 0x65)
        val o = Bitwise.parse("lbcd freq[4]; bbcd b[2];", m)
        assertEquals(14652000L, o["freq"].value)
        assertEquals(1465L, o["b"].value)
        o["freq"].value = 44612500
        assertContentEquals(byteArrayOf(0x00, 0x25, 0x61, 0x44), m.getBytes(0, 4))
    }

    @Test
    fun charArraysAndStructArrays() {
        val m = MemoryMap(ByteArray(0x20) { 0xFF.toByte() })
        val spec = """
            #seekto 0x10;
            struct {
              u8 flag;   // comment
              char name[3];
            } mem[2];
        """.trimIndent()
        val o = Bitwise.parse(spec, m)
        val second = o["mem"][1]
        assertEquals(0x14, second.offset)
        second["name"].string = "ABC"
        assertEquals("ABC", o.path(".mem[1].name").string)
        assertEquals('A'.code, m[0x15])
        assertEquals(32, o["mem"][0].sizeBits)
    }

    @Test
    fun namedStructTypesAndUnions() {
        val m = map(0x01, 0x02, 0x03, 0x04, 0x05)
        val spec = """
            struct pair { u8 a; u8 b; };
            struct pair p;
            union { u16 whole; bbcd digits[2]; } u;
            u8 tail;
        """.trimIndent()
        val o = Bitwise.parse(spec, m)
        assertEquals(1, o["p"]["a"].int)
        assertEquals(0x0304, o["u"]["whole"].int)
        assertEquals(304L, o["u"]["digits"].value)
        assertEquals(16, o["u"].sizeBits)
        assertEquals(5, o["tail"].int)
    }

    @Test
    fun duplicateNamesAreRenamed() {
        val warnings = mutableListOf<String>()
        Bitwise.warningSink = { warnings += it }
        try {
            val o = Bitwise.parse("u8 x; u8 x;", map(1, 2))
            assertTrue("x_000001" in o)
            assertEquals(1, warnings.size)
        } finally {
            Bitwise.warningSink = {}
        }
    }

    @Test
    fun syntaxErrorsAreReported() {
        assertFailsWith<ParseError> { Bitwise.parse("u8 x", map(0)) }
        assertFailsWith<ParseError> { Bitwise.parse("foo x;", map(0)) }
    }

    @Test
    fun bcdHelpers() {
        assertEquals(243L, Bcd.bbcdToDec(0x243))
        assertEquals(0x243L, Bcd.decToBbcd(243))
        assertEquals(243L, Bcd.bbcdToDec(0x020403, packed = false))
    }
}
