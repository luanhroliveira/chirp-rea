package chirprea.app.serial

import chirprea.core.radio.PortInfo
import chirprea.core.radio.SerialPort
import chirprea.core.radio.SerialPortProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.fazecast.jSerialComm.SerialPort as JPort

/** Desktop [SerialPort] backed by jSerialComm. */
class JSerialCommPort(private val port: JPort) : SerialPort {
    override val name: String get() = port.systemPortName

    override var baudRate: Int
        get() = port.baudRate
        set(v) { port.setBaudRate(v) }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val n = port.writeBytes(bytes, bytes.size)
        check(n == bytes.size) { "Serial write failed ($n of ${bytes.size} bytes)" }
    }

    override suspend fun read(count: Int, timeoutMs: Long): ByteArray = withContext(Dispatchers.IO) {
        port.setComPortTimeouts(JPort.TIMEOUT_READ_SEMI_BLOCKING, timeoutMs.toInt(), 0)
        val buf = ByteArray(count)
        var got = 0
        val deadline = System.currentTimeMillis() + timeoutMs
        while (got < count && System.currentTimeMillis() < deadline) {
            val n = port.readBytes(buf, count - got, got)
            if (n < 0) break
            got += n
        }
        buf.copyOf(got)
    }

    override suspend fun setRts(on: Boolean) { if (on) port.setRTS() else port.clearRTS() }
    override suspend fun setDtr(on: Boolean) { if (on) port.setDTR() else port.clearDTR() }

    override suspend fun flushInput() = withContext(Dispatchers.IO) {
        val pending = port.bytesAvailable()
        if (pending > 0) port.readBytes(ByteArray(pending), pending)
        Unit
    }

    override suspend fun close() = withContext(Dispatchers.IO) { port.closePort(); Unit }
}

object JSerialCommProvider : SerialPortProvider {
    override suspend fun list(): List<PortInfo> = withContext(Dispatchers.IO) {
        PortInfo.sortForCables(
            JPort.getCommPorts().map { p ->
                // On Windows the descriptive name looks like "USB-SERIAL CH340 (COM6)".
                val desc = p.descriptivePortName.replace("(${p.systemPortName})", "").trim()
                PortInfo(p.systemPortName, desc.ifEmpty { p.portDescription })
            },
        )
    }

    override suspend fun open(name: String, baudRate: Int): SerialPort = withContext(Dispatchers.IO) {
        val p = JPort.getCommPort(name)
        p.setComPortParameters(baudRate, 8, JPort.ONE_STOP_BIT, JPort.NO_PARITY)
        check(p.openPort()) { "Não foi possível abrir $name" }
        JSerialCommPort(p)
    }
}
