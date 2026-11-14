package chirprea.core.drivers.baofeng

import chirprea.core.radio.CloneStatus
import chirprea.core.radio.RadioException
import chirprea.core.radio.SerialPort
import kotlinx.coroutines.delay

/**
 * Where and how a radio stores its power-on logo.
 *
 * Reverse engineered from Baofeng's "Import Picture tools.exe" v1.2.4.11
 * (shipped with "BF-UVs Pro En CPS (Modified)"): model "UV-21 Pro" uses a
 * 128x160 image at flash 0x100000; "UV-17/18/19/20 Pro" uses 150x128 at 0xC0000.
 */
data class LogoSpec(
    val width: Int,
    val height: Int,
    val flashAddress: Int,
    val ident: String = "PROGRAMCOLORPROU",
) {
    val byteCount: Int get() = width * height * 2
}

/** Radios whose power-on logo can be replaced. */
interface SupportsLogo {
    val logoSpec: LogoSpec
}

/**
 * Logo upload protocol of the Baofeng "COLOR PRO" family. All multi-byte
 * header fields are big-endian; the flash address payload is little-endian.
 *
 * Packet: `A5 cmd idHi idLo lenHi lenLo data… crcHi crcLo`, CRC-16/XMODEM
 * (poly 0x1021, init 0) over `cmd … data`. The radio answers each packet with a
 * packet carrying the same cmd and the single data byte `'Y'`.
 */
object BaofengLogo {
    const val BAUD_RATE = 115200
    const val PACKET_DATA = 1024

    private const val CMD_HANDSHAKE = 2
    private const val CMD_ADDRESS = 3
    private const val CMD_ERASE = 4
    private const val CMD_END = 6
    private const val CMD_WRITE = 87 // 'W'
    private const val ERASE_ID = 0x4504
    private const val ACK = 'Y'.code.toByte()

    /** Convert ARGB pixels (row-major, top-left first) to the radio's RGB565 little-endian bytes. */
    fun toRgb565(argb: IntArray, width: Int, height: Int): ByteArray {
        require(argb.size == width * height) { "Expected ${width * height} pixels, got ${argb.size}" }
        val out = ByteArray(width * height * 2)
        for (i in argb.indices) {
            val p = argb[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val v = ((r shr 3) shl 11) or ((g shr 2) shl 5) or (b shr 3)
            out[2 * i] = v.toByte()
            out[2 * i + 1] = (v shr 8).toByte()
        }
        return out
    }

    /** Inverse of [toRgb565], used to preview exactly what the radio will show. */
    fun fromRgb565(data: ByteArray): IntArray = IntArray(data.size / 2) { i ->
        val v = (data[2 * i].toInt() and 0xFF) or ((data[2 * i + 1].toInt() and 0xFF) shl 8)
        val r = (v shr 11) and 0x1F
        val g = (v shr 5) and 0x3F
        val b = v and 0x1F
        (0xFF shl 24) or ((r * 255 / 31) shl 16) or ((g * 255 / 63) shl 8) or (b * 255 / 31)
    }

    /** CRC-16/XMODEM, as in the tool's `CrcValidation`. */
    fun crc16(data: ByteArray, start: Int, length: Int): Int {
        var crc = 0
        for (i in start until start + length) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return crc
    }

    fun packet(cmd: Int, id: Int, data: ByteArray): ByteArray {
        val p = ByteArray(6 + data.size + 2)
        p[0] = 0xA5.toByte()
        p[1] = cmd.toByte()
        p[2] = (id shr 8).toByte(); p[3] = id.toByte()
        p[4] = (data.size shr 8).toByte(); p[5] = data.size.toByte()
        data.copyInto(p, 6)
        val crc = crc16(p, 1, 5 + data.size)
        p[6 + data.size] = (crc shr 8).toByte()
        p[7 + data.size] = crc.toByte()
        return p
    }

    /** Parse a reply packet; returns (cmd, data) or null if malformed/bad CRC. */
    fun parse(p: ByteArray): Pair<Int, ByteArray>? {
        if (p.size < 8 || p[0] != 0xA5.toByte()) return null
        val len = ((p[4].toInt() and 0xFF) shl 8) or (p[5].toInt() and 0xFF)
        if (p.size < 8 + len) return null
        val crc = ((p[6 + len].toInt() and 0xFF) shl 8) or (p[7 + len].toInt() and 0xFF)
        if (crc != crc16(p, 1, 5 + len)) return null
        return (p[1].toInt() and 0xFF) to p.copyOfRange(6, 6 + len)
    }

    private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

    /**
     * Upload [rgb565] (exactly [LogoSpec.byteCount] bytes) to the radio.
     * Sequence: ident → 'D' → handshake → erase → address → data packets → end.
     */
    suspend fun upload(port: SerialPort, spec: LogoSpec, rgb565: ByteArray, status: (CloneStatus) -> Unit = {}) {
        require(rgb565.size == spec.byteCount) { "A imagem precisa ter ${spec.width}x${spec.height} pixels" }
        val packets = (rgb565.size + PACKET_DATA - 1) / PACKET_DATA
        val total = packets + 3
        port.baudRate = BAUD_RATE
        port.setDtr(true)
        port.setRts(true)

        status(CloneStatus("Conectando…", 0, total))
        enterProgramMode(port, spec)

        exchange(port, CMD_HANDSHAKE, 0, "PROGRAM".encodeToByteArray())
        status(CloneStatus("Apagando a área da logo…", 1, total))
        exchange(port, CMD_ERASE, ERASE_ID, le32(spec.flashAddress) + byteArrayOf(0x00, 0x01))
        exchange(port, CMD_ADDRESS, 0, le32(spec.flashAddress))
        status(CloneStatus("Gravando a logo…", 3, total))

        for (id in 0 until packets) {
            val chunk = ByteArray(PACKET_DATA) { 0xFF.toByte() }
            val start = id * PACKET_DATA
            rgb565.copyInto(chunk, 0, start, minOf(start + PACKET_DATA, rgb565.size))
            exchange(port, CMD_WRITE, id, chunk)
            status(CloneStatus("Gravando a logo…", 4 + id, total))
        }

        port.write(packet(CMD_END, 0, "Over".encodeToByteArray()))
        delay(100)
        status(CloneStatus("Logo gravada", total, total))
    }

    private suspend fun enterProgramMode(port: SerialPort, spec: LogoSpec) {
        val ident = spec.ident.encodeToByteArray()
        port.flushInput()
        repeat(RETRIES) {
            port.write(ident)
            val ack = port.read(1, TIMEOUT_MS)
            if (ack.isNotEmpty() && ident.startsWith(ack)) throw RadioException(BaofengUV17Pro.ECHO_MESSAGE)
            if (ack.size == 1 && ack[0] == 0x06.toByte()) {
                port.write(byteArrayOf(0x44)) // 'D'
                delay(100)
                return
            }
        }
        throw RadioException("O rádio não respondeu ao handshake da logo")
    }

    /** Send a packet and wait for the matching 'Y' reply, retrying like the official tool. */
    private suspend fun exchange(port: SerialPort, cmd: Int, id: Int, data: ByteArray) {
        val pkt = packet(cmd, id, data)
        var last = "sem resposta"
        repeat(RETRIES) {
            port.write(pkt)
            val reply = readPacket(port)
            if (reply == null) {
                last = "sem resposta ou CRC inválido"
                return@repeat
            }
            val (rcmd, rdata) = reply
            if (rcmd == cmd && rdata.size == 1 && rdata[0] == ACK) return
            throw RadioException("O rádio recusou o comando $cmd (resposta ${rdata.joinToString(" ") { (it.toInt() and 0xFF).toString(16) }})")
        }
        throw RadioException("Falha no comando $cmd da logo: $last")
    }

    private suspend fun readPacket(port: SerialPort): Pair<Int, ByteArray>? {
        val head = port.read(1, TIMEOUT_MS)
        if (head.isEmpty() || head[0] != 0xA5.toByte()) return null
        val hdr = port.read(5, TIMEOUT_MS)
        if (hdr.size != 5) return null
        val len = ((hdr[3].toInt() and 0xFF) shl 8) or (hdr[4].toInt() and 0xFF)
        val rest = port.read(len + 2, TIMEOUT_MS)
        return parse(head + hdr + rest)
    }

    private const val RETRIES = 5
    private const val TIMEOUT_MS = 1000L

    private fun ByteArray.startsWith(prefix: ByteArray) =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
