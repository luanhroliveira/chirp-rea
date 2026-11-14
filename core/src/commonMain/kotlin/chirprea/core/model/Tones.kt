package chirprea.core.model

/** One side (TX or RX) of a tone setting: mode is "", "Tone" or "DTCS". */
data class ToneSpec(val mode: String = "", val value: Number? = null, val polarity: Char? = null) {
    companion object {
        val NONE = ToneSpec()
    }
}

/** Ports of `chirp_common.split_tone_decode/encode` and band helpers. */
object ToneLogic {
    /** Set tmode/rtone/ctone/dtcs/... on [mem] from separate TX and RX tone specs. */
    fun splitToneDecode(mem: Memory, tx: ToneSpec, rx: ToneSpec) {
        mem.dtcsPolarity = "${tx.polarity ?: 'N'}${rx.polarity ?: 'N'}"
        val txMode = tx.mode
        val rxMode = rx.mode
        when {
            txMode.isEmpty() && rxMode.isEmpty() -> return
            txMode == "Tone" && rxMode.isEmpty() -> {
                mem.tmode = "Tone"; mem.rtone = tx.value!!.toDouble(); return
            }
            txMode == "Tone" && rxMode == "Tone" && tx.value == rx.value -> {
                mem.tmode = "TSQL"; mem.ctone = tx.value!!.toDouble(); return
            }
            txMode == "DTCS" && rxMode == "DTCS" && tx.value == rx.value -> {
                mem.tmode = "DTCS"; mem.dtcs = tx.value!!.toInt(); return
            }
        }
        mem.tmode = "Cross"
        mem.crossMode = "$txMode->$rxMode"
        when (txMode) {
            "Tone" -> mem.rtone = tx.value!!.toDouble()
            "DTCS" -> mem.dtcs = tx.value!!.toInt()
        }
        when (rxMode) {
            "Tone" -> mem.ctone = rx.value!!.toDouble()
            "DTCS" -> mem.rxDtcs = rx.value!!.toInt()
        }
    }

    /** Inverse of [splitToneDecode]: returns the TX and RX tone specs of [mem]. */
    fun splitToneEncode(mem: Memory): Pair<ToneSpec, ToneSpec> {
        var txMode = ""; var rxMode = ""
        var txVal: Number? = null; var rxVal: Number? = null
        when (mem.tmode) {
            "Tone" -> { txMode = "Tone"; txVal = mem.rtone }
            "TSQL" -> { txMode = "Tone"; rxMode = "Tone"; txVal = mem.ctone; rxVal = mem.ctone }
            "DTCS" -> { txMode = "DTCS"; rxMode = "DTCS"; txVal = mem.dtcs; rxVal = mem.dtcs }
            "Cross" -> {
                txMode = mem.crossMode.substringBefore("->")
                rxMode = mem.crossMode.substringAfter("->")
                txVal = when (txMode) { "Tone" -> mem.rtone; "DTCS" -> mem.dtcs; else -> null }
                rxVal = when (rxMode) { "Tone" -> mem.ctone; "DTCS" -> mem.rxDtcs; else -> null }
            }
        }
        val txPol = if (txMode == "DTCS") mem.dtcsPolarity[0] else null
        val rxPol = if (rxMode == "DTCS") mem.dtcsPolarity[1] else null
        return ToneSpec(txMode, txVal, txPol) to ToneSpec(rxMode, rxVal, rxPol)
    }

    fun inRange(freq: Long, ranges: List<LongRange>) = ranges.any { freq in it }

    /** True when the two frequencies are not inside the same band. */
    fun isSplit(bands: List<LongRange>, f1: Long, f2: Long) = bands.none { f1 in it && f2 in it }
}
