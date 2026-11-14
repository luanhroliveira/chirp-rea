package chirprea.core.model

/** Universal value lists, ported from `chirp/chirp_common.py`. */
object Chirp {
    /** The 50 standard CTCSS tones. */
    val TONES: List<Double> = listOf(
        67.0, 69.3, 71.9, 74.4, 77.0, 79.7, 82.5,
        85.4, 88.5, 91.5, 94.8, 97.4, 100.0, 103.5,
        107.2, 110.9, 114.8, 118.8, 123.0, 127.3,
        131.8, 136.5, 141.3, 146.2, 151.4, 156.7,
        159.8, 162.2, 165.5, 167.9, 171.3, 173.8,
        177.3, 179.9, 183.5, 186.2, 189.9, 192.8,
        196.6, 199.5, 203.5, 206.5, 210.7, 218.1,
        225.7, 229.1, 233.6, 241.8, 250.3, 254.1,
    )

    val OLD_TONES: List<Double> = (TONES.toSet() - setOf(
        159.8, 165.5, 171.3, 177.3, 183.5, 189.9, 196.6, 199.5, 206.5, 229.1, 254.1,
    )).sorted()

    fun isValidTone(v: Double) = v > 50.0 && v < 300.0

    /** The 104 standard DCS codes (octal digits written as decimal). */
    val DTCS_CODES: List<Int> = listOf(
        23, 25, 26, 31, 32, 36, 43, 47, 51, 53, 54,
        65, 71, 72, 73, 74, 114, 115, 116, 122, 125, 131,
        132, 134, 143, 145, 152, 155, 156, 162, 165, 172, 174,
        205, 212, 223, 225, 226, 243, 244, 245, 246, 251, 252,
        255, 261, 263, 265, 266, 271, 274, 306, 311, 315, 325,
        331, 332, 343, 346, 351, 356, 364, 365, 371, 411, 412,
        413, 423, 431, 432, 445, 446, 452, 454, 455, 462, 464,
        465, 466, 503, 506, 516, 523, 526, 532, 546, 565, 606,
        612, 624, 627, 631, 632, 654, 662, 664, 703, 712, 723,
        731, 732, 734, 743, 754,
    )

    /** All 512 possible DCS codes. */
    val ALL_DTCS_CODES: List<Int> = buildList {
        for (a in 0..7) for (b in 0..7) for (c in 0..7) add(a * 100 + b * 10 + c)
    }

    val CROSS_MODES = listOf(
        "Tone->Tone", "DTCS->", "->DTCS", "Tone->DTCS", "DTCS->Tone", "->Tone", "DTCS->DTCS", "Tone->",
    )

    /** Master list of modes; must stay stable to allow cross-radio import. */
    val MODES = listOf(
        "WFM", "FM", "NFM", "AM", "NAM", "DV", "USB", "LSB", "CW", "RTTY",
        "DIG", "PKT", "NCW", "NCWR", "CWR", "P25", "Auto", "RTTYR",
        "FSK", "FSKR", "DMR", "DN", "NXDN",
    )

    val TONE_MODES = listOf("", "Tone", "TSQL", "DTCS", "DTCS-R", "TSQL-R", "Cross")

    val TUNING_STEPS = listOf(5.0, 6.25, 10.0, 12.5, 15.0, 20.0, 25.0, 30.0, 50.0, 100.0, 125.0, 200.0, 9.0, 1.0, 2.5)

    val COMMON_TUNING_STEPS = listOf(5.0, 10.0, 15.0, 20.0, 25.0, 30.0, 50.0, 100.0)

    val DUPLEXES = listOf("", "+", "-", "split", "off")

    val DTCS_POLARITIES = listOf("NN", "NR", "RN", "RR")

    val SKIP_VALUES = listOf("", "S", "P")

    const val CHARSET_UPPER_NUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ 1234567890"
    const val CHARSET_ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz 1234567890"
    val CHARSET_ASCII: String = (' '..'~').joinToString("")

    /** Column order of CHIRP's generic CSV format. */
    val CSV_FORMAT = listOf(
        "Location", "Name", "Frequency", "Duplex", "Offset", "Tone",
        "rToneFreq", "cToneFreq", "DtcsCode", "DtcsPolarity", "RxDtcsCode",
        "CrossMode", "Mode", "TStep", "Skip", "Power", "Comment",
        "URCALL", "RPT1CALL", "RPT2CALL", "DVCODE",
    )
}
