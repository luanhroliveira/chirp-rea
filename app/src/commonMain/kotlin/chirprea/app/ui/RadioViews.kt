package chirprea.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chirprea.app.RadioDocument
import chirprea.core.bitwise.ArrayElement
import chirprea.core.bitwise.BcdElement
import chirprea.core.bitwise.BitElement
import chirprea.core.bitwise.CharElement
import chirprea.core.bitwise.DataElement
import chirprea.core.bitwise.IntElement
import chirprea.core.bitwise.StructElement
import chirprea.core.memmap.HexDump
import chirprea.core.model.Freq
import chirprea.core.radio.Bank
import chirprea.core.radio.CloneModeRadio

enum class RadioTab(val title: String) {
    Memories("Memórias"), Settings("Configurações"), Banks("Bancos"), Info("Informações"), Browser("Navegador"),
}

/** Sub-tabs available for a document, mirroring CHIRP's per-radio tabs. */
fun RadioDocument.tabs(): List<RadioTab> = buildList {
    add(RadioTab.Memories)
    if (radio.settings().isNotEmpty()) add(RadioTab.Settings)
    if (radio.bankModel() != null) add(RadioTab.Banks)
    add(RadioTab.Info)
    if (radio is CloneModeRadio) add(RadioTab.Browser)
}

// ------------------------------------------------------------------- Bancos

@Composable
fun BanksView(doc: RadioDocument, onStatus: (String) -> Unit) {
    val model = doc.radio.bankModel() ?: return
    var version by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf(1) }
    val banks = remember(version) { model.banks }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(420.dp).fillMaxHeight().padding(12.dp).verticalScroll(rememberScrollState())) {
            Text("Bancos", style = MaterialTheme.typography.titleMedium)
            if (model.isStatic) {
                Text(
                    "Este rádio tem bancos fixos: cada banco agrupa uma faixa de canais e não é possível mover canais entre eles." +
                        if (model.nameLength > 0) " Só o nome do banco pode ser alterado." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            banks.forEach { bank ->
                val range = model.memoriesIn(bank)
                val used = doc.rows.count { !it.empty && it.number in range }
                BankRow(
                    bank = bank,
                    rangeText = "Canais ${range.first}–${range.last} · $used em uso",
                    selected = bank.index == selected,
                    editable = model.nameLength > 0,
                    maxLength = model.nameLength,
                    onSelect = { selected = bank.index },
                    onRename = { name ->
                        runCatching { model.rename(bank, name) }
                            .onSuccess { version++; doc.dirty = true; onStatus("Banco ${bank.index} renomeado para '$name'") }
                            .onFailure { onStatus("Não foi possível renomear: ${it.message}") }
                    },
                )
            }
        }
        VerticalDivider()
        val bank = banks.firstOrNull { it.index == selected }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            if (bank != null) {
                val range = model.memoriesIn(bank)
                Text(
                    "Banco ${bank.index}" + (if (bank.name.isNotBlank()) " — ${bank.name}" else ""),
                    style = MaterialTheme.typography.titleMedium,
                )
                val mems = doc.rows.filter { it.number in range && !it.empty }
                if (mems.isEmpty()) {
                    Text("Nenhum canal programado neste banco.", Modifier.padding(top = 8.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                        items(mems, key = { it.number }) { m ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text(m.number.toString(), Modifier.width(56.dp), style = MaterialTheme.typography.bodySmall)
                                Text(Freq.format(m.freq), Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
                                Text(m.name, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BankRow(
    bank: Bank,
    rangeText: String,
    selected: Boolean,
    editable: Boolean,
    maxLength: Int,
    onSelect: () -> Unit,
    onRename: (String) -> Unit,
) {
    var text by remember(bank.name) { mutableStateOf(bank.name) }
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Row(
        Modifier.fillMaxWidth().background(bg).clickable(onClick = onSelect).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("${bank.index}", Modifier.width(24.dp), fontWeight = FontWeight.SemiBold)
        Column(Modifier.weight(1f)) {
            if (editable) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= maxLength) text = it },
                    singleLine = true,
                    placeholder = { Text("(sem nome)") },
                    supportingText = { Text(rangeText) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onRename(text) }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                    ),
                    trailingIcon = if (text != bank.name) {
                        { androidx.compose.material3.TextButton(onClick = { onRename(text) }) { Text("Salvar") } }
                    } else null,
                )
            } else {
                Text(bank.name.ifBlank { "Banco ${bank.index}" })
                Text(rangeText, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ------------------------------------------------------------- Informações

@Composable
fun InfoView(doc: RadioDocument) {
    val items = remember(doc) { doc.radio.info() }
    SelectionContainer {
        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Informações do rádio", style = MaterialTheme.typography.titleMedium)
            items.forEach { item ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text(item.label, Modifier.width(240.dp), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(item.value, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }
        }
    }
}

// --------------------------------------------------------------- Navegador

private data class Node(val path: String, val name: String, val el: DataElement, val depth: Int) {
    /** Structs and arrays of structs/arrays expand; scalar arrays (char[], lbcd[]) are shown as one value. */
    val expandable: Boolean
        get() = el is StructElement || (el is ArrayElement && el.count > 0 && el[0].let { it is StructElement || it is ArrayElement })
}

private fun children(n: Node): List<Node> = when (val e = n.el) {
    is StructElement -> e.items().map { (k, v) -> Node("${n.path}.$k", k, v, n.depth + 1) }
    is ArrayElement -> (0 until e.count).map { i -> Node("${n.path}[$i]", "[$i]", e[i], n.depth + 1) }
    else -> emptyList()
}

private fun describe(e: DataElement): String = runCatching {
    when (e) {
        is IntElement -> "${e.value}  ($e)"
        is BitElement -> "${e.value}  (${e.nbits} bit${if (e.nbits > 1) "s" else ""})"
        is BcdElement -> e.value.toString()
        is CharElement -> "'${e.string}'"
        is ArrayElement -> when (e.count.takeIf { it > 0 }?.let { e[0] }) {
            is CharElement -> "\"" + e.string.map { c -> if (c.code in 0x20..0x7E) c else '·' }.joinToString("") + "\""
            is BcdElement -> e.value.toString()
            else -> e.getRaw().joinToString(" ") { (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase() }
        }
        is StructElement -> "${e.keys.size} campos"
        else -> ""
    }
}.getOrElse { "?" }

/** Developer view of the parsed memory map, like CHIRP's "Browser" tab. */
@Composable
fun BrowserView(doc: RadioDocument) {
    val radio = doc.radio as? CloneModeRadio ?: return
    val root = remember(doc) { Node("", "memória", radio.memoryObject, -1) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var selected by remember { mutableStateOf<Node?>(null) }

    val visible = remember(expanded, root) {
        buildList {
            fun walk(n: Node) {
                children(n).forEach { c ->
                    add(c)
                    if (c.expandable && c.path in expanded) walk(c)
                }
            }
            walk(root)
        }
    }

    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.width(560.dp).fillMaxHeight()) {
            items(visible, key = { it.path }) { n ->
                val isSel = selected?.path == n.path
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable {
                            selected = n
                            if (n.expandable) expanded = if (n.path in expanded) expanded - n.path else expanded + n.path
                        }
                        .padding(start = (12 + n.depth * 16).dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
                ) {
                    Text(
                        if (n.expandable) (if (n.path in expanded) "▾ " else "▸ ") else "   ",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(n.name, Modifier.width(180.dp), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    if (!n.expandable) {
                        Text(describe(n.el), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
            }
        }
        VerticalDivider()
        Column(Modifier.fillMaxSize().padding(12.dp).horizontalScroll(rememberScrollState())) {
            val n = selected
            if (n == null) {
                Text("Selecione um campo para ver os bytes.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val bytes = (n.el.sizeBits + 7) / 8
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(n.path.removePrefix("."), style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                        Text(
                            "Endereço 0x${n.el.offset.toString(16).uppercase().padStart(4, '0')} · ${n.el.sizeBits} bits ($bytes bytes)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (!n.expandable) Text("Valor: ${describe(n.el)}", style = MaterialTheme.typography.bodySmall)
                        Box(Modifier.padding(top = 8.dp)) {
                            Text(
                                HexDump.format(radio.mmap.getBytes(n.el.offset, maxOf(1, minOf(bytes, 512))), 0)
                                    .lines().joinToString("\n") { line ->
                                        // Re-base the dump addresses on the element's real offset.
                                        if (line.length < 4) line
                                        else (line.substring(0, 4).toInt(16) + n.el.offset).toString(16).uppercase().padStart(4, '0') + line.substring(4)
                                    },
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
