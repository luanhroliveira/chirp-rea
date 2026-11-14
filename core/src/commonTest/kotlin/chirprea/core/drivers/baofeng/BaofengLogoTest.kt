package chirprea.core.drivers.baofeng

import chirprea.core.radio.RadioException
import chirprea.core.radio.SerialPort
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simulates the logo-upload side of a COLOR PRO radio, following the protocol of Import Picture tools.exe. */
private class FakeLogoRadio(private val reject: Int? = null) : SerialPort {
    override val name = "FAKE"
    override var baudRate = 9600
    private val pending = ArrayDeque<Byte>()
    val commands = mutableListOf<Int>()
    var eraseAddress = -1
    var writeAddress = -1
    val flash = HashMap<Int, ByteArray>()
    var gotD = false
    var ended = false

    override suspend fun write(bytes: ByteArray) {
        when {
            bytes.decodeToString() == "PROGRAMCOLORPROU" -> pending.addLast(0x06)
            bytes.size == 1 && bytes[0] == 0x44.toByte() -> gotD = true
            bytes[0] == 0xA5.toByte() -> {
                val (cmd, data) = BaofengLogo.parse(bytes) ?: error("bad packet")
                commands += cmd
                val id = ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
                fun le(d: ByteArray) = (0..3).sumOf { (d[it].toInt() and 0xFF) shl (8 * it) }
                when (cmd) {
                    4 -> eraseAddress = le(data)
                    3 -> writeAddress = le(data)
                    87 -> flash[id] = data
                    6 -> { ended = true; return }
                }
                val answer = if (cmd == reject) byteArrayOf('N'.code.toByte()) else byteArrayOf('Y'.code.toByte())
                BaofengLogo.packet(cmd, 0, answer).forEach(pending::addLast)
            }
        }
    }

    override suspend fun read(count: Int, timeoutMs: Long) = ByteArray(minOf(count, pending.size)) { pending.removeFirst() }
    override suspend fun flushInput() = pending.clear()
    override suspend fun close() {}
}

class BaofengLogoTest {
    @Test
    fun crcIsXmodem() {
        assertEquals(0x31C3, BaofengLogo.crc16("123456789".encodeToByteArray(), 0, 9))
    }

    @Test
    fun rgb565IsLittleEndianAndRoundTrips() {
        val px = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt())
        val raw = BaofengLogo.toRgb565(px, 2, 2)
        assertContentEquals(byteArrayOf(0x00, 0xF8.toByte(), 0xE0.toByte(), 0x07, 0x1F, 0x00, 0xFF.toByte(), 0xFF.toByte()), raw)
        assertContentEquals(px, BaofengLogo.fromRgb565(raw))
    }

    @Test
    fun packetLayout() {
        val p = BaofengLogo.packet(2, 0, "PROGRAM".encodeToByteArray())
        assertEquals(0xA5.toByte(), p[0])
        assertContentEquals(byteArrayOf(2, 0, 0, 0, 7), p.copyOfRange(1, 6))
        assertEquals(6 + 7 + 2, p.size)
        assertEquals(2 to "PROGRAM", BaofengLogo.parse(p)!!.let { it.first to it.second.decodeToString() })
    }

    @Test
    fun uploadsUv21ProLogoLikeTheOfficialTool() = runTest {
        val spec = BaofengUV21ProGPS().logoSpec
        assertEquals(LogoSpec(128, 160, 0x100000), spec)
        val image = ByteArray(spec.byteCount) { (it * 7).toByte() }
        val radio = FakeLogoRadio()

        var lastStatus = 0
        BaofengLogo.upload(radio, spec, image) { lastStatus = it.current }

        assertEquals(BaofengLogo.BAUD_RATE, radio.baudRate)
        assertTrue(radio.gotD)
        assertEquals(listOf(2, 4, 3) + List(40) { 87 } + 6, radio.commands)
        assertEquals(0x100000, radio.eraseAddress)
        assertEquals(0x100000, radio.writeAddress)
        val written = (0 until 40).map { radio.flash.getValue(it) }.reduce(ByteArray::plus)
        assertContentEquals(image, written)
        assertTrue(radio.ended)
        assertEquals(43, lastStatus)
    }

    @Test
    fun rejectionIsAnErrorNotASilentSuccess() = runTest {
        val spec = BaofengUV21ProGPS().logoSpec
        val e = assertFailsWith<RadioException> {
            BaofengLogo.upload(FakeLogoRadio(reject = 4), spec, ByteArray(spec.byteCount))
        }
        assertTrue("recusou" in e.message!!)
    }
}
