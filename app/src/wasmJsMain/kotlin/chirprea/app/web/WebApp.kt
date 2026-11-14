package chirprea.app.web

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import chirprea.app.AppState
import chirprea.app.RadioDocument
import chirprea.app.ui.ChirpApp
import chirprea.core.csv.CsvRadio
import chirprea.core.radio.CloneModeRadio
import chirprea.core.radio.CloneStatus
import chirprea.core.radio.Directory
import chirprea.core.radio.ImageFile
import chirprea.core.radio.ModelChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

const val REPO_URL = "https://github.com/luanhroliveira/chirp-rea"

/** Web counterpart of the desktop window: in-page menu bar plus the shared ChirpApp UI. */
@Composable
fun WebApp() {
    val state = remember { AppState() }
    val scope = rememberCoroutineScope()
    val actions = remember { WebActions(state, scope) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        MenuBar(state, actions)
        HorizontalDivider()
        if (!hasWebSerial()) {
            Text(
                "Este navegador não tem Web Serial: dá para abrir, editar e salvar arquivos, mas não conversar com o rádio. " +
                    "Use o Google Chrome ou o Microsoft Edge no computador.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(8.dp),
            )
        }
        Box(Modifier.weight(1f)) {
            ChirpApp(
                state,
                onChangeLogo = { actions.logo.openFor(it) },
                welcomeText = "Arquivo › Novo para começar, Arquivo › Abrir para carregar um .csv ou .img,\n" +
                    "Rádio › Baixar do rádio para ler a memória de um rádio pelo cabo USB,\n" +
                    "ou Rádio › Alterar logo do rádio para trocar só a logo.\n" +
                    "Nada é enviado para servidores: tudo roda no seu navegador.",
            )
        }
    }
    actions.clone?.let { CloneWebDialog(it) { actions.clone = null } }
    if (actions.showStock) StockDialog(actions) { actions.showStock = false }
    if (actions.showAbout) AboutDialog { actions.showAbout = false }
    if (actions.logo.open) WebLogoDialog(actions.logo)
}

class WebActions(private val state: AppState, private val scope: CoroutineScope) {
    var clone by mutableStateOf<CloneState?>(null)
    var showStock by mutableStateOf(false)
    var showAbout by mutableStateOf(false)
    var stockNames by mutableStateOf<List<String>>(emptyList())
    val logo = WebLogoState(state, scope)

    fun newCsv() = state.open(RadioDocument(CsvRadio(), null, "Sem título.csv"))

    fun open() = scope.launch {
        val (name, bytes) = pickFile(".csv,.img") ?: return@launch
        runCatching { openBytes(name, bytes) }
            .onFailure { state.status = "Não foi possível abrir $name: ${it.message}" }
    }

    private fun openBytes(name: String, bytes: ByteArray) {
        val doc = when (name.substringAfterLast('.').lowercase()) {
            "csv" -> {
                val radio = CsvRadio.fromText(bytes.decodeToString())
                state.status = if (radio.errors.isEmpty()) "Aberto: $name" else "${radio.errors.size} aviso(s): ${radio.errors.first()}"
                RadioDocument(radio, null, name)
            }
            "img" -> {
                val rclass = ImageFile.rclass(bytes) ?: error("imagem sem metadados do CHIRP")
                val info = Directory.byChirpClass(rclass) ?: error("o driver '$rclass' ainda não foi portado para o CHIRP-REA")
                val radio = info.factory() as CloneModeRadio
                radio.loadImage(bytes)
                state.status = "Aberto: $name"
                RadioDocument(radio, null, name)
            }
            else -> error("formato não suportado")
        }
        state.open(doc)
    }

    fun openStockList() = scope.launch {
        if (stockNames.isEmpty()) {
            stockNames = runCatching { fetchText("stock_configs/index.txt").lines().map { it.trim() }.filter { it.isNotEmpty() } }
                .getOrElse { state.status = "Não foi possível carregar a lista: ${it.message}"; emptyList() }
        }
        showStock = true
    }

    fun openStock(name: String) = scope.launch {
        showStock = false
        runCatching { fetchText("stock_configs/" + name) }
            .onSuccess { state.open(RadioDocument(CsvRadio.fromText(it), null, name)); state.status = "Aberto: $name" }
            .onFailure { state.status = "Não foi possível abrir $name: ${it.message}" }
    }

    fun save() {
        val doc = state.current ?: return
        val radio = doc.radio
        val (bytes, ext, mime) = when (radio) {
            is CsvRadio -> Triple(radio.save().encodeToByteArray(), "csv", "text/csv")
            is CloneModeRadio -> Triple(radio.toImage(), "img", "application/octet-stream")
            else -> return
        }
        val name = doc.title.let { if (it.endsWith(".$ext", ignoreCase = true)) it else it.substringBeforeLast(" (rádio)") + ".$ext" }
        download(name, bytes, mime)
        doc.dirty = false
        state.status = "Baixado: $name (veja a pasta de downloads do navegador)"
    }

    fun startDownload() {
        val default = Directory.choices.firstOrNull { it.label.startsWith("Baofeng UV-21 Pro (") } ?: Directory.choices.firstOrNull()
        clone = CloneState(state, scope, upload = false).also { it.choice = default }
    }

    fun startUpload() {
        val radio = state.current?.radio as? CloneModeRadio ?: run {
            state.status = "Abra uma imagem de rádio (.img) ou baixe do rádio antes de enviar"
            return
        }
        clone = CloneState(state, scope, upload = true).also {
            it.choice = ModelChoice(radio.info.displayName, radio.info)
        }
    }
}

/** State of the download/upload dialog on the web. */
class CloneState(private val state: AppState, private val scope: CoroutineScope, val upload: Boolean) {
    var choice by mutableStateOf<ModelChoice?>(null)
    var progress by mutableStateOf<CloneStatus?>(null)
    var running by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf(false)

    /** Called from the button click: the browser only shows its port chooser during a user gesture. */
    fun start(onFinished: () -> Unit) {
        val info = choice?.driver ?: return
        running = true; error = null
        scope.launch {
            val port = runCatching { WebSerialPort.request(info.baudRate) }
                .getOrElse { error = it.message; running = false; return@launch }
            try {
                if (upload) {
                    val radio = state.current!!.radio as CloneModeRadio
                    radio.syncOut(port) { progress = it }
                    state.status = "Envio concluído para ${info.displayName}"
                } else {
                    val radio = info.factory() as CloneModeRadio
                    radio.syncIn(port) { progress = it }
                    state.open(RadioDocument(radio, null, "${info.displayName} (rádio)").also { it.dirty = true })
                    state.status = "Download concluído de ${info.displayName}"
                }
                done = true
                onFinished()
            } catch (t: Throwable) {
                error = t.message ?: t::class.simpleName
                state.status = "Falha na comunicação: $error"
            } finally {
                runCatching { port.close() }
                running = false
            }
        }
    }
}

@Composable
private fun MenuBar(state: AppState, a: WebActions) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "CHIRP-REA",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 12.dp),
        )
        Menu("Arquivo") { close ->
            Item("Novo") { close(); a.newCsv() }
            Item("Abrir .csv ou .img…") { close(); a.open() }
            Item("Abrir configuração pronta…") { close(); a.openStockList() }
            HorizontalDivider()
            Item("Salvar (download)", enabled = state.current != null) { close(); a.save() }
            Item("Fechar aba", enabled = state.current != null) { close(); state.close(state.selected) }
        }
        Menu("Rádio") { close ->
            Item("Baixar do rádio…") { close(); a.startDownload() }
            Item("Enviar para o rádio…", enabled = state.current?.radio is CloneModeRadio) { close(); a.startUpload() }
            HorizontalDivider()
            Item("Alterar logo do rádio…") { close(); a.logo.openStandalone() }
        }
        Menu("Ajuda") { close ->
            Item("Documentação") { close(); openInNewTab("$REPO_URL/blob/main/docs/README.md") }
            Item("Baixar a versão para Windows") { close(); openInNewTab("$REPO_URL/releases/latest") }
            Item("Sobre o CHIRP-REA") { close(); a.showAbout = true }
        }
    }
}

private class MenuScope(val close: () -> Unit)

@Composable
private fun Menu(title: String, content: @Composable MenuScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(title) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuScope { open = false }.content { open = false }
        }
    }
}

@Composable
private fun MenuScope.Item(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, onClick = onClick, enabled = enabled)
}

@Composable
private fun DialogFrame(width: Int = 460, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp, modifier = Modifier.width(width.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun CloneWebDialog(c: CloneState, onClose: () -> Unit) {
    Dialog(onDismissRequest = { if (!c.running) onClose() }) {
        DialogFrame {
            Text(if (c.upload) "Enviar para o rádio" else "Baixar do rádio", style = MaterialTheme.typography.titleLarge)
            Text(
                "1. Desligue o rádio  2. Encaixe o cabo até o fim  3. Ligue o rádio  4. Clique no botão abaixo e escolha " +
                    "a porta do cabo (ex.: USB-SERIAL CH340) na janela do navegador.",
                style = MaterialTheme.typography.bodySmall,
            )
            var open by remember { mutableStateOf(false) }
            Text("Modelo", style = MaterialTheme.typography.labelMedium)
            Box {
                OutlinedButton(onClick = { open = true }, enabled = !c.running && !c.upload) { Text(c.choice?.label ?: "") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    Directory.choices.forEach { m ->
                        DropdownMenuItem(text = { Text(m.label) }, onClick = { open = false; c.choice = m })
                    }
                }
            }
            c.progress?.let { p ->
                LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
                Text("${p.message} ${p.current}/${p.max}", style = MaterialTheme.typography.bodySmall)
            }
            c.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onClose, enabled = !c.running) { Text("Cancelar") }
                Box(Modifier.padding(start = 8.dp)) {
                    Button(onClick = { c.start(onClose) }, enabled = !c.running && c.choice != null && hasWebSerial()) {
                        Text(if (c.upload) "Escolher porta e enviar" else "Escolher porta e baixar")
                    }
                }
            }
        }
    }
}

@Composable
private fun StockDialog(a: WebActions, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        DialogFrame(width = 520) {
            Text("Configurações prontas", style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(a.stockNames) { name ->
                    Text(
                        name.removeSuffix(".csv"),
                        modifier = Modifier.fillMaxWidth().clickable { a.openStock(name) }.padding(vertical = 10.dp, horizontal = 4.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("Fechar") }
            }
        }
    }
}

@Composable
private fun AboutDialog(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        DialogFrame {
            Text("CHIRP-REA", style = MaterialTheme.typography.titleLarge)
            Text(
                "Programador de rádios compatível com o CHIRP, escrito em Kotlin + Compose e rodando no navegador " +
                    "via WebAssembly. A comunicação com o rádio usa a Web Serial API (Chrome/Edge no computador).\n\n" +
                    "Licença GPL-3.0. Baseado no CHIRP, de Dan Smith e colaboradores.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { openInNewTab(REPO_URL) }) { Text("Código-fonte") }
                TextButton(onClick = onClose) { Text("Fechar") }
            }
        }
    }
}
