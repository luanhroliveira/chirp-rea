package chirprea.app.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import chirprea.core.model.Chirp
import chirprea.core.model.Fmt
import chirprea.core.model.Freq
import chirprea.core.model.Memory
import chirprea.core.radio.RadioFeatures

/** How a grid column edits its value. */
sealed interface Editor {
    data object ReadOnly : Editor
    data object Text : Editor
    data class Choice(val options: (RadioFeatures) -> List<String>) : Editor
}

/** One column of the memory grid, mirroring CHIRP's memedit columns. */
data class GridColumn(
    val title: String,
    val width: Dp,
    val editor: Editor,
    val visible: (RadioFeatures) -> Boolean = { true },
    val get: (Memory) -> String,
    val set: (Memory, String, RadioFeatures) -> Memory = { m, _, _ -> m },
)

private fun tone(v: Double) = Fmt.fixed(v, 1)

val GRID_COLUMNS: List<GridColumn> = listOf(
    GridColumn("Loc", 56.dp, Editor.ReadOnly, get = { it.number.toString() }),
    GridColumn(
        "Frequency", 110.dp, Editor.Text,
        get = { if (it.empty) "" else Freq.format(it.freq) },
        set = { m, v, f ->
            val hz = Freq.parse(v)
            if (m.empty) {
                m.copy(
                    empty = false, freq = hz,
                    power = f.validPowerLevels.firstOrNull(),
                    mode = f.validModes.firstOrNull { it == "FM" } ?: f.validModes.first(),
                )
            } else m.copy(freq = hz)
        },
    ),
    GridColumn(
        "Name", 110.dp, Editor.Text, visible = { it.hasName },
        get = { it.name },
        set = { m, v, _ -> m.copy(name = v) },
    ),
    GridColumn(
        "Tone Mode", 90.dp, Editor.Choice { it.validTmodes },
        get = { it.tmode },
        set = { m, v, _ -> m.copy(tmode = v) },
    ),
    GridColumn(
        "Tone", 70.dp, Editor.Choice { f -> f.validTones.map(::tone) },
        get = { tone(it.rtone) },
        set = { m, v, _ -> m.copy(rtone = v.toDouble()) },
    ),
    GridColumn(
        "ToneSql", 70.dp, Editor.Choice { f -> f.validTones.map(::tone) }, visible = { it.hasCtone },
        get = { tone(it.ctone) },
        set = { m, v, _ -> m.copy(ctone = v.toDouble()) },
    ),
    GridColumn(
        "DTCS Code", 80.dp, Editor.Choice { f -> f.validDtcsCodes.map { Fmt.zeroPad(it, 3) } }, visible = { it.hasDtcs },
        get = { Fmt.zeroPad(it.dtcs, 3) },
        set = { m, v, _ -> m.copy(dtcs = v.toInt()) },
    ),
    GridColumn(
        "RX DTCS", 80.dp, Editor.Choice { f -> f.validDtcsCodes.map { Fmt.zeroPad(it, 3) } }, visible = { it.hasRxDtcs },
        get = { Fmt.zeroPad(it.rxDtcs, 3) },
        set = { m, v, _ -> m.copy(rxDtcs = v.toInt()) },
    ),
    GridColumn(
        "DTCS Pol", 76.dp, Editor.Choice { Chirp.DTCS_POLARITIES }, visible = { it.hasDtcsPolarity },
        get = { it.dtcsPolarity },
        set = { m, v, _ -> m.copy(dtcsPolarity = v) },
    ),
    GridColumn(
        "Cross Mode", 100.dp, Editor.Choice { it.validCrossModes }, visible = { it.hasCrossMode },
        get = { it.crossMode },
        set = { m, v, _ -> m.copy(crossMode = v) },
    ),
    GridColumn(
        "Duplex", 70.dp, Editor.Choice { it.validDuplexes },
        get = { it.duplex },
        set = { m, v, _ -> m.copy(duplex = v) },
    ),
    GridColumn(
        "Offset", 100.dp, Editor.Text, visible = { it.hasOffset },
        get = { if (it.empty) "" else Freq.format(it.offset) },
        set = { m, v, _ -> m.copy(offset = Freq.parse(v)) },
    ),
    GridColumn(
        "Mode", 70.dp, Editor.Choice { it.validModes },
        get = { it.mode },
        set = { m, v, _ -> m.copy(mode = v) },
    ),
    GridColumn(
        "Power", 76.dp, Editor.Choice { f -> f.validPowerLevels.map { it.label } },
        visible = { it.validPowerLevels.isNotEmpty() },
        get = { it.power?.label ?: "" },
        set = { m, v, f -> m.copy(power = f.validPowerLevels.firstOrNull { it.label == v }) },
    ),
    GridColumn(
        "Skip", 56.dp, Editor.Choice { it.validSkips },
        get = { it.skip },
        set = { m, v, _ -> m.copy(skip = v) },
    ),
    GridColumn(
        "Comment", 180.dp, Editor.Text, visible = { it.hasComment },
        get = { it.comment },
        set = { m, v, _ -> m.copy(comment = v) },
    ),
)
