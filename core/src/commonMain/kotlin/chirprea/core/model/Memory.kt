package chirprea.core.model

/**
 * One radio memory (channel), the Kotlin counterpart of `chirp_common.Memory`.
 * Defaults match CHIRP so that round-trips through CSV are identical.
 */
data class Memory(
    var number: Int = 0,
    var empty: Boolean = false,
    var name: String = "",
    /** Receive frequency in Hz. */
    var freq: Long = 0,
    var extdNumber: String = "",
    var vfo: Int = 0,
    var rtone: Double = 88.5,
    var ctone: Double = 88.5,
    var dtcs: Int = 23,
    var rxDtcs: Int = 23,
    /** "", "Tone", "TSQL", "DTCS", "DTCS-R", "TSQL-R", "Cross" */
    var tmode: String = "",
    var crossMode: String = "Tone->Tone",
    var dtcsPolarity: String = "NN",
    /** "", "S" (skip), "P" (priority) */
    var skip: String = "",
    var power: PowerLevel? = null,
    /** "", "+", "-", "split", "off" */
    var duplex: String = "",
    /** Offset in Hz, or the TX frequency when [duplex] is "split". */
    var offset: Long = 600_000,
    var mode: String = "FM",
    var tuningStep: Double = 5.0,
    var comment: String = "",
    /** Fields the radio does not allow to change for this memory. */
    var immutable: List<String> = emptyList(),
) {
    /** Field-level validation against CHIRP's universal value lists. */
    fun validateValues(): List<String> = buildList {
        if (!Chirp.isValidTone(rtone)) add("'$rtone' is not a valid value for rtone")
        if (!Chirp.isValidTone(ctone)) add("'$ctone' is not a valid value for ctone")
        if (dtcs !in Chirp.ALL_DTCS_CODES) add("'$dtcs' is not a valid DTCS code")
        if (rxDtcs !in Chirp.ALL_DTCS_CODES) add("'$rxDtcs' is not a valid RX DTCS code")
        if (tmode !in Chirp.TONE_MODES) add("'$tmode' is not a valid tone mode")
        if (dtcsPolarity !in Chirp.DTCS_POLARITIES) add("'$dtcsPolarity' is not a valid DTCS polarity")
        if (crossMode !in Chirp.CROSS_MODES) add("'$crossMode' is not a valid cross mode")
        if (mode !in Chirp.MODES) add("'$mode' is not a valid mode")
        if (duplex !in Chirp.DUPLEXES) add("'$duplex' is not a valid duplex")
        if (skip !in Chirp.SKIP_VALUES) add("'$skip' is not a valid skip value")
    }

    /** TX frequency implied by duplex/offset. */
    val txFreq: Long
        get() = when (duplex) {
            "+" -> freq + offset
            "-" -> freq - offset
            "split" -> offset
            else -> freq
        }

    fun toCsv(): List<String> = listOf(
        number.toString(),
        name,
        Freq.format(freq),
        duplex,
        Freq.format(offset),
        tmode,
        Fmt.fixed(rtone, 1),
        Fmt.fixed(ctone, 1),
        Fmt.zeroPad(dtcs, 3),
        dtcsPolarity,
        Fmt.zeroPad(rxDtcs, 3),
        crossMode,
        mode,
        Fmt.fixed(tuningStep, 2),
        skip,
        power?.toString() ?: "",
        comment,
        "", "", "", "",
    )

    companion object {
        fun empty(number: Int) = Memory(number = number, empty = true)
    }
}
