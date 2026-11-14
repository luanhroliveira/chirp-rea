package chirprea.core.model

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/** Frequencies are integral Hz, as in CHIRP. */
object Freq {
    /** Parse "146.520", "146.52 MHz", "500 kHz" or "" (0) into Hz. */
    fun parse(text: String): Long {
        val s = text.trim()
        if (s.isEmpty()) return 0
        if (s.endsWith(" MHz")) return parse(s.substringBefore(" "))
        if (s.endsWith(" kHz")) return s.substringBefore(" ").toLong() * 1000
        return if ('.' in s) {
            val mhzPart = s.substringBefore('.').ifEmpty { "0" }
            val khzPart = s.substringAfter('.').padEnd(6, '0')
            require(khzPart.length <= 6 && khzPart.all { it.isDigit() }) { "Invalid kHz value: $khzPart" }
            mhzPart.toLong() * 1_000_000 + khzPart.toLong()
        } else {
            s.toLong() * 1_000_000
        }
    }

    /** Format Hz as "146.520000". */
    fun format(hz: Long): String {
        val sign = if (hz < 0) "-" else ""
        val a = abs(hz)
        return sign + (a / 1_000_000).toString() + "." + (a % 1_000_000).toString().padStart(6, '0')
    }
}

/** Minimal multiplatform number formatting (no String.format in common code). */
object Fmt {
    fun fixed(value: Double, decimals: Int): String {
        val factor = 10.0.pow(decimals)
        val scaled = (abs(value) * factor).roundToLong()
        val sign = if (value < 0 && scaled != 0L) "-" else ""
        if (decimals == 0) return sign + scaled.toString()
        val whole = scaled / factor.toLong()
        val frac = (scaled % factor.toLong()).toString().padStart(decimals, '0')
        return "$sign$whole.$frac"
    }

    fun zeroPad(value: Int, width: Int) = value.toString().padStart(width, '0')
}

/** A transmit power level supported by a radio. Compared by dBm. */
open class PowerLevel(val label: String, val dBm: Double) : Comparable<PowerLevel> {
    val watts: Double get() = dBmToWatts(dBm)

    override fun compareTo(other: PowerLevel) = dBm.compareTo(other.dBm)
    override fun equals(other: Any?) = other is PowerLevel && other.dBm == dBm
    override fun hashCode() = dBm.hashCode()
    override fun toString() = label

    companion object {
        fun watts(label: String, watts: Double) = PowerLevel(label, wattsToDBm(watts))

        /** "5.0W", "50W" — named from the wattage, like CHIRP's AutoNamedPowerLevel. */
        fun auto(watts: Double) = PowerLevel(
            if (watts >= 10) "${watts.toLong()}W" else "${Fmt.fixed(watts, 1)}W",
            wattsToDBm(watts),
        )

        fun wattsToDBm(watts: Double) = 10 * log10(watts) + 30
        fun dBmToWatts(dBm: Double) = (10.0.pow(dBm / 10) / 1000 * 10).roundToLong() / 10.0

        private val POWER_RE = Regex("""^\s*([0-9.]+)\s*([Ww]?)\s*$""")

        /** Parse "5", "5W", "0.5 W" into an auto-named level. */
        fun parse(text: String): PowerLevel {
            val m = POWER_RE.matchEntire(text) ?: throw IllegalArgumentException("Invalid power specification: '$text'")
            return auto(m.groupValues[1].toDouble())
        }
    }
}
