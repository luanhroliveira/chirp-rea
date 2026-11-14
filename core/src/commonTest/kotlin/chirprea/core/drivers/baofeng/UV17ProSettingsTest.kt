package chirprea.core.drivers.baofeng

import chirprea.core.memmap.MemoryMap
import chirprea.core.radio.BoolSetting
import chirprea.core.radio.ChoiceSetting
import chirprea.core.radio.Setting
import chirprea.core.radio.TextSetting
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UV17ProSettingsTest {
    private fun radio() = BaofengUV21ProGPS().apply {
        // A freshly reset radio: zeros in the settings area, 0xFF elsewhere.
        val map = MemoryMap(memSize, 0xFF)
        for (a in 0x8000 until 0x8080) map[a] = 0
        loadMmap(map)
    }

    private inline fun <reified T : Setting> BaofengUV21ProGPS.find(id: String): T =
        settings().flatMap { it.all() }.single { it.id == id } as T

    @Test
    fun gpsFamilyExposesChirpGroupsAndFlags() {
        val r = radio()
        val groups = r.settings()
        assertEquals(listOf("basic", "workmode", "dtmfe", "bank"), groups.map { it.id })
        val ids = groups.flatMap { it.all() }.map { it.id }.toSet()
        // UV-17ProGPS-only settings (CHIRP flags _has_gps, _has_voxsw, _has_pilot_tone, ...)
        assertTrue("settings.gpsw" in ids && "settings.voxsw" in ids && "settings.tone" in ids && "settings.key2short" in ids)
        assertFalse("ani.aniid" in ids) // _has_when_to_send_aniid = False
        assertEquals(10, groups.single { it.id == "bank" }.items.size)
        assertEquals(20, ids.count { it.matches(Regex("""pttid/\d+\.code""")) })
    }

    @Test
    fun choicesAndFlagsWriteTheSettingsStruct() {
        val r = radio()
        val squelch = r.find<ChoiceSetting>("settings.squelch")
        assertEquals("Off", squelch.current)
        squelch.set(3)
        // settings_obj starts right after the two 32-byte VFO entries at 0x8000.
        assertEquals(3, r.mmap[0x8040])
        assertEquals("3", r.find<ChoiceSetting>("settings.squelch").current)

        r.find<BoolSetting>("settings.roger").set(true)
        assertTrue(r.find<BoolSetting>("settings.roger").value)

        val key = r.find<ChoiceSetting>("settings.key2short")
        key.set(key.options.indexOf("Vox"))
        assertEquals(0x2D, r.memoryObject["settings"]["key2short"].int)
    }

    @Test
    fun vfoFrequencyAndOffsetUseOneDigitPerByte() {
        val r = radio()
        r.find<TextSetting>("vfo.a.freq").set("146.520")
        assertContentEquals(byteArrayOf(1, 4, 6, 5, 2, 0, 0, 0), r.mmap.getBytes(0x8000, 8))
        assertEquals("146.520000", r.find<TextSetting>("vfo.a.freq").value)

        r.find<TextSetting>("vfo.b.offset").set("0.600")
        assertEquals("0.600000", r.find<TextSetting>("vfo.b.offset").value)

        assertFailsWith<IllegalArgumentException> { r.find<TextSetting>("vfo.a.freq").set("300.000") }
    }

    @Test
    fun dtmfCodesAndNames() {
        val r = radio()
        r.find<TextSetting>("pttid/0.code").set("12A*")
        val code = r.memoryObject["pttid"][0]["code"]
        assertEquals(listOf(1, 2, 10, 14, 0xFF), (0 until 5).map { code[it].int })
        assertEquals("12A*", r.find<TextSetting>("pttid/0.code").value)
        assertFailsWith<IllegalArgumentException> { r.find<TextSetting>("pttid/0.code").set("12X") }

        r.find<TextSetting>("pttid/1.name").set("BASE")
        assertEquals("BASE", r.find<TextSetting>("pttid/1.name").value)

        r.find<TextSetting>("bank_name/4.name").set("MARITIMO")
        assertEquals("MARITIMO", r.bankModel().banks[4].name)
    }
}
