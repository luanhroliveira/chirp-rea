package chirprea.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import chirprea.app.RadioDocument
import chirprea.core.model.Memory
import chirprea.core.radio.RadioFeatures

private val ROW_HEIGHT = 28.dp

/** Spreadsheet-style editor of a document's memories (CHIRP's memedit). */
@Composable
fun MemoryGrid(doc: RadioDocument, onStatus: (String) -> Unit, modifier: Modifier = Modifier) {
    val features = doc.features
    val columns = GRID_COLUMNS.filter { it.visible(features) }
    val totalWidth = columns.fold(40.dp) { acc, c -> acc + c.width }

    Box(modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(totalWidth).fillMaxHeight()) {
            Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant).height(ROW_HEIGHT)) {
                columns.forEach { col ->
                    Box(Modifier.width(col.width).fillMaxHeight().padding(horizontal = 6.dp), Alignment.CenterStart) {
                        Text(col.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                itemsIndexed(doc.rows, key = { _, m -> m.number }) { index, mem ->
                    MemoryRow(
                        mem = mem,
                        striped = index % 2 == 1,
                        columns = columns,
                        features = features,
                        onEdit = { col, text ->
                            val result = runCatching { col.set(mem, text, features) }
                            result.exceptionOrNull()?.let { onStatus("Valor inválido em ${col.title}: ${it.message}") }
                            result.getOrNull()?.let { updated ->
                                val errors = doc.update(updated)
                                onStatus(if (errors.isEmpty()) "Memória ${mem.number} atualizada" else errors.joinToString("; "))
                            }
                        },
                        onErase = {
                            doc.erase(mem.number)
                            onStatus("Memória ${mem.number} apagada")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryRow(
    mem: Memory,
    striped: Boolean,
    columns: List<GridColumn>,
    features: RadioFeatures,
    onEdit: (GridColumn, String) -> Unit,
    onErase: () -> Unit,
) {
    val bg = if (striped) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface
    Row(Modifier.background(bg).height(ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        columns.forEach { col ->
            // Like CHIRP, an empty memory can only be brought to life by typing a frequency.
            val editable = col.editor !is Editor.ReadOnly && (!mem.empty || col.title == "Frequency")
            val text = if (mem.empty && col.title != "Loc") "" else col.get(mem)
            Box(Modifier.width(col.width).fillMaxHeight().padding(horizontal = 2.dp), Alignment.CenterStart) {
                val editor = col.editor
                when {
                    !editable -> CellText(text, dim = mem.empty)
                    editor is Editor.Text -> TextCell(text) { onEdit(col, it) }
                    editor is Editor.Choice -> ChoiceCell(text, editor.options(features)) { onEdit(col, it) }
                }
            }
        }
        if (!mem.empty) {
            IconButton(onClick = onErase, modifier = Modifier.width(40.dp).fillMaxHeight()) {
                Icon(Icons.Default.Close, contentDescription = "Apagar memória", Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
private fun CellText(text: String, dim: Boolean = false) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 4.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
        color = if (dim) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
    )
}

/** Text cell that commits on Enter or focus loss. */
@Composable
private fun TextCell(value: String, onCommit: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    val commit = { if (text != value) onCommit(text) }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .onFocusChanged {
                if (focused && !it.isFocused) commit()
                focused = it.isFocused
            },
    )
}

@Composable
private fun ChoiceCell(value: String, options: List<String>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().clickable { open = true }, Alignment.CenterStart) {
        CellText(value)
        if (open) {
            DropdownMenu(expanded = true, onDismissRequest = { open = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt.ifEmpty { "(nenhum)" }, style = MaterialTheme.typography.bodySmall) },
                        onClick = { open = false; if (opt != value) onPick(opt) },
                    )
                }
            }
        }
    }
}
