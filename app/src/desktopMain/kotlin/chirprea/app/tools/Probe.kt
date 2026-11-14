package chirprea.app.tools

import chirprea.app.serial.JSerialCommProvider
import chirprea.core.radio.SerialPort
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.time.LocalDateTime

/**
 * Read-only radio probe: sends only the identification handshakes used by
 * CHIRP's Baofeng drivers and logs every byte the radio returns. It never
 * sends a write command, so it cannot change the radio's memory.
 *
 * Usage: ./gradlew :app:probe -Pport=COM6
 */
private data class Candidate(
    val family: String,
    val name: String,
    val baud: Int,
    val payload: ByteArray,
    val readLen: Int,
    /** Send one byte at a time (UV-5R family expects this). */
    val byteWise: Boolean = false,
    /** UV-5R: after ACK, request the 8-byte model ident with 0x02. */
    val followIdent: Boolean = false,
)

private fun hex(b: ByteArray) = b.joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }

private fun ascii(b: ByteArray) = b.map { val c = it.toInt() and 0xFF; if (c in 0x20..0x7E) c.toChar() else '.' }.joinToString("")

private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

private val CANDIDATES = buildList {
    // chirp/drivers/baofeng_uv17Pro.py
    for ((name, ident) in listOf(
        "UV-17Pro / UV-21ProV2" to "PROGRAMBFNORMALU",
        "UV-17ProGPS / UV-21ProGPS / UV-5R Mini" to "PROGRAMCOLORPROU",
        "GM-5RH" to "PROGRAMBFGMRS05U",
        "BF-F8HP-PRO" to "PROGRAMBF5RTECHU",
        "K6" to "PROGRAMBF-K6PROU",
    )) add(Candidate("UV17Pro", name, 115200, ident.encodeToByteArray(), 16))
    // chirp/drivers/baofeng_uv17.py
    add(Candidate("UV17", "UV-17 / UV-13Pro", 57600, "PSEARCH".encodeToByteArray(), 8))
    // chirp/drivers/uv5r.py
    for ((name, magic) in listOf(
        "UV-5R (291)" to bytes(0x50, 0xBB, 0xFF, 0x20, 0x12, 0x07, 0x25),
        "UV-5R (orig)" to bytes(0x50, 0xBB, 0xFF, 0x01, 0x25, 0x98, 0x4D),
        "BF-F11" to bytes(0x50, 0xBB, 0xFF, 0x13, 0xA1, 0x11, 0xDD),
        "UV-82" to bytes(0x50, 0xBB, 0xFF, 0x20, 0x13, 0x01, 0x05),
        "UV-6" to bytes(0x50, 0xBB, 0xFF, 0x20, 0x12, 0x08, 0x23),
        "UV-6 (orig)" to bytes(0x50, 0xBB, 0xFF, 0x12, 0x03, 0x98, 0x4D),
        "A-58" to bytes(0x50, 0xBB, 0xFF, 0x20, 0x14, 0x04, 0x13),
        "UV-5G" to bytes(0x50, 0xBB, 0xFF, 0x20, 0x12, 0x06, 0x25),
    )) add(Candidate("UV5R", name, 9600, magic, 1, byteWise = true, followIdent = true))
}

private suspend fun SerialPort.drain(ms: Long): ByteArray {
    val out = ArrayList<Byte>()
    val end = System.currentTimeMillis() + ms
    while (System.currentTimeMillis() < end) {
        val b = read(64, 100)
        b.forEach(out::add)
        if (b.isEmpty() && out.isNotEmpty()) break
    }
    return out.toByteArray()
}

fun main(args: Array<String>) = runBlocking {
    val portName = args.firstOrNull() ?: error("Informe a porta, ex.: COM6")
    val report = StringBuilder()
    fun log(s: String) { println(s); report.appendLine(s) }

    log("CHIRP-REA probe — ${LocalDateTime.now()} — porta $portName")
    log("Somente comandos de identificação; nenhum comando de escrita é enviado.\n")

    for (c in CANDIDATES) {
        val port = JSerialCommProvider.open(portName, c.baud)
        try {
            port.flushInput()
            if (c.byteWise) {
                for (b in c.payload) { port.write(byteArrayOf(b)); delay(10) }
            } else {
                port.write(c.payload)
            }
            val resp = port.drain(1500)
            val ack = resp.isNotEmpty() && resp[0] == 0x06.toByte()
            log("[${c.family}] ${c.name} @${c.baud}: enviado=${hex(c.payload)}")
            log("    resposta (${resp.size} bytes)= ${hex(resp)}   ascii='${ascii(resp)}'   ${if (ack) "ACK" else ""}")
            if (ack && c.followIdent) {
                port.write(byteArrayOf(0x02))
                val ident = port.drain(1500)
                log("    ident 0x02 -> (${ident.size} bytes)= ${hex(ident)}   ascii='${ascii(ident)}'")
            }
            if (ack && c.family == "UV17Pro") {
                // The remaining UV-17Pro magics are read-only queries (model/firmware info).
                for ((magic, label) in listOf(bytes(0x46) to "0x46", bytes(0x4D) to "0x4D")) {
                    port.write(magic)
                    val r = port.drain(1500)
                    log("    $label -> (${r.size} bytes)= ${hex(r)}   ascii='${ascii(r)}'")
                }
            }
        } catch (t: Throwable) {
            log("[${c.family}] ${c.name}: erro ${t.message}")
        } finally {
            port.close()
        }
        // Let the radio time out of any half-finished handshake before the next attempt.
        delay(2500)
    }

    val out = File("build/probe-${portName.lowercase()}.txt")
    out.parentFile.mkdirs()
    out.writeText(report.toString())
    println("\nRelatório salvo em ${out.absolutePath}")
}
