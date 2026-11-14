package chirprea.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import chirprea.app.AppState
import chirprea.app.RadioDocument

@Composable
fun ChirpTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), content = content)
}

const val DESKTOP_WELCOME =
    "Arquivo › Novo para começar, Arquivo › Abrir para carregar um .csv ou .img,\n" +
        "Rádio › Baixar do rádio para ler a memória de um rádio,\n" +
        "ou Rádio › Alterar logo do rádio (Alt+L) para trocar só a logo."

/** Main window content: document tabs, the memory grid and a status bar. */
@Composable
fun ChirpApp(
    state: AppState,
    onChangeLogo: ((RadioDocument) -> Unit)? = null,
    appIcon: androidx.compose.ui.graphics.painter.Painter? = null,
    welcomeText: String = DESKTOP_WELCOME,
) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            if (state.documents.isEmpty()) {
                Welcome(Modifier.weight(1f), appIcon, welcomeText)
            } else {
                Tabs(state)
                HorizontalDivider()
                state.current?.let { doc ->
                    val tabs = doc.tabs()
                    if (tabs.size > 1) {
                        SubTabs(tabs, doc.tab) { doc.tab = it }
                        HorizontalDivider()
                    }
                    val onStatus: (String) -> Unit = { state.status = it }
                    Box(Modifier.weight(1f)) {
                        when (doc.tab.takeIf { it in tabs } ?: RadioTab.Memories) {
                            RadioTab.Memories -> MemoryGrid(doc, onStatus = onStatus)
                            RadioTab.Settings -> SettingsView(
                                doc,
                                onStatus,
                                onChangeLogo?.takeIf { doc.radio is chirprea.core.drivers.baofeng.SupportsLogo }?.let { cb -> { cb(doc) } },
                            )
                            RadioTab.Banks -> BanksView(doc, onStatus)
                            RadioTab.Info -> InfoView(doc)
                            RadioTab.Browser -> BrowserView(doc)
                        }
                    }
                }
            }
            HorizontalDivider()
            Text(
                state.status,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Tabs(state: AppState) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        state.documents.forEachIndexed { i, doc ->
            val selected = i == state.selected
            Row(
                Modifier
                    .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                    .clickable { state.selected = i }
                    .padding(start = 12.dp, end = 2.dp)
                    .height(36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (if (doc.dirty) "• " else "") + doc.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                IconButton(onClick = { state.close(i) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar aba", Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun SubTabs(tabs: List<RadioTab>, selected: RadioTab, onSelect: (RadioTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tabs.forEach { t ->
            val isSel = t == selected
            Box(
                Modifier
                    .clickable { onSelect(t) }
                    .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    t.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Welcome(modifier: Modifier, appIcon: androidx.compose.ui.graphics.painter.Painter?, text: String) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (appIcon != null) {
                androidx.compose.foundation.Image(appIcon, contentDescription = "CHIRP-REA", Modifier.size(112.dp))
            } else {
                Icon(Icons.Default.Radio, contentDescription = null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text("CHIRP-REA", style = MaterialTheme.typography.headlineMedium)
            Text(
                text,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(1.dp))
        }
    }
}
