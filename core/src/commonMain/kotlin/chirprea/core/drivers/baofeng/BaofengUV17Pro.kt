package chirprea.core.drivers.baofeng

import chirprea.core.bitwise.DataElement
import chirprea.core.memmap.MemoryMap
import chirprea.core.model.Chirp
import chirprea.core.model.Memory
import chirprea.core.model.PowerLevel
import chirprea.core.model.ToneLogic
import chirprea.core.model.ToneSpec
import chirprea.core.radio.Bank
import chirprea.core.radio.BankModel
import chirprea.core.radio.CloneModeRadio
import chirprea.core.radio.StaticBankModel
import chirprea.core.radio.CloneStatus
import chirprea.core.radio.DriverInfo
import chirprea.core.radio.RadioException
import chirprea.core.radio.RadioFeatures
import chirprea.core.radio.SerialPort
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Baofeng UV-17Pro family. Port of `chirp/drivers/baofeng_uv17Pro.py`
 * (class `UV17Pro`) from CHIRP, GPL-3.0.
 *
 * Memory layout: 1000 channels of 32 bytes each from 0x0000, followed by VFO,
 * settings, ANI and PTT-ID blocks. The radio is cloned over serial at 115200
 * baud in 0x40-byte blocks, with a simple XOR cipher on the payload.
 */
open class BaofengUV17Pro : CloneModeRadio() {
    override val info: DriverInfo get() = INFO

    protected open val memStarts = listOf(0x0000, 0x9000, 0xA000, 0xD000)
    protected open val memSizes = listOf(0x8040, 0x0040, 0x02C0, 0x0040)
    override val memSize: Int get() = memSizes.sum()

    protected open val idents: List<ByteArray> = listOf("PROGRAMBFNORMALU".encodeToByteArray())
    protected open val fingerprint: ByteArray = byteArrayOf(0x06)
    protected open val magics: List<Pair<ByteArray, Int>> = listOf(
        byteArrayOf(0x46) to 16,
        byteArrayOf(0x4D) to 15,
        byteArrayOf(
            0x53, 0x45, 0x4E, 0x44, 0x21, 0x05, 0x0D, 0x01, 0x01, 0x01, 0x04, 0x11, 0x08, 0x05,
            0x0D, 0x0D, 0x01, 0x11, 0x0F, 0x09, 0x12, 0x09, 0x10, 0x04, 0x00,
        ) to 1,
    )
    protected open val usesEncryption = true
    protected open val encryptionSymbol = 1
    protected open val scodeOffset = 0

    open val channels = 1000
    open val modes = listOf("NFM", "FM")
    open val validChars = Chirp.CHARSET_ALPHANUMERIC + "!@#$%^&*()+-=[]:\";'<>?,./"
    open val nameLength = 12
    open val powerLevels = listOf(PowerLevel.watts("High", 5.0), PowerLevel.watts("Low", 1.0))
    open val dtcsCodes: List<Int> = (Chirp.DTCS_CODES + 645).sorted()
    open val steps = listOf(2.5, 5.0, 6.25, 10.0, 12.5, 20.0, 25.0, 50.0)
    open val airbands: List<LongRange> = listOf(108_000_000L..135_999_999L)
    open val validBands: List<LongRange> = listOf(
        136_000_000L..174_000_000L,
        200_000_000L..260_000_000L,
        400_000_000L..520_000_000L,
    )

    // Feature flags that decide which settings exist (CHIRP's _has_* attributes).
    open val hasWhenToSendAniId = true
    open val hasPilotTone = false
    open val hasSendIdDelay = false
    open val hasSkey2Short = false
    open val hasVoxSwitch = false
    open val hasGps = false
    open val hasVoice = true
    open val hasBankNames = false
    open val scodeList: List<String> = (1..20).map(Int::toString)
    open val squelchList: List<String> = listOf("Off") + (1..5).map(Int::toString)

    override fun settings() = UV17ProSettings(this, memobj).build()

    protected open val memParams = mapOf("mems" to 1000, "ani" to 0x8080, "pttid" to 0x80A0, "names" to 0x8280)

    override val memFormat: String
        get() = MEM_FORMAT
            .replace("%(mems)i", memParams.getValue("mems").toString())
            .replace("%(ani)X", hex(memParams.getValue("ani")))
            .replace("%(pttid)04X", hex(memParams.getValue("pttid")))
            .replace("%(names)04X", hex(memParams.getValue("names")))

    override fun features() = RadioFeatures(
        memoryBounds = 1..channels,
        validModes = modes,
        validTmodes = listOf("", "Tone", "TSQL", "DTCS", "Cross"),
        validCrossModes = listOf("Tone->Tone", "DTCS->", "->DTCS", "Tone->DTCS", "DTCS->Tone", "->Tone", "DTCS->DTCS"),
        validDuplexes = listOf("", "-", "+", "split", "off"),
        validTuningSteps = steps,
        validBands = validBands,
        validSkips = listOf("", "S"),
        validPowerLevels = powerLevels,
        validDtcsCodes = dtcsCodes,
        validNameLength = nameLength,
        validCharacters = validChars,
        hasRxDtcs = true,
        hasCrossMode = true,
        hasTuningStep = false,
        hasSettings = true,
        canOddSplit = true,
    )

    // ---------------------------------------------------------------- memories

    private fun raw(number: Int): DataElement = memobj["memory"][number - 1]

    override fun getMemory(number: Int): Memory {
        val m = raw(number)
        val mem = Memory(number = number)
        if (m.getRaw()[0] == 0xFF.toByte()) return mem.copy(empty = true)

        mem.freq = m["rxfreq"].value * 10
        splitTxFreq(m, mem)
        ToneLogic.splitToneDecode(mem, decodeTone(m["txtone"].int), decodeTone(m["rxtone"].int))
        if (m["scan"].int == 0) mem.skip = "S"
        mem.power = powerLevels.getOrElse(m["lowpower"].int) { powerLevels[0] }
        mem.mode = if (m["wide"].int != 0) modes[0] else modes[1]
        if (ToneLogic.inRange(mem.freq, airbands)) mem.mode = "AM"
        mem.name = m["name"].string.replace('ÿ', ' ').replace('\u0000', ' ').trimEnd()
        return mem
    }

    private fun isTxInhibited(m: DataElement): Boolean {
        val tx = m["txfreq"].getRaw()
        return tx.all { it == 0xFF.toByte() } || tx.all { it == 0.toByte() }
    }

    private fun splitTxFreq(m: DataElement, mem: Memory) {
        if (isTxInhibited(m)) {
            mem.duplex = "off"; mem.offset = 0
            return
        }
        val tx = m["txfreq"].value * 10
        val offset = tx - mem.freq
        when {
            offset == 0L -> { mem.duplex = ""; mem.offset = 0 }
            ToneLogic.isSplit(features().validBands, mem.freq, tx) -> { mem.duplex = "split"; mem.offset = tx }
            offset < 0 -> { mem.duplex = "-"; mem.offset = abs(offset) }
            else -> { mem.duplex = "+"; mem.offset = offset }
        }
    }

    override fun setMemory(mem: Memory) {
        val m = raw(mem.number)
        val wasEmpty = m.getRaw()[0] == 0xFF.toByte()
        // CHIRP round-trips these per-channel extras; keep them unless the slot was empty.
        val extras = if (wasEmpty) null else Triple(m["bcl"].int, m["pttid"].int, m["scode"].int)

        m.setRaw(ByteArray(16) { 0 } + ByteArray(16) { 0xFF.toByte() })
        if (mem.empty) {
            m.fillRaw(0xFF)
            return
        }

        m["name"].string = mem.name.padEnd(nameLength, 'ÿ')
        m["rxfreq"].value = mem.freq / 10
        m["txfreq"].let { tx ->
            when (mem.duplex) {
                "off" -> tx.fillRaw(0xFF)
                "split" -> tx.value = mem.offset / 10
                "+" -> tx.value = (mem.freq + mem.offset) / 10
                "-" -> tx.value = (mem.freq - mem.offset) / 10
                else -> tx.value = mem.freq / 10
            }
        }

        val (tx, rx) = ToneLogic.splitToneEncode(mem)
        encodeTone(m["txtone"], tx)
        encodeTone(m["rxtone"], rx)

        m["scan"].int = if (mem.skip != "S") 1 else 0
        m["wide"].int = if (mem.mode == modes[0]) 1 else 0
        m["lowpower"].int = mem.power?.let { powerLevels.indexOf(it) }?.takeIf { it >= 0 } ?: 0

        m["bcl"].int = extras?.first ?: 0
        m["pttid"].int = extras?.second ?: 0
        m["scode"].int = extras?.third ?: scodeOffset
    }

    /** 0/0xFFFF = none; >= 0x258 = CTCSS * 10; otherwise a DCS index (+0x69 for reversed). */
    private fun decodeTone(v: Int): ToneSpec = when {
        v == 0 || v == 0xFFFF -> ToneSpec.NONE
        v >= 0x0258 -> ToneSpec("Tone", v / 10.0)
        v > 0x69 -> ToneSpec("DTCS", dtcsCodes[v - 0x6A], 'R')
        else -> ToneSpec("DTCS", dtcsCodes[v - 1], 'N')
    }

    private fun encodeTone(el: DataElement, spec: ToneSpec) {
        el.value = when (spec.mode) {
            "Tone" -> (spec.value!!.toDouble() * 10).roundToLong()
            "DTCS" -> {
                val idx = dtcsCodes.indexOf(spec.value!!.toInt())
                if (idx < 0) throw RadioException("DTCS ${spec.value} not supported")
                (idx + 1 + if (spec.polarity == 'R') 0x69 else 0).toLong()
            }
            else -> 0L
        }
    }

    // ---------------------------------------------------------------- cloning

    private suspend fun SerialPort.recv(count: Int): ByteArray {
        val d = read(count, 2000)
        if (d.isEmpty()) throw RadioException("O rádio não respondeu. Confira o cabo e se o rádio está ligado.")
        if (d.size != count) throw RadioException("Resposta incompleta do rádio (${d.size} de $count bytes)")
        return d
    }

    private suspend fun identify(port: SerialPort) {
        var sawData = false
        for (ident in idents) {
            port.flushInput()
            port.write(ident)
            val ack = port.read(fingerprint.size, 2000)
            if (ack.isNotEmpty()) sawData = true
            if (ack.isNotEmpty() && ident.startsWith(ack)) {
                // The cable is returning our own bytes: TX and RX are shorted, which on
                // Baofeng K1 plugs almost always means the plug is not fully seated.
                throw RadioException(ECHO_MESSAGE)
            }
            if (!ack.startsWith(fingerprint)) continue
            for ((magic, len) in magics) {
                port.write(magic)
                port.recv(len)
            }
            return
        }
        throw RadioException(if (sawData) "Resposta inesperada do rádio" else "O rádio não respondeu ao handshake")
    }

    private fun frame(cmd: Char, addr: Int, length: Int, data: ByteArray = ByteArray(0)) =
        byteArrayOf(cmd.code.toByte(), (addr shr 8).toByte(), addr.toByte(), length.toByte()) + data

    override suspend fun syncIn(port: SerialPort, status: (CloneStatus) -> Unit) {
        port.baudRate = BAUD_RATE
        identify(port)
        val out = ArrayList<Byte>(memSize)
        val max = memSize / BLOCK_SIZE
        status(CloneStatus("Lendo do rádio…", 0, max))
        for ((start, size) in memStarts.zip(memSizes)) {
            for (addr in start until start + size step BLOCK_SIZE) {
                port.write(frame('R', addr, BLOCK_SIZE))
                val d = port.recv(BLOCK_SIZE + 4).copyOfRange(4, BLOCK_SIZE + 4)
                val block = if (usesEncryption) crypt(encryptionSymbol, d) else d
                block.forEach(out::add)
                status(CloneStatus("Lendo do rádio…", out.size / BLOCK_SIZE, max))
            }
        }
        loadMmap(MemoryMap(out.toByteArray()))
    }

    override suspend fun syncOut(port: SerialPort, status: (CloneStatus) -> Unit) {
        port.baudRate = BAUD_RATE
        identify(port)
        val image = mmap.toByteArray()
        val max = memSize / BLOCK_SIZE
        var dataAddr = 0
        status(CloneStatus("Gravando no rádio…", 0, max))
        for ((start, size) in memStarts.zip(memSizes)) {
            for (addr in start until start + size step BLOCK_SIZE) {
                var data = image.copyOfRange(dataAddr, dataAddr + BLOCK_SIZE)
                if (usesEncryption) data = crypt(encryptionSymbol, data)
                dataAddr += BLOCK_SIZE
                port.write(frame('W', addr, BLOCK_SIZE, data))
                val ack = port.recv(1)
                if (ack[0] != 0x06.toByte()) throw RadioException("Bad ack writing block 0x${hex(addr)}")
                status(CloneStatus("Gravando no rádio…", dataAddr / BLOCK_SIZE, max))
            }
        }
    }

    companion object {
        const val BAUD_RATE = 115200
        const val BLOCK_SIZE = 0x40

        const val ECHO_MESSAGE =
            "O cabo devolveu o próprio comando (eco): o rádio não está recebendo os dados. " +
                "Empurre o conector do cabo no rádio com firmeza até encaixar por completo " +
                "(no conector K1 da Baofeng costuma ser bem justo), confira se o rádio está ligado e tente de novo."

        val INFO = DriverInfo("Baofeng", "UV-17Pro", chirpClass = "UV17Pro", baudRate = BAUD_RATE) { BaofengUV17Pro() }

        private val CRYPT_SYMBOLS = listOf(
            "BHT ", "CO 7", "A ES", " EIY", "M PQ", "XN Y", "RVB ", " HQP", "W RC", "MS N",
            " SAT", "K DH", "ZO R", "C SL", "6RB ", " JCG", "PN V", "J PK", "EK L", "I LZ",
        )

        /**
         * The radio's symmetric XOR cipher: each byte is XORed with a 4-byte key
         * unless the byte is 0x00, 0xFF, equal to the key byte or its complement,
         * or the key byte is a space.
         */
        fun crypt(symbolIndex: Int, buffer: ByteArray): ByteArray {
            val key = CRYPT_SYMBOLS[symbolIndex].encodeToByteArray()
            return ByteArray(buffer.size) { i ->
                val b = buffer[i].toInt() and 0xFF
                val k = key[i % 4].toInt() and 0xFF
                val encrypt = k != 0x20 && b != 0 && b != 0xFF && b != k && b != (k xor 0xFF)
                (if (encrypt) b xor k else b).toByte()
            }
        }

        private fun hex(v: Int) = v.toString(16).uppercase().padStart(4, '0')

        private fun ByteArray.startsWith(prefix: ByteArray) =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

        /** `MEM_FORMAT` of the UV-17Pro family, verbatim from CHIRP. */
        private val MEM_FORMAT = """
            struct memory_obj {
              lbcd rxfreq[4];
              lbcd txfreq[4];
              ul16 rxtone;
              ul16 txtone;
              u8 scode;
              u8 pttid;
              u8 unknown7:2,
                 scramble:2,
                 unknown8:2,
                 lowpower:2;
              u8 unknown1:1,
                 wide:1,
                 sqmode:2,
                 bcl:1,
                 scan:1,
                 unknown2:1,
                 fhss:1;
              u8 unknown3;
              u8 unknown4;
              u8 unknown5;
              u8 unknown6;
              char name[12];
              };
            struct vfo_entry {
              u8 freq[8];
              ul16 rxtone;
              ul16 txtone;
              u8 unknown0;
              u8 bcl;
              u8 sftd:3,
                 scode:5;
              u8 unknown1;
              u8 lowpower;
              u8 unknown2:1,
                 wide:1,
                 unknown3:5,
                 fhss:1;
              u8 unknown4;
              u8 step;
              u8 offset[6];
              u8 unknown5[2];
              u8 sqmode;
              u8 unknown6[3];
              };
            struct ani_obj {
              u8 code[5];
              u8 unknown[1];
              u8 unused1:6,
                 aniid:2;
              u8 dtmfon;
              u8 dtmfoff;
              u8 separatecode;
              u8 groupcallcode;
            };
            struct settings_obj {
              u8 squelch;
              u8 savemode;
              u8 vox;
              u8 backlight;
              u8 dualstandby;
              u8 tot;
              u8 beep;
              u8 voicesw;
              u8 voice;
              u8 sidetone;
              u8 scanmode;
              u8 pttid;
              u8 pttdly;
              u8 chadistype;
              u8 chbdistype;
              u8 bcl;
              u8 autolock;
              u8 alarmmode;
              u8 alarmtone;
              u8 unknown1;
              u8 tailclear;
              u8 rpttailclear;
              u8 rpttaildet;
              u8 roger;
              u8 a_or_b_selected; // a=0, b=1
              u8 fmenable;
              u8 chbworkmode:4,
                 chaworkmode:4;
              u8 keylock;
              u8 powerondistype;
              u8 tone;
              u8 unknown4[2];
              u8 voxdlytime;
              u8 menuquittime;
              u8 bluetooth;
              u8 unknown5;
              u8 dispani;
              u8 unknown11[3];
              u8 totalarm;
              u8 unknown6[2];
              u8 ctsdcsscantype;
              ul16 vfoscanmin;
              ul16 vfoscanmax;
              u8 gpsw;
              u8 gpsmode;
              u8 key1short;
              u8 unknown7;
              u8 key2short;
              u8 unknown8[2];
              u8 rstmenu;
              u8 singlewatch;
              u8 hangup;
              u8 voxsw;
              u8 gpstimezone;
              u8 unknown10;
              u8 inputdtmf;
              u8 gpsunits;
              u8 pontime;
            };

            struct memory_obj memory[%(mems)i];

            #seekto 0x8000;
            struct {
              struct vfo_entry a;
              struct vfo_entry b;
            } vfo;

            struct settings_obj settings;
            #seekto 0x%(ani)X;
            struct ani_obj ani;

            #seekto 0x%(pttid)04X;
            struct {
              u8 code[5];
              char name[10];
              u8 unused;
            } pttid[20];

            struct {
              u8 unknown32[32];
              u8 code[16];
            } upcode;

            struct {
              u8 code[16];
            } downcode;

            #seekto 0x%(names)04X;
            struct {
              char name[16];
            } bank_name[10];
        """.trimIndent()
    }
}

/** Band plan shared by the UV-21 Pro models (airband RX, AM). */
private val UV21_BANDS: List<LongRange> = listOf(
    108_000_000L..135_999_999L,
    136_000_000L..174_000_000L,
    220_000_000L..260_000_000L,
    350_000_000L..390_000_000L,
    400_000_000L..479_999_999L,
    480_000_000L..520_000_000L,
)

/** Baofeng UV-21 Pro (V2): UV-17Pro protocol with airband RX and AM. */
class BaofengUV21ProV2 : BaofengUV17Pro() {
    override val info: DriverInfo get() = INFO

    override val validBands: List<LongRange> = UV21_BANDS
    override val modes = listOf("NFM", "FM", "AM")

    companion object {
        val INFO = DriverInfo(
            "Baofeng", "UV-21ProV2", chirpClass = "UV21ProV2", baudRate = BaofengUV17Pro.BAUD_RATE,
        ) { BaofengUV21ProV2() }
    }
}

/**
 * Baofeng UV-17ProGPS family ("COLOR PRO" firmware). Same memory layout as the
 * UV-17Pro, but identifies with `PROGRAMCOLORPROU` and answers the 0x4D model
 * query with 7 bytes. Port of CHIRP's `UV17ProGPS`.
 */
open class BaofengUV17ProGPS : BaofengUV17Pro(), SupportsLogo {
    override val info: DriverInfo get() = INFO

    /** "UV-17/18/19/20 Pro" entry of Baofeng's Import Picture tool. */
    override val logoSpec: LogoSpec get() = LogoSpec(width = 150, height = 128, flashAddress = 0xC0000)

    override val idents: List<ByteArray> = listOf("PROGRAMCOLORPROU".encodeToByteArray())
    override val magics: List<Pair<ByteArray, Int>> = listOf(
        byteArrayOf(0x46) to 16,
        byteArrayOf(0x4D) to 7,
        byteArrayOf(
            0x53, 0x45, 0x4E, 0x44, 0x21, 0x05, 0x0D, 0x01, 0x01, 0x01, 0x04, 0x11, 0x08, 0x05,
            0x0D, 0x0D, 0x01, 0x11, 0x0F, 0x09, 0x12, 0x09, 0x10, 0x04, 0x00,
        ) to 1,
    )
    override val validBands: List<LongRange> = listOf(
        108_000_000L..135_999_999L,
        136_000_000L..174_000_000L,
        200_000_000L..260_000_000L,
        400_000_000L..520_000_000L,
        350_000_000L..390_000_000L,
    )
    override val modes = listOf("NFM", "FM", "AM")

    override val hasWhenToSendAniId = false
    override val hasPilotTone = true
    override val hasSendIdDelay = true
    override val hasSkey2Short = true
    override val hasVoxSwitch = true
    override val hasGps = true
    override val hasBankNames = true

    override fun features() = super.features().copy(hasBank = true)

    /** 10 fixed banks of 100 channels; only their names (bank_name[10]) are editable. */
    override fun bankModel(): BankModel = object : StaticBankModel(features().memoryBounds, 10, ::bankNames) {
        override val nameLength = BANK_NAME_LENGTH
        override fun rename(bank: Bank, name: String) {
            require(name.length <= BANK_NAME_LENGTH) { "Nome do banco com mais de $BANK_NAME_LENGTH caracteres" }
            require(name.all { it.code in 0x20..0x7E }) { "Use apenas letras sem acento, números e símbolos" }
            val raw = name.encodeToByteArray() + ByteArray(BANK_NAME_LENGTH - name.length) { 0xFF.toByte() }
            memobj["bank_name"][bank.index - 1]["name"].setRaw(raw)
        }
    }

    private fun bankNames(): List<String> = List(10) { i ->
        // Stored as GB2312, padded with 0xFF; ASCII covers the names this app writes.
        memobj["bank_name"][i]["name"].getRaw()
            .takeWhile { it != 0xFF.toByte() && it != 0.toByte() }
            .joinToString("") { b -> (b.toInt() and 0xFF).let { if (it in 0x20..0x7E) it.toChar().toString() else "?" } }
            .trim()
    }

    companion object {
        const val BANK_NAME_LENGTH = 16

        val INFO = DriverInfo(
            "Baofeng", "UV-17ProGPS", chirpClass = "UV17ProGPS", baudRate = BaofengUV17Pro.BAUD_RATE,
        ) { BaofengUV17ProGPS() }
    }
}

/**
 * Baofeng UV-21 Pro with "COLOR PRO" firmware: answers `PROGRAMCOLORPROU` and
 * reports model `UV21PRO`. Port of CHIRP's `UV21ProGPS`.
 */
class BaofengUV21ProGPS : BaofengUV17ProGPS() {
    override val info: DriverInfo get() = INFO

    override val validBands: List<LongRange> = UV21_BANDS

    /** "UV-21 Pro" entry of Baofeng's Import Picture tool: portrait 128x160 at 0x100000. */
    override val logoSpec: LogoSpec get() = LogoSpec(width = 128, height = 160, flashAddress = 0x100000)

    companion object {
        val INFO = DriverInfo(
            "Baofeng", "UV-21ProGPS", chirpClass = "UV21ProGPS", baudRate = BaofengUV17Pro.BAUD_RATE,
        ) { BaofengUV21ProGPS() }
    }
}
