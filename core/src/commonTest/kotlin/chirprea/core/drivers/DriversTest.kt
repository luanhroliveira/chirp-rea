package chirprea.core.drivers

import chirprea.core.radio.Directory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DriversTest {
    @Test
    fun uv21ProIsAnAliasOfUv21ProGpsNotV2() {
        Drivers.registerAll()
        val choice = Directory.choices.single { it.label.startsWith("Baofeng UV-21 Pro (") }
        assertTrue(choice.isAlias)
        assertEquals("UV21ProGPS", choice.driver.chirpClass)
        // The V2 is still offered as its own driver.
        assertTrue(Directory.choices.any { it.driver.chirpClass == "UV21ProV2" && !it.isAlias })
    }
}
