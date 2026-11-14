package chirprea.core.radio

/**
 * Platform-neutral serial port. Desktop implements it with jSerialComm,
 * the web target will implement it with the Web Serial API.
 */
interface SerialPort {
    val name: String
    var baudRate: Int

    suspend fun write(bytes: ByteArray)

    /** Read up to [count] bytes, returning fewer if [timeoutMs] elapses. */
    suspend fun read(count: Int, timeoutMs: Long = 1000): ByteArray

    /** Read exactly [count] bytes or throw [RadioException]. */
    suspend fun readExactly(count: Int, timeoutMs: Long = 1000): ByteArray {
        val data = read(count, timeoutMs)
        if (data.size != count) throw RadioException("Radio did not respond (got ${data.size} of $count bytes)")
        return data
    }

    suspend fun setRts(on: Boolean) {}
    suspend fun setDtr(on: Boolean) {}

    /** Discard unread input. */
    suspend fun flushInput() {}

    suspend fun close()
}

/** A serial port as seen by the OS, e.g. `COM6` / "USB-SERIAL CH340". */
data class PortInfo(val name: String, val description: String = "") {
    /** USB-to-serial adapters used by programming cables (vs. Bluetooth/modem ports). */
    val isUsbCable: Boolean
        get() = USB_HINTS.any { description.contains(it, ignoreCase = true) }

    val isBluetooth: Boolean get() = description.contains("bluetooth", ignoreCase = true)

    val label: String get() = if (description.isBlank() || description == name) name else "$name — $description"

    companion object {
        private val USB_HINTS = listOf("CH340", "CH341", "CP210", "Prolific", "PL2303", "FTDI", "FT232", "USB")

        /** Programming cables first, Bluetooth last. */
        fun sortForCables(ports: List<PortInfo>): List<PortInfo> =
            ports.sortedWith(compareBy({ !it.isUsbCable }, { it.isBluetooth }, { it.name }))
    }
}

/** Lists and opens serial ports on the current platform. */
interface SerialPortProvider {
    suspend fun list(): List<PortInfo>
    suspend fun open(name: String, baudRate: Int): SerialPort
}
