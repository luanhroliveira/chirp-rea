package chirprea.core.drivers

import chirprea.core.drivers.baofeng.BaofengUV17Pro
import chirprea.core.drivers.baofeng.BaofengUV17ProGPS
import chirprea.core.drivers.baofeng.BaofengUV21ProGPS
import chirprea.core.drivers.baofeng.BaofengUV21ProV2
import chirprea.core.radio.Directory

/** Registers every ported driver in the [Directory]. */
object Drivers {
    private var done = false

    fun registerAll() {
        if (done) return
        listOf(
            BaofengUV17Pro.INFO,
            BaofengUV21ProV2.INFO,
            BaofengUV17ProGPS.INFO,
            BaofengUV21ProGPS.INFO,
        ).forEach(Directory::register)

        // The original UV-21 Pro has no model of its own in CHIRP. A unit probed on
        // 2026-11-14 answered PROGRAMCOLORPROU and reported model "UV21PRO", which
        // is the UV21ProGPS protocol. Older units reportedly work with UV-17Pro
        // (https://chirpmyradio.com/issues/10581). It is NOT the UV-21 Pro V2.
        Directory.registerAlias("Baofeng UV-21 Pro", "UV21ProGPS")
        Directory.registerAlias("Baofeng UV-21R", "UV17Pro")
        done = true
    }
}
