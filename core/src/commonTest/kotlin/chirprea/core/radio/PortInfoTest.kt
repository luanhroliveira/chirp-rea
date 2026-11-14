package chirprea.core.radio

import kotlin.test.Test
import kotlin.test.assertEquals

class PortInfoTest {
    @Test
    fun usbCableComesBeforeBluetoothPorts() {
        val ports = listOf(
            PortInfo("COM4", "Serial Padrão por link Bluetooth"),
            PortInfo("COM5", "Serial Padrão por link Bluetooth"),
            PortInfo("COM6", "USB-SERIAL CH340"),
        )
        val sorted = PortInfo.sortForCables(ports)
        assertEquals("COM6", sorted.first().name)
        assertEquals("COM6 — USB-SERIAL CH340", sorted.first().label)
    }
}
