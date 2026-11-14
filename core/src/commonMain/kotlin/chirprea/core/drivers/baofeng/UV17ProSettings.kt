package chirprea.core.drivers.baofeng

import chirprea.core.bitwise.DataElement
import chirprea.core.bitwise.StructElement
import chirprea.core.model.Freq
import chirprea.core.radio.BoolSetting
import chirprea.core.radio.ChoiceSetting
import chirprea.core.radio.Setting
import chirprea.core.radio.SettingGroup
import chirprea.core.radio.TextSetting

/**
 * Radio-wide settings of the UV-17Pro family. Port of `get_settings*` in
 * CHIRP's `baofeng_uv17Pro.py` (GPL-3.0); setting ids match CHIRP's paths.
 */
internal class UV17ProSettings(private val r: BaofengUV17Pro, private val m: StructElement) {
    private val s get() = m["settings"]

    fun build(): List<SettingGroup> = buildList {
        add(SettingGroup("basic", "Básico", basic()))
        add(SettingGroup("workmode", "Modo de trabalho", subgroups = listOf(vfo("a", "VFO A"), vfo("b", "VFO B"))))
        add(SettingGroup("dtmfe", "DTMF", dtmf()))
        if (r.hasBankNames) add(SettingGroup("bank", "Nomes dos bancos", bankNames()))
    }

    // ------------------------------------------------------------ helpers

    /** List setting backed by an integer field; out-of-range raw values show [fallback]. */
    private fun choice(id: String, label: String, doc: String, el: DataElement, options: List<String>, fallback: Int = 0) =
        ChoiceSetting(id, label, doc, options, el.int.takeIf { it in options.indices } ?: fallback) { el.int = it }

    private fun bool(id: String, label: String, doc: String, el: DataElement) =
        BoolSetting(id, label, doc, el.int != 0) { el.int = if (it) 1 else 0 }

    private fun set(name: String, label: String, options: List<String>, doc: String, fallback: Int = 0) =
        choice("settings.$name", label, doc, s[name], options, fallback)

    private fun flag(name: String, label: String, doc: String) = bool("settings.$name", label, doc, s[name])

    // -------------------------------------------------------------- basic

    private fun basic(): List<Setting> = buildList {
        add(set("squelch", "Squelch", r.squelchList,
            "Intensidade mínima do sinal para abrir o alto-falante. Off é o nível 0 (sempre aberto)."))
        add(set("tot", "Timeout (tempo máx. de TX)", LIST_TIMEOUT,
            "Limita a duração de cada transmissão. Off não limita.", fallback = 3))
        add(set("dualstandby", "Dual Watch", listOf("Off", "On"),
            "Monitora alternadamente as frequências A e B.", fallback = 1))
        if (s["powerondistype"].int < LIST_POWERON_DISPLAY.size) {
            add(set("powerondistype", "Tela ao ligar", LIST_POWERON_DISPLAY, "Logo ou tensão da bateria ao ligar."))
        }
        if (r.hasVoice) add(set("voice", "Idioma da voz", listOf("English", "Chinese"), "Idioma dos menus e da voz.", fallback = 1))
        add(flag("voicesw", "Voz ativada", "Anuncia canais e operações em voz alta."))
        add(set("backlight", "Tempo da luz de fundo", LIST_BACKLIGHT, "Always On mantém a luz sempre acesa."))
        add(flag("autolock", "Bloqueio automático do teclado", "Trava o teclado sozinho para evitar toques acidentais."))
        add(set("beep", "Bipe", listOf("Off", "Beep", "Voice", "Both"), "Tom de confirmação ao operar o rádio.", fallback = 1))
        add(flag("roger", "Roger beep", "Envia um tom curto ao final de cada transmissão."))
        add(set("chadistype", "Display do canal A", LIST_DISPLAY_MODE, "No modo canal, mostra nome, frequência ou número."))
        add(set("chbdistype", "Display do canal B", LIST_DISPLAY_MODE, "No modo canal, mostra nome, frequência ou número."))
        // get_settings_pro_basic
        add(set("savemode", "Economia de energia", listOf("Off", "On"),
            "Desliga o receptor em ciclos quando não há sinal, poupando bateria.", fallback = 1))
        add(set("totalarm", "Alarme do timeout", listOf("Off") + (1..10).map { "$it sec" },
            "Avisa alguns segundos antes do timeout encerrar a transmissão."))
        if (r.hasPilotTone) {
            add(set("tone", "Tom piloto", listOf("1000 Hz", "1450 Hz", "1750 Hz", "2100 Hz"), "Frequência do tom de acesso a repetidoras."))
        }
        add(set("sidetone", "Side tone", listOf("Off", "KB Side Tone", "ANI Side Tone", "KB + ANI Side Tone"),
            "Quais tons DTMF transmitidos também saem no alto-falante."))
        add(flag("tailclear", "Tail clear", "Envia um sinal de fim de transmissão que reduz o ruído no outro rádio."))
        add(set("scanmode", "Modo de varredura", listOf("Time", "Carrier", "Search"),
            "Time retoma após um tempo, Carrier quando o sinal some, Search para no sinal."))
        add(set("alarmmode", "Modo do alarme", listOf("Local", "Send Tone", "Send Code"), "O que o alarme de emergência faz."))
        add(flag("alarmtone", "Som do alarme", "Liga ou desliga o som do alarme neste rádio."))
        add(flag("keylock", "Teclado travado", "Estado atual do bloqueio do teclado (segure * no rádio)."))
        add(set("menuquittime", "Tempo de saída do menu", (5..50 step 5).map { "$it sec" } + "60 sec",
            "Tempo sem tocar nas teclas até o menu fechar."))
        if (r.hasSendIdDelay) {
            add(set("pttdly", "Atraso do envio do ID", (100..3000 step 100).map { "$it ms" },
                "Espera antes de enviar o ANI/PTT-ID após apertar o PTT."))
        }
        add(set("ctsdcsscantype", "Gravar tom da varredura (QT)", listOf("Both", "RX", "TX"),
            "Onde gravar o CTCSS/DCS encontrado na varredura em modo VFO."))
        if (r.hasSkey2Short) add(sideKey("key2short", "Tecla lateral 2 (toque curto)"))
        add(set("chaworkmode", "Modo do canal A", listOf("Frequency", "Channel"), "VFO (sintonia direta) ou memórias no display A."))
        add(set("chbworkmode", "Modo do canal B", listOf("Frequency", "Channel"), "VFO (sintonia direta) ou memórias no display B."))
        add(set("rpttailclear", "Rpt tail clear", LIST_RPT_TAIL, "Eliminação do rabicho de squelch em repetidoras compatíveis."))
        add(set("rpttaildet", "Rpt tail delay", LIST_RPT_TAIL, "Atraso antes da eliminação do rabicho de squelch."))
        add(flag("rstmenu", "Menu de reset habilitado", "Permite o menu RESET no painel. Não reseta o rádio sozinho."))
        if (r.hasVoxSwitch) {
            add(flag("voxsw", "VOX", "Transmite automaticamente quando você fala no microfone."))
            add(set("vox", "Nível do VOX", (1..9).map(Int::toString), "Sensibilidade do VOX."))
        } else {
            add(set("vox", "Nível do VOX", listOf("Off") + (1..9).map(Int::toString), "Sensibilidade do VOX."))
        }
        add(set("voxdlytime", "Atraso do VOX", (500..2000 step 100).map { "$it ms" },
            "Quanto tempo o transmissor fica ativo depois que você para de falar."))
        if (r.hasGps) {
            add(flag("gpsw", "GPS ligado", "Liga o receptor GPS."))
            add(set("gpsmode", "Modo do GPS", listOf("GPS", "Beidou", "GPS + Beidou"), "Constelação de satélites usada."))
            add(set("gpstimezone", "Fuso horário do GPS", (-12..12).map(Int::toString), "Fuso usado para mostrar a hora do GPS."))
        }
        add(flag("fmenable", "Desativar rádio FM", "Bloqueia o receptor de rádio FM comercial."))
    }

    /** Side-key functions are stored as key codes, not indices. */
    private fun sideKey(name: String, label: String): ChoiceSetting {
        val el = s[name]
        val codes = SIDE_KEYS.values.toList()
        return ChoiceSetting("settings.$name", label, "Função da tecla lateral.", SIDE_KEYS.keys.toList(),
            codes.indexOf(el.int).coerceAtLeast(0)) { el.int = codes[it] }
    }

    // ---------------------------------------------------------- work mode

    private fun vfo(side: String, label: String): SettingGroup {
        val v = m["vfo"][side]
        val powers = r.powerLevels.map { it.label }
        val items = listOf(
            TextSetting("vfo.$side.freq", "Frequência", "Frequência de recepção do $label (MHz).",
                Freq.format(digits(v["freq"]) * 10), 10, "0123456789.") { text ->
                val hz = Freq.parse(text)
                require(r.validBands.any { hz >= it.first && hz < it.last }) { "${Freq.format(hz)} está fora das bandas do rádio" }
                setDigits(v["freq"], hz / 10)
            },
            choice("vfo.$side.sftd", "Shift", "Direção do deslocamento de repetidora.", v["sftd"], listOf("Off", "+", "-")),
            TextSetting("vfo.$side.offset", "Offset (0.0–999.999)", "Diferença entre RX e TX em MHz.",
                Freq.format(digits(v["offset"]) * 1000), 10, "0123456789.") { text ->
                setDigits(v["offset"], Freq.parse(text) / 1000)
            },
            choice("vfo.$side.lowpower", "Potência", "Potência de transmissão do $label.", v["lowpower"], powers),
            choice("vfo.$side.wide", "Largura de banda", "Wide ou Narrow.", v["wide"], listOf("Wide", "Narrow")),
            choice("vfo.$side.scode", "Código PTT-ID", "Qual dos códigos DTMF este VFO envia.", v["scode"], r.scodeList),
            choice("vfo.$side.step", "Passo de sintonia", "Passo do VFO e da varredura em modo frequência.", v["step"],
                r.steps.map { if (it % 1.0 == 0.0) "${it.toLong()}.0" else it.toString() }),
        )
        return SettingGroup("vfo$side", label, items)
    }

    /** u8 array where each byte holds one decimal digit. */
    private fun digits(arr: DataElement): Long {
        var v = 0L
        for (i in 0 until arr.count) {
            val d = arr[i].int
            if (d > 9) return 0
            v = v * 10 + d
        }
        return v
    }

    private fun setDigits(arr: DataElement, value: Long) {
        var v = value
        for (i in arr.count - 1 downTo 0) {
            arr[i].int = (v % 10).toInt()
            v /= 10
        }
    }

    // --------------------------------------------------------------- DTMF

    private fun dtmf(): List<Setting> = buildList {
        val pttid = m["pttid"]
        for (i in r.scodeList.indices) {
            val entry = pttid[i]
            add(codeSetting("pttid/$i.code", "Código ${i + 1}", "Código DTMF ${i + 1}, enviado como PTT-ID.", entry["code"]))
            if ("name" in entry) {
                val nameEl = entry["name"]
                add(TextSetting("pttid/$i.name", "Nome do código ${i + 1}", "Nome de até 10 caracteres.",
                    asciiUntil(nameEl.getRaw()), 10) { nameEl.setRaw(padFF(it, 10)) })
            }
        }
        val ani = m["ani"]
        add(codeSetting("ani.code", "Código ANI", "Identificação numérica automática do rádio (DTMF).", ani["code"]))
        val speeds = listOf(50, 100, 200, 300, 500).map { "$it ms" }
        add(ChoiceSetting("ani.dtmfon", "Velocidade DTMF (tom)", "Duração de cada tom DTMF.", speeds,
            ani["dtmfon"].int.let { if (it > 0xC3) 3 else it }.takeIf { it in speeds.indices } ?: 0) { ani["dtmfon"].int = it })
        add(ChoiceSetting("ani.dtmfoff", "Velocidade DTMF (pausa)", "Pausa entre tons DTMF.", speeds,
            ani["dtmfoff"].int.let { if (it > 0xC3) 3 else it }.takeIf { it in speeds.indices } ?: 0) { ani["dtmfoff"].int = it })
        if (r.hasWhenToSendAniId) {
            add(choice("ani.aniid", "Quando enviar o ANI", "BOT ao apertar o PTT, EOT ao soltar, Both nos dois.",
                ani["aniid"], listOf("Off", "BOT", "EOT", "Both")))
        }
        add(set("hangup", "Tempo de hang-up", (3..10).map { "$it s" },
            "Quanto tempo o decodificador DTMF fica ativo após um código válido."))
    }

    private fun codeSetting(id: String, label: String, doc: String, code: DataElement): TextSetting {
        val current = (0 until code.count).map { code[it].int }.filter { it < 0x1F && it < DTMF_CHARS.length }
            .joinToString("") { DTMF_CHARS[it].toString() }
        return TextSetting(id, label, doc, current, code.count, DTMF_CHARS) { text ->
            for (j in 0 until code.count) code[j].int = text.getOrNull(j)?.let(DTMF_CHARS::indexOf) ?: 0xFF
        }
    }

    // -------------------------------------------------------------- banks

    private fun bankNames(): List<Setting> {
        val model = (r as BaofengUV17ProGPS).bankModel()
        return model.banks.map { bank ->
            TextSetting("bank_name/${bank.index - 1}.name", "Nome do banco ${bank.index}", "Até 16 caracteres.",
                bank.name, BaofengUV17ProGPS.BANK_NAME_LENGTH) { model.rename(bank, it) }
        }
    }

    companion object {
        const val DTMF_CHARS = "0123456789ABCD*#"

        val LIST_TIMEOUT = listOf("Off") + (15 until 195 step 15).map { "$it sec" }
        val LIST_POWERON_DISPLAY = listOf("LOGO", "BATT voltage")
        val LIST_BACKLIGHT = listOf("Always On") + (5 until 25 step 5).map { "$it sec" }
        val LIST_DISPLAY_MODE = listOf("Name", "Frequency", "Channel Number")
        val LIST_RPT_TAIL = (0..1000 step 100).map { "$it ms" }

        /** Side-key function name -> code stored in the radio. */
        val SIDE_KEYS = linkedMapOf("FM" to 0x07, "Scan" to 0x1C, "Search" to 0x1D, "Vox" to 0x2D)

        fun asciiUntil(raw: ByteArray): String = raw
            .takeWhile { it != 0.toByte() && it != 0xFF.toByte() }
            .joinToString("") { b -> (b.toInt() and 0xFF).let { if (it in 0x20..0x7E) it.toChar().toString() else "?" } }
            .trim()

        fun padFF(text: String, length: Int): ByteArray =
            text.encodeToByteArray().copyOf(length).also { arr -> for (i in text.length until length) arr[i] = 0xFF.toByte() }
    }
}
