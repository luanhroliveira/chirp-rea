package chirprea.core.drivers.baofeng

import chirprea.core.memmap.MemoryMap
import chirprea.core.model.Memory
import chirprea.core.radio.ImageFile
import chirprea.core.radio.RadioException
import chirprea.core.radio.SerialPort
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A cable whose plug is not fully seated: everything sent comes straight back. */
private class EchoCable : SerialPort {
    override val name = "ECHO"
    override var baudRate = 9600
    private val pending = ArrayDeque<Byte>()
    override suspend fun write(bytes: ByteArray) = bytes.forEach(pending::addLast)
    override suspend fun read(count: Int, timeoutMs: Long) = ByteArray(minOf(count, pending.size)) { pending.removeFirst() }
    override suspend fun flushInput() = pending.clear()
    override suspend fun close() {}
}

/** Simulates a UV-17Pro-family radio on the other end of the cable. */
private class FakeRadio(
    val image: ByteArray,
    var silent: Boolean = false,
    /** Ident string this radio accepts; any other ident gets no reply (like the real radio). */
    val ident: String = "PROGRAMBFNORMALU",
    /** Reply to the 0x4D model query (UV-17Pro: 15 bytes, COLOR PRO firmware: "UV21PRO"). */
    val model: String = "UV17PROXXXXXXXX",
) : SerialPort {
    override val name = "FAKE"
    override var baudRate = 9600
    private val pending = ArrayDeque<Byte>()
    val writes = HashMap<Int, ByteArray>()

    private val starts = listOf(0x0000, 0x9000, 0xA000, 0xD000)
    private val sizes = listOf(0x8040, 0x0040, 0x02C0, 0x0040)

    /** Radio address -> offset in the flat image. */
    private fun imageOffset(addr: Int): Int {
        var base = 0
        for ((s, z) in starts.zip(sizes)) {
            if (addr in s until s + z) return base + (addr - s)
            base += z
        }
        error("bad address $addr")
    }

    private fun reply(vararg b: Byte) = b.forEach(pending::addLast)

    override suspend fun write(bytes: ByteArray) {
        if (silent) return
        val text = bytes.decodeToString()
        when {
            text == ident -> reply(0x06)
            text.startsWith("PROGRAM") -> {} // wrong ident: real radios stay silent
            bytes.size == 1 && bytes[0] == 0x46.toByte() -> reply(*ByteArray(16) { 0x30 })
            bytes.size == 1 && bytes[0] == 0x4D.toByte() -> reply(*model.encodeToByteArray())
            text.startsWith("SEND!") -> reply(0x06)
            bytes[0] == 'R'.code.toByte() -> {
                val addr = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
                val off = imageOffset(addr)
                val data = BaofengUV17Pro.crypt(1, image.copyOfRange(off, off + 0x40))
                reply(*bytes.copyOfRange(0, 4), *data)
            }
            bytes[0] == 'W'.code.toByte() -> {
                val addr = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
                writes[imageOffset(addr)] = BaofengUV17Pro.crypt(1, bytes.copyOfRange(4, bytes.size))
                reply(0x06)
            }
            else -> error("unexpected frame ${bytes.toList()}")
        }
    }

    override suspend fun read(count: Int, timeoutMs: Long): ByteArray {
        val n = minOf(count, pending.size)
        return ByteArray(n) { pending.removeFirst() }
    }

    override suspend fun flushInput() = pending.clear()
    override suspend fun close() {}
}

class BaofengUV17ProTest {
    private fun blankRadio(): BaofengUV21ProV2 = BaofengUV21ProV2().apply {
        loadMmap(MemoryMap(memSize, 0xFF))
    }

    private fun hex(s: String) = s.split(" ").map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun cryptIsSymmetricAndSkipsSpecialBytes() {
        val plain = byteArrayOf(0x00, 0xFF.toByte(), 0x43, 0x12, 0x34, 0x56, 0x78, 0x9A.toByte())
        val enc = BaofengUV17Pro.crypt(1, plain)
        // key "CO 7": 0x00/0xFF untouched, 0x43 == key byte untouched, key ' ' never encrypts
        assertEquals(0x00, enc[0].toInt())
        assertEquals(0x43, enc[2].toInt())
        assertEquals(0x34 xor 0x43, enc[4].toInt() and 0xFF)
        assertEquals(0x56 xor 0x4F, enc[5].toInt() and 0xFF)
        assertEquals(0x78, enc[6].toInt())
        assertContentEquals(plain, BaofengUV17Pro.crypt(1, enc))
    }

    @Test
    fun memoryLayoutMatchesChirp() {
        val r = blankRadio()
        r.setMemory(
            Memory(
                number = 1, name = "TEST", freq = 146_520_000, duplex = "+", offset = 600_000,
                tmode = "Tone", rtone = 88.5, mode = "FM", power = r.powerLevels[0],
            ),
        )
        // rxfreq lbcd[4] | txfreq lbcd[4] | rxtone ul16 (none) | txtone ul16 (885 = 88.5 Hz)
        // | scode | pttid | power bits | flags (scan=1) | 4 unknown bytes (0xFF) | name[12]
        val expected = hex("00 20 65 14 00 20 71 14 00 00 75 03 00 00 00 04 FF FF FF FF") +
            "TEST".encodeToByteArray() + ByteArray(8) { 0xFF.toByte() }
        assertContentEquals(expected, r.mmap.getBytes(0, 32))
    }

    @Test
    fun memoryRoundTrips() {
        val r = blankRadio()
        val cases = listOf(
            Memory(number = 1, name = "RPT", freq = 147_000_000, duplex = "+", offset = 600_000, tmode = "TSQL", ctone = 100.0, power = r.powerLevels[1]),
            Memory(number = 2, name = "DCS", freq = 446_006_250, tmode = "DTCS", dtcs = 754, dtcsPolarity = "RR", mode = "NFM", power = r.powerLevels[0]),
            Memory(number = 3, name = "XBAND", freq = 145_500_000, duplex = "split", offset = 435_000_000, skip = "S", power = r.powerLevels[0]),
            Memory(number = 4, name = "RXONLY", freq = 162_550_000, duplex = "off", offset = 0, power = r.powerLevels[0]),
            Memory(number = 5, name = "AIR", freq = 118_100_000, mode = "AM", power = r.powerLevels[0]),
            Memory(number = 6, name = "CROSS", freq = 146_000_000, tmode = "Cross", crossMode = "Tone->DTCS", rtone = 67.0, rxDtcs = 23, dtcsPolarity = "NN", power = r.powerLevels[0]),
        )
        for (c in cases) {
            r.setMemory(c)
            val back = r.getMemory(c.number)
            assertEquals(c.freq, back.freq, c.name)
            assertEquals(c.name, back.name)
            assertEquals(c.duplex, back.duplex, c.name)
            assertEquals(if (c.duplex == "") 0 else c.offset, back.offset, c.name)
            assertEquals(c.tmode, back.tmode, c.name)
            assertEquals(c.skip, back.skip, c.name)
            assertEquals(c.mode, back.mode, c.name)
            assertEquals(c.power, back.power, c.name)
            when (c.tmode) {
                "TSQL" -> assertEquals(c.ctone, back.ctone)
                "DTCS" -> { assertEquals(c.dtcs, back.dtcs); assertEquals(c.dtcsPolarity, back.dtcsPolarity) }
                "Cross" -> { assertEquals(c.crossMode, back.crossMode); assertEquals(c.rtone, back.rtone); assertEquals(c.rxDtcs, back.rxDtcs) }
            }
            assertTrue(r.features().validate(back).isEmpty(), "${c.name}: ${r.features().validate(back)}")
        }
        r.eraseMemory(2)
        assertTrue(r.getMemory(2).empty)
        assertTrue(r.getMemory(1000).empty)
    }

    @Test
    fun downloadAndUploadThroughSimulatedRadio() = runTest {
        // Build a "radio" whose memory 7 holds a channel.
        val source = blankRadio()
        source.setMemory(Memory(number = 7, name = "SIMPLEX", freq = 146_520_000, power = source.powerLevels[0]))
        val image = source.mmap.toByteArray()
        val radio = FakeRadio(image)

        val r = BaofengUV21ProV2()
        var lastStatus = 0
        r.syncIn(radio) { lastStatus = it.current }
        assertEquals(BaofengUV17Pro.BAUD_RATE, radio.baudRate)
        assertEquals(image.size / 0x40, lastStatus)
        assertContentEquals(image, r.mmap.toByteArray())
        assertEquals("SIMPLEX", r.getMemory(7).name)

        r.setMemory(Memory(number = 8, name = "NEW", freq = 433_500_000, power = r.powerLevels[1]))
        r.syncOut(radio)
        val written = ByteArray(image.size)
        radio.writes.forEach { (off, data) -> data.copyInto(written, off) }
        assertContentEquals(r.mmap.toByteArray(), written)
    }

    @Test
    fun silentRadioReportsNoResponse() = runTest {
        val e = assertFailsWith<RadioException> { BaofengUV21ProV2().syncIn(FakeRadio(ByteArray(0), silent = true)) }
        assertTrue("handshake" in e.message!!)
    }

    @Test
    fun colorProUv21DownloadsWithGpsDriverOnly() = runTest {
        // Mirrors the real UV-21 Pro probed on COM6: accepts PROGRAMCOLORPROU, reports "UV21PRO".
        val source = blankRadio()
        source.setMemory(Memory(number = 1, name = "AIRPORT", freq = 118_100_000, mode = "AM", power = source.powerLevels[0]))
        val radio = FakeRadio(source.mmap.toByteArray(), ident = "PROGRAMCOLORPROU", model = "UV21PRO")

        val r = BaofengUV21ProGPS()
        r.syncIn(radio)
        assertEquals("AIRPORT", r.getMemory(1).name)
        assertEquals("AM", r.getMemory(1).mode)
        assertEquals("UV21ProGPS", ImageFile.rclass(r.toImage()))

        // The non-GPS driver sends PROGRAMBFNORMALU, which this radio ignores.
        val e = assertFailsWith<RadioException> { BaofengUV17Pro().syncIn(radio) }
        assertTrue("handshake" in e.message!!)
    }

    @Test
    fun gpsFamilyHasTenStaticBanksWithEditableNames() {
        val r = BaofengUV21ProGPS().apply { loadMmap(MemoryMap(memSize, 0xFF)) }
        val model = r.bankModel()
        assertEquals(10, model.banks.size)
        assertEquals(1..100, model.memoriesIn(model.banks[0]))
        assertEquals(901..1000, model.memoriesIn(model.banks[9]))
        assertEquals(3, model.bankOf(250)!!.index)
        assertEquals("", model.banks[0].name)

        model.rename(model.banks[2], "REPETIDORAS")
        assertEquals("REPETIDORAS", model.banks[2].name)
        val raw = r.mmap.getBytes(0x8280 + 2 * 16, 16)
        assertContentEquals("REPETIDORAS".encodeToByteArray() + ByteArray(5) { 0xFF.toByte() }, raw)
        assertFailsWith<IllegalArgumentException> { model.rename(model.banks[0], "Ação") }

        assertTrue(r.info().any { it.label == "Driver (classe CHIRP)" && it.value == "UV21ProGPS" })
    }

    @Test
    fun cableEchoIsReportedAsLoosePlug() = runTest {
        val e = assertFailsWith<RadioException> { BaofengUV17Pro().syncIn(EchoCable()) }
        assertEquals(BaofengUV17Pro.ECHO_MESSAGE, e.message)
    }

    @Test
    fun imageFileKeepsDriverIdentity() {
        val r = blankRadio()
        r.setMemory(Memory(number = 1, name = "A", freq = 145_000_000, power = r.powerLevels[0]))
        val img = r.toImage()
        assertEquals("UV21ProV2", ImageFile.rclass(img))
        val again = BaofengUV21ProV2().apply { loadImage(img) }
        assertEquals("A", again.getMemory(1).name)
    }
}
