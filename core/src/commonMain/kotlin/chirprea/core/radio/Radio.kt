package chirprea.core.radio

import chirprea.core.model.Chirp
import chirprea.core.model.Memory
import chirprea.core.model.PowerLevel

class RadioException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** What a radio supports; drives validation and which grid columns are shown. */
data class RadioFeatures(
    val memoryBounds: IntRange = 0..1,
    val validModes: List<String> = Chirp.MODES,
    val validTmodes: List<String> = listOf("", "Tone", "TSQL", "DTCS"),
    val validCrossModes: List<String> = Chirp.CROSS_MODES,
    val validDuplexes: List<String> = listOf("", "+", "-"),
    val validTuningSteps: List<Double> = Chirp.COMMON_TUNING_STEPS,
    val validBands: List<LongRange> = listOf(0L..10_000_000_000L),
    val validSkips: List<String> = listOf("", "S"),
    val validPowerLevels: List<PowerLevel> = emptyList(),
    val validDtcsCodes: List<Int> = Chirp.DTCS_CODES,
    val validTones: List<Double> = Chirp.TONES,
    val validNameLength: Int = 6,
    val validCharacters: String = Chirp.CHARSET_UPPER_NUMERIC,
    val hasName: Boolean = true,
    val hasCtone: Boolean = true,
    val hasDtcs: Boolean = true,
    val hasRxDtcs: Boolean = false,
    val hasDtcsPolarity: Boolean = true,
    val hasCrossMode: Boolean = false,
    val hasOffset: Boolean = true,
    val hasTuningStep: Boolean = true,
    val hasComment: Boolean = false,
    val hasBank: Boolean = false,
    val hasSettings: Boolean = false,
    val canOddSplit: Boolean = false,
) {
    /** Port of the core checks in `RadioFeatures.validate_memory`. */
    fun validate(mem: Memory): List<String> {
        if (mem.empty) return emptyList()
        val msgs = mem.validateValues().toMutableList()
        if (mem.number !in memoryBounds) msgs += "Location ${mem.number} is out of range"
        if (validBands.none { mem.freq in it }) msgs += "Frequency ${mem.freq} is out of supported range"
        if (mem.duplex in listOf("+", "-") && validBands.none { mem.txFreq in it }) {
            msgs += "TX frequency ${mem.txFreq} is out of supported range"
        }
        if (mem.mode !in validModes) msgs += "Mode ${mem.mode} not supported"
        if (mem.tmode !in validTmodes) msgs += "Tone mode ${mem.tmode} not supported"
        if (mem.duplex !in validDuplexes) msgs += "Duplex ${mem.duplex} not supported"
        if (mem.duplex == "split" && !canOddSplit) msgs += "Odd split not supported"
        if (mem.skip !in validSkips) msgs += "Skip ${mem.skip} not supported"
        if (hasName) {
            if (mem.name.length > validNameLength) msgs += "Name '${mem.name}' is longer than $validNameLength"
            val bad = mem.name.filter { it !in validCharacters }
            if (bad.isNotEmpty()) msgs += "Name contains invalid characters: '$bad'"
        }
        if (mem.tmode in listOf("DTCS", "DTCS-R") && mem.dtcs !in validDtcsCodes) msgs += "DTCS ${mem.dtcs} not supported"
        if (validPowerLevels.isNotEmpty() && mem.power != null && mem.power !in validPowerLevels) {
            msgs += "Power level ${mem.power} not supported"
        }
        return msgs
    }
}

/** Progress reported during clone operations. */
data class CloneStatus(val message: String, val current: Int, val max: Int) {
    val fraction: Float get() = if (max == 0) 0f else current.toFloat() / max
}

/** Base contract of every radio driver. */
interface Radio {
    val vendor: String
    val model: String
    val variant: String get() = ""

    fun features(): RadioFeatures
    fun getMemory(number: Int): Memory
    fun setMemory(mem: Memory)
    fun eraseMemory(number: Int) = setMemory(Memory.empty(number))

    fun memories(): List<Memory> = features().memoryBounds.map(::getMemory)

    /** Bank layout, or null if the radio has no banks. */
    fun bankModel(): BankModel? = null

    /** Radio-wide settings ("Configurações" tab); empty if the driver has none. */
    fun settings(): List<SettingGroup> = emptyList()

    /** Facts about the radio/driver/image for the "Informações" tab. */
    fun info(): List<InfoItem> {
        val f = features()
        return listOf(
            InfoItem("Fabricante", vendor),
            InfoItem("Modelo", model),
            InfoItem("Canais", "${f.memoryBounds.first} a ${f.memoryBounds.last}"),
            InfoItem("Modos", f.validModes.joinToString(", ")),
        )
    }
}

/** Metadata describing a driver so the UI can list it before instantiating. */
data class DriverInfo(
    val vendor: String,
    val model: String,
    val variant: String = "",
    /** CHIRP class name, written to `.img` metadata for compatibility. */
    val chirpClass: String,
    val baudRate: Int = 9600,
    val factory: () -> Radio,
) {
    val displayName get() = listOf(vendor, model, variant).filter { it.isNotEmpty() }.joinToString(" ")
}

/**
 * An entry of the model picker: either a driver itself or another model name
 * that is programmed with that driver (like CHIRP's `model_alias_map.yaml`).
 */
data class ModelChoice(val label: String, val driver: DriverInfo, val isAlias: Boolean = false)

/** Registry of drivers, like `chirp/directory.py`. */
object Directory {
    private val drivers = mutableListOf<DriverInfo>()
    private val aliases = mutableListOf<Pair<String, String>>()

    fun register(info: DriverInfo) {
        drivers.removeAll { it.chirpClass == info.chirpClass }
        drivers += info
    }

    /** Declare that the model [label] is programmed with the driver [chirpClass]. */
    fun registerAlias(label: String, chirpClass: String) {
        aliases.removeAll { it.first == label }
        aliases += label to chirpClass
    }

    val all: List<DriverInfo> get() = drivers.sortedBy { it.displayName.lowercase() }

    /** Drivers plus aliases, sorted by label, for the model picker. */
    val choices: List<ModelChoice>
        get() = (
            drivers.map { ModelChoice(it.displayName, it) } +
                aliases.mapNotNull { (label, cls) ->
                    byChirpClass(cls)?.let { ModelChoice("$label (driver ${it.model})", it, isAlias = true) }
                }
            ).sortedBy { it.label.lowercase() }

    fun byChirpClass(name: String): DriverInfo? = drivers.firstOrNull { it.chirpClass == name }
}
