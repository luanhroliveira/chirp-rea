package chirprea.core.drivers.baofeng

import chirprea.core.memmap.MemoryMap
import chirprea.core.model.Memory
import chirprea.core.radio.ImageFile
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Writes a sample UV-21ProGPS image to build/samples for manual UI checks. */
class SampleImageTest {
    @Test
    fun writeSampleImage() {
        val map = MemoryMap(BaofengUV21ProGPS().memSize, 0xFF)
        for (a in 0x8000 until 0x8080) map[a] = 0 // VFOs + settings, like a reset radio
        val r = BaofengUV21ProGPS().apply { loadMmap(map) }
        val hi = r.powerLevels[0]
        r.setMemory(Memory(number = 1, name = "SIMPLEX 2M", freq = 146_520_000, power = hi))
        r.setMemory(Memory(number = 2, name = "RPT CENTRO", freq = 147_000_000, duplex = "+", offset = 600_000, tmode = "Tone", rtone = 100.0, power = hi))
        r.setMemory(Memory(number = 3, name = "UHF DCS", freq = 439_500_000, duplex = "-", offset = 5_000_000, tmode = "DTCS", dtcs = 754, mode = "NFM", power = r.powerLevels[1]))
        r.setMemory(Memory(number = 101, name = "TORRE", freq = 118_100_000, mode = "AM", power = hi))
        r.bankModel().let { m ->
            m.rename(m.banks[0], "RADIOAMADOR")
            m.rename(m.banks[1], "AVIACAO")
        }
        val out = File("build/samples/uv21progps-sample.img")
        out.parentFile.mkdirs()
        out.writeBytes(r.toImage())
        assertEquals("UV21ProGPS", ImageFile.rclass(out.readBytes()))
    }
}
