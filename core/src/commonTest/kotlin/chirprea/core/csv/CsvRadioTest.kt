package chirprea.core.csv

import chirprea.core.model.Freq
import chirprea.core.model.PowerLevel
import chirprea.core.radio.ImageFile
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvRadioTest {
    private val sample = """
        Location,Name,Frequency,Duplex,Offset,Tone,rToneFreq,cToneFreq,DtcsCode,DtcsPolarity,RxDtcsCode,CrossMode,Mode,TStep,Skip,Power,Comment,URCALL,RPT1CALL,RPT2CALL,DVCODE
        0,"Call, 2m",146.520000,,0.000000,,88.5,88.5,023,NN,023,Tone->Tone,FM,5.00,,5.0W,Simplex calling,,,,
        3,RPT1,147.000000,+,0.600000,Tone,100.0,100.0,023,NN,023,Tone->Tone,FM,5.00,,50W,,,,,
    """.trimIndent()

    @Test
    fun loadsChirpCsv() {
        val r = CsvRadio.fromText(sample)
        assertTrue(r.errors.isEmpty(), r.errors.toString())
        val m0 = r.getMemory(0)
        assertEquals("Call, 2m", m0.name)
        assertEquals(146_520_000L, m0.freq)
        assertEquals(PowerLevel.auto(5.0), m0.power)
        val m3 = r.getMemory(3)
        assertEquals("+", m3.duplex)
        assertEquals(147_600_000L, m3.txFreq)
        assertTrue(r.getMemory(1).empty)
    }

    @Test
    fun roundTripsExactly() {
        val r = CsvRadio.fromText(sample)
        val out = r.save()
        assertEquals(sample.lines(), out.trimEnd().lines())
    }

    @Test
    fun onlyRToneColumnFillsCtone() {
        val r = CsvRadio.fromText("Location,Frequency,Tone,rToneFreq\n1,146.52,TSQL,123.0\n")
        assertEquals(123.0, r.getMemory(1).ctone)
    }

    @Test
    fun freqParsingMatchesChirp() {
        assertEquals(146_520_000L, Freq.parse("146.52"))
        assertEquals(500_000L, Freq.parse("500 kHz"))
        assertEquals(446_006_250L, Freq.parse("446.00625 MHz"))
        assertEquals("146.520000", Freq.format(146_520_000))
    }

    @Test
    fun imageFileRoundTrip() {
        val data = ByteArray(16) { it.toByte() }
        val img = ImageFile.encode(data, mapOf("rclass" to JsonPrimitive("BaofengUV5R")))
        val (back, meta) = ImageFile.decode(img)
        assertContentEquals(data, back)
        assertEquals("BaofengUV5R", ImageFile.rclass(img))
        assertEquals(1, meta.size)
    }
}
