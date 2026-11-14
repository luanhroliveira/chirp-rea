package chirprea.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import chirprea.app.serial.JSerialCommProvider
import chirprea.app.ui.ChirpTheme
import chirprea.core.drivers.baofeng.BaofengLogo
import chirprea.core.drivers.baofeng.LogoSpec
import chirprea.core.drivers.baofeng.SupportsLogo
import chirprea.core.radio.CloneStatus
import chirprea.core.radio.Directory
import chirprea.core.radio.PortInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.image.BufferedImage
import java.io.File

/** Picks a picture (gallery or file), renders it for the radio's logo size and uploads it. */
class LogoController(private val state: AppState) {
    var spec by mutableStateOf<LogoSpec?>(null)

    /** Model picker; editable only when opened from the Rádio menu (no radio document needed). */
    var standalone by mutableStateOf(false)
    var modelLabel by mutableStateOf<String?>(null)

    var gallery by mutableStateOf(listOf<LogoEntry>())
    var selected by mutableStateOf<LogoEntry?>(null)

    /** Original (unprocessed) picture currently chosen. */
    var source by mutableStateOf<BufferedImage?>(null)
    var sourceName by mutableStateOf("")

    /** Fit by default so nothing of the logo is cropped (UV-21 Pro's screen is portrait). */
    var fit by mutableStateOf(LogoFit.Fit)
    var ports by mutableStateOf(listOf<PortInfo>())
    var port by mutableStateOf<String?>(null)
    var progress by mutableStateOf<CloneStatus?>(null)
    var running by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Models whose driver supports a custom logo, including aliases like "Baofeng UV-21 Pro". */
    val models: List<Pair<String, LogoSpec>>
        get() = Directory.choices.mapNotNull { c -> (c.driver.factory() as? SupportsLogo)?.let { c.label to it.logoSpec } }

    /** Open from the Rádio menu without a downloaded radio: the user picks the model. */
    fun openStandalone() {
        val all = models
        val pick = all.firstOrNull { it.first == modelLabel }
            ?: all.firstOrNull { it.first.startsWith("Baofeng UV-21 Pro (") }
            ?: all.firstOrNull()
            ?: run { state.status = "Nenhum rádio com logo configurável"; return }
        standalone = true
        selectModel(pick.first)
        reset()
    }

    /** Open for the radio in [doc] (button in Configurações › Tela ao ligar). */
    fun open(doc: RadioDocument) {
        val radio = doc.radio as? SupportsLogo ?: run {
            state.status = "Este rádio não tem logo configurável"
            return
        }
        standalone = false
        modelLabel = "${doc.radio.vendor} ${doc.radio.model}"
        spec = radio.logoSpec
        reset()
    }

    fun selectModel(label: String) {
        val m = models.firstOrNull { it.first == label } ?: return
        modelLabel = m.first
        spec = m.second
        done = false
    }

    private fun reset() {
        error = null; progress = null; done = false
        refreshGallery()
        scope.launch {
            ports = JSerialCommProvider.list()
            if (ports.none { it.name == port }) port = ports.firstOrNull()?.name
        }
    }

    fun refreshGallery() {
        scope.launch { gallery = withContext(Dispatchers.IO) { LogoLibrary.entries() } }
    }

    fun openGalleryFolder() {
        runCatching { Desktop.getDesktop().open(LogoLibrary.folder) }
            .onFailure { error = "Não foi possível abrir a pasta de imagens" }
    }

    fun close() { if (!running) spec = null }

    fun choose(entry: LogoEntry) {
        selected = entry
        done = false
        scope.launch {
            val img = withContext(Dispatchers.IO) { LogoLibrary.original(entry) }
            if (img == null) error = "Não foi possível ler ${entry.name}"
            else { source = img; sourceName = entry.title; error = null }
        }
    }

    fun pickFile(frame: java.awt.Frame?) {
        val d = FileDialog(frame, "Escolher imagem da logo", FileDialog.LOAD)
        d.setFilenameFilter { _, n -> n.substringAfterLast('.').lowercase() in LogoLibrary.EXTENSIONS }
        d.isVisible = true
        val f = d.file?.let { File(d.directory, it) } ?: return
        val img = LogoProcessing.read(f) ?: run { error = "Não foi possível ler a imagem ${f.name}"; return }
        selected = null
        source = img; sourceName = f.name; error = null; done = false
    }

    fun upload() {
        val s = spec ?: return
        val src = source ?: return
        val name = port ?: return
        // Rendered now, for the selected model: nothing is cached on disk.
        val data = LogoProcessing.rgb565(src, s, fit)
        running = true; error = null; done = false
        scope.launch {
            val serial = runCatching { JSerialCommProvider.open(name, BaofengLogo.BAUD_RATE) }
                .getOrElse { running = false; error = it.message; return@launch }
            try {
                BaofengLogo.upload(serial, s, data) { progress = it }
                done = true
                state.status = "Logo '$sourceName' enviada para o rádio em $name"
            } catch (t: Throwable) {
                error = t.message ?: t::class.simpleName
                state.status = "Falha ao enviar a logo: $error"
            } finally {
                runCatching { serial.close() }
                running = false
            }
        }
    }
}

@Composable
fun LogoDialog(c: LogoController, frame: () -> java.awt.Frame?) {
    val spec = c.spec ?: return
    DialogWindow(
        onCloseRequest = c::close,
        title = "Logo da tela ao ligar",
        state = rememberDialogState(size = DpSize(1040.dp, 760.dp)),
        icon = AppIcon.painter,
    ) {
        ChirpTheme(dark = isSystemInDarkTheme()) {
            Surface(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxSize()) {
                    Gallery(c, spec, Modifier.weight(1f).fillMaxHeight())
                    VerticalDivider()
                    Controls(c, spec, frame, Modifier.width(420.dp).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun Gallery(c: LogoController, spec: LogoSpec, modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Galeria (${c.gallery.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = c::refreshGallery) { Text("Atualizar") }
            TextButton(onClick = c::openGalleryFolder) { Text("Abrir pasta") }
        }
        Text(
            "Miniaturas já processadas para ${spec.width} × ${spec.height} (${c.fit.label.substringBefore(" (")}).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyVerticalGrid(GridCells.Adaptive(minSize = 120.dp), Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (source in LogoSource.entries) {
                val group = c.gallery.filter { it.source == source }
                item(key = "header-$source", span = { GridItemSpan(maxLineSpan) }) {
                    Text("${source.label} (${group.size})", style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 6.dp))
                }
                if (group.isEmpty()) {
                    item(key = "empty-$source", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            if (source == LogoSource.User) "Nenhuma imagem. Use \"Abrir pasta\" para adicionar PNG, JPG, JFIF ou BMP."
                            else "Nenhuma logo incluída nesta versão do app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(group, key = { it.key }) { entry ->
                    Thumb(entry, spec, c.fit, selected = entry == c.selected, enabled = !c.running) { c.choose(entry) }
                }
            }
        }
    }
}

@Composable
private fun Thumb(entry: LogoEntry, spec: LogoSpec, fit: LogoFit, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    // Rendered off the UI thread for the current model/fit; recomputed when either changes.
    val bitmap by produceState<ImageBitmap?>(null, entry, spec, fit) {
        value = withContext(Dispatchers.Default) { LogoLibrary.preview(entry, spec, fit) }
    }
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .border(if (selected) 3.dp else 1.dp, border)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(spec.width.toFloat() / spec.height).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val b = bitmap
            if (b != null) Image(b, entry.title, Modifier.fillMaxSize(), filterQuality = FilterQuality.Low)
            else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Text(entry.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Controls(c: LogoController, spec: LogoSpec, frame: () -> java.awt.Frame?, modifier: Modifier) {
    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Pré-visualização", style = MaterialTheme.typography.titleMedium)
        Text(
            "Exatamente como o rádio vai mostrar: ${spec.width} × ${spec.height}, cores RGB565.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Fixed 2x scale of the real logo size; the radio's pixel grid stays visible (no smoothing).
        val scale = 2
        Box(
            Modifier.size((spec.width * scale).dp, (spec.height * scale).dp).border(1.dp, MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center,
        ) {
            val preview by produceState<ImageBitmap?>(null, c.source, spec, c.fit) {
                val src = c.source
                value = if (src == null) null else withContext(Dispatchers.Default) { LogoProcessing.preview(src, spec, c.fit) }
            }
            val p = preview
            if (p != null) Image(p, "Prévia da logo", Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
            else Text("Escolha uma imagem na galeria", style = MaterialTheme.typography.bodySmall)
        }
        if (c.sourceName.isNotEmpty()) Text(c.sourceName, style = MaterialTheme.typography.bodySmall, maxLines = 2)

        OutlinedButton(onClick = { c.pickFile(frame()) }, enabled = !c.running) { Text("Outra imagem do computador…") }
        Picker("Modelo", c.modelLabel ?: "", c.models.map { it.first }, enabled = c.standalone && !c.running) { c.selectModel(it) }
        Picker("Enquadramento", c.fit.label, LogoFit.entries.map { it.label }, !c.running) { l ->
            c.fit = LogoFit.entries.first { it.label == l }
        }
        Picker(
            "Porta",
            c.ports.firstOrNull { it.name == c.port }?.label ?: "(nenhuma porta)",
            c.ports.map { it.label },
            !c.running,
        ) { l -> c.port = c.ports.first { it.label == l }.name }

        Text(
            "A logo é gravada direto na memória flash do rádio (não faz parte da imagem .img). " +
                "Não desconecte o cabo nem desligue o rádio durante o envio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        c.progress?.let { p ->
            LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
            Text("${p.message} ${p.current}/${p.max}", style = MaterialTheme.typography.bodySmall)
        }
        c.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (c.done) Text("Logo enviada. Desligue e ligue o rádio para ver.", color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = c::close, enabled = !c.running) { Text("Fechar") }
            Box(Modifier.padding(start = 8.dp)) {
                Button(onClick = c::upload, enabled = !c.running && c.source != null && c.port != null) {
                    Text("Enviar logo")
                }
            }
        }
    }
    LaunchedEffect(Unit) { if (c.gallery.isEmpty()) c.refreshGallery() }
}

@Composable
private fun Picker(label: String, value: String, options: List<String>, enabled: Boolean, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { open = true }, enabled = enabled && options.isNotEmpty()) {
                Text(value, maxLines = 2)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(o) }) }
            }
        }
    }
}
