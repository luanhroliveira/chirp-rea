package chirprea.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import chirprea.app.RadioDocument
import chirprea.core.radio.BoolSetting
import chirprea.core.radio.ChoiceSetting
import chirprea.core.radio.Setting
import chirprea.core.radio.SettingGroup
import chirprea.core.radio.TextSetting

/** Flattened navigation entry: a group or a subgroup (e.g. "Modo de trabalho › VFO A"). */
private data class Section(val title: String, val group: SettingGroup)

private fun sections(groups: List<SettingGroup>): List<Section> = buildList {
    for (g in groups) {
        if (g.items.isNotEmpty() || g.subgroups.isEmpty()) add(Section(g.label, g))
        g.subgroups.forEach { add(Section("${g.label} › ${it.label}", it)) }
    }
}

/** CHIRP's "Settings" tab: radio-wide options grouped like in CHIRP. */
@Composable
fun SettingsView(doc: RadioDocument, onStatus: (String) -> Unit, onChangeLogo: (() -> Unit)? = null) {
    // Settings are snapshots; bump the version after each write to re-read them.
    var version by remember { mutableIntStateOf(0) }
    val groups = remember(doc, version) { doc.radio.settings() }
    val secs = remember(groups) { sections(groups) }
    var selected by remember { mutableIntStateOf(0) }
    val section = secs.getOrNull(selected) ?: return

    fun applied(setting: Setting, block: () -> Unit) {
        runCatching(block)
            .onSuccess { version++; doc.dirty = true; onStatus("${setting.label} alterado") }
            .onFailure { onStatus("${setting.label}: ${it.message}") }
    }

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(260.dp).fillMaxHeight().padding(vertical = 8.dp)) {
            secs.forEachIndexed { i, s ->
                val isSel = i == selected
                Text(
                    s.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSel) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { selected = i }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Text(
                "As alterações ficam na imagem aberta. Use Rádio › Enviar para gravar no rádio.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        VerticalDivider()
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Text(section.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 12.dp))
            }
            items(section.group.items, key = { it.id }) { setting ->
                SettingRow(setting, onChangeLogo.takeIf { setting.id == LOGO_SETTING_ID }) { block -> applied(setting, block) }
                HorizontalDivider()
            }
        }
    }
}

/** The "Tela ao ligar" setting; radios with a replaceable logo get an extra button here. */
const val LOGO_SETTING_ID = "settings.powerondistype"

@Composable
private fun SettingRow(setting: Setting, onChangeLogo: (() -> Unit)?, apply: (() -> Unit) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(setting.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (setting.doc.isNotBlank()) {
                Text(setting.doc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.width(280.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (setting) {
                is BoolSetting -> Switch(checked = setting.value, onCheckedChange = { v -> apply { setting.set(v) } })
                is ChoiceSetting -> ChoiceControl(setting) { i -> apply { setting.set(i) } }
                is TextSetting -> TextControl(setting) { t -> apply { setting.set(t) } }
            }
            if (onChangeLogo != null) {
                TextButton(onClick = onChangeLogo) { Text("Alterar logo…") }
            }
        }
    }
}

@Composable
private fun ChoiceControl(setting: ChoiceSetting, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) {
            Text(setting.current ?: "(valor desconhecido)")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            setting.options.forEachIndexed { i, opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { open = false; if (i != setting.index) onPick(i) })
            }
        }
    }
}

@Composable
private fun TextControl(setting: TextSetting, onCommit: (String) -> Unit) {
    var text by remember(setting.value) { mutableStateOf(setting.value) }
    val changed = text != setting.value
    OutlinedTextField(
        value = text,
        onValueChange = { if (it.length <= setting.maxLength) text = it },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (changed) onCommit(text) }),
        trailingIcon = if (changed) {
            { TextButton(onClick = { onCommit(text) }) { Text("Salvar") } }
        } else null,
        modifier = Modifier.fillMaxWidth(),
    )
}
