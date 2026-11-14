package chirprea.core.csv

import chirprea.core.model.Chirp
import chirprea.core.model.Freq
import chirprea.core.model.Memory
import chirprea.core.model.PowerLevel
import chirprea.core.radio.Radio
import chirprea.core.radio.RadioFeatures

/**
 * "Generic CSV" radio: an in-memory list of channels that loads/saves CHIRP's
 * CSV format. Port of `chirp/drivers/generic_csv.py`.
 */
class CsvRadio(maxMemory: Int = 999, withDefault: Boolean = true) : Radio {
    override val vendor = "Generic"
    override val model = "CSV"

    private val memories = ArrayList<Memory>()
    val errors = ArrayList<String>()

    init {
        blank(maxMemory)
        if (withDefault) {
            memories[0] = memories[0].copy(empty = false, freq = 146_010_000, power = DEFAULT_POWER)
        }
    }

    private fun blank(max: Int) {
        memories.clear()
        for (i in 0..max) memories += Memory.empty(i)
        errors.clear()
    }

    private fun grow(number: Int) {
        while (memories.size <= number) memories += Memory.empty(memories.size)
    }

    override fun features() = RadioFeatures(
        memoryBounds = 0 until memories.size,
        validModes = Chirp.MODES,
        validTmodes = Chirp.TONE_MODES,
        validCrossModes = Chirp.CROSS_MODES,
        validDuplexes = Chirp.DUPLEXES,
        validTuningSteps = Chirp.TUNING_STEPS,
        validBands = listOf(1L..10_000_000_000L),
        validSkips = listOf("", "S"),
        validCharacters = CHARSET_1252,
        validNameLength = 999,
        validPowerLevels = listOf(PowerLevel.auto(0.1), DEFAULT_POWER, PowerLevel.auto(1500.0)),
        validDtcsCodes = Chirp.ALL_DTCS_CODES,
        hasRxDtcs = true,
        hasCrossMode = true,
        hasComment = true,
        canOddSplit = true,
    )

    override fun getMemory(number: Int): Memory = memories[number].copy()

    override fun setMemory(mem: Memory) {
        grow(mem.number)
        memories[mem.number] = mem.copy()
    }

    override fun memories(): List<Memory> = memories.map { it.copy() }

    fun load(text: String) {
        blank(0)
        memories.clear()
        val rows = Csv.parse(text)
        var header: List<String>? = null
        var lineno = 0
        var fileHasRtone = false
        var fileHasCtone = false
        for (line in rows) {
            if (line.isNotEmpty() && line[0].startsWith("#")) continue
            lineno++
            if (header == null) {
                header = line
                fileHasRtone = "rToneFreq" in line
                fileHasCtone = "cToneFreq" in line
                line.filter { it !in Chirp.CSV_FORMAT }.forEach { errors += "Header line has unknown field '$it'" }
                continue
            }
            if (line.size < header.size) {
                errors += "Column number mismatch on line $lineno"
                continue
            }
            try {
                val mem = parseLine(header, line)
                if (mem.freq == 0L) continue
                if (fileHasRtone && !fileHasCtone) mem.ctone = mem.rtone
                else if (fileHasCtone && !fileHasRtone) mem.rtone = mem.ctone
                grow(mem.number)
                memories[mem.number] = mem
            } catch (e: Exception) {
                errors += "Line $lineno: ${e.message}"
            }
        }
        if (memories.isEmpty()) memories += Memory.empty(0)
    }

    private fun parseLine(header: List<String>, line: List<String>): Memory {
        val mem = Memory()
        for ((i, col) in header.withIndex()) {
            val v = line.getOrNull(i) ?: continue
            try {
                when (col) {
                    "Location" -> mem.number = v.toInt()
                    "Name" -> mem.name = v
                    "Frequency" -> mem.freq = Freq.parse(v)
                    "Duplex" -> mem.duplex = v
                    "Offset" -> mem.offset = Freq.parse(v)
                    "Tone" -> mem.tmode = v
                    "rToneFreq" -> mem.rtone = v.toDouble()
                    "cToneFreq" -> mem.ctone = v.toDouble()
                    "DtcsCode" -> if (v.isNotEmpty()) mem.dtcs = v.toInt()
                    "DtcsPolarity" -> mem.dtcsPolarity = v
                    "RxDtcsCode" -> if (v.isNotEmpty()) mem.rxDtcs = v.toInt()
                    "CrossMode" -> mem.crossMode = v
                    "Mode" -> mem.mode = v
                    "TStep" -> mem.tuningStep = v.toDouble()
                    "Skip" -> mem.skip = v
                    "Power" -> if (v.isNotEmpty()) mem.power = PowerLevel.parse(v)
                    "Comment" -> mem.comment = v
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("[$col] ${e.message}", e)
            }
        }
        val problems = mem.validateValues()
        if (problems.isNotEmpty()) throw IllegalArgumentException(problems.first())
        if (mem.power == null) mem.power = DEFAULT_POWER
        return mem
    }

    fun save(): String {
        val rows = ArrayList<List<String>>()
        rows += Chirp.CSV_FORMAT
        memories.filter { !it.empty }.forEach { rows += it.toCsv() }
        return Csv.format(rows)
    }

    companion object {
        val DEFAULT_POWER = PowerLevel.auto(50.0)

        /** Printable Windows-1252 characters, like CHIRP's CHARSET_1252. */
        val CHARSET_1252: String = buildString {
            val cp1252High = "€\u0081‚ƒ„…†‡ˆ‰Š‹Œ\u008DŽ\u008F\u0090‘’“”•–—˜™š›œ\u009DžŸ"
            for (b in 0x20..0xFF) {
                if (b in listOf(0x7F, 0x81, 0x8D, 0x8F, 0x90, 0x9D, 0xA0, 0xAD)) continue
                append(if (b in 0x80..0x9F) cp1252High[b - 0x80] else b.toChar())
            }
        }

        fun fromText(text: String) = CsvRadio(0, withDefault = false).apply { load(text) }
    }
}
