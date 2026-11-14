package chirprea.app.web

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import chirprea.app.AppState
import chirprea.app.LogoFit
import chirprea.app.RadioDocument
import chirprea.core.drivers.baofeng.BaofengLogo
import chirprea.core.drivers.baofeng.LogoSpec
import chirprea.core.drivers.baofeng.SupportsLogo
import chirprea.core.radio.CloneStatus
import chirprea.core.radio.Directory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image as SkImage
import org.jetbrains.skia.ImageInfo

/** A picture of the bundled gallery (served next to the page under logos/). */
data class WebLogo(val path: String) {
    val title: String get() = path.substringAfterLast('/').substringBeforeLast('.')
}

/** State of the web logo dialog: picture, model, framing, port and upload progress. */
class WebLogoState(private val state: AppState, private val scope: CoroutineScope) {
    var open by mutableStateOf(false)
    var spec by mutableStateOf<LogoSpec?>(null)
    var modelLabel by mutableStateOf<String?>(null)
    var standalone by mutableStateOf(true)
    var gallery by mutableStateOf<List<WebLogo>>(emptyList())
    var selected by mutableStateOf<WebLogo?>(null)
    var source by mutableStateOf<ByteArray?>(null)
    var sourceName by mutableStateOf("")
    var fit by mutableStateOf(LogoFit.Fit)
    var progress by mutableStateOf<CloneStatus?>(null)
    var running by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var done by mutableStateOf(false)

    private val bytes = HashMap<String, ByteArray>()
    private val previews = HashMap<String, ImageBitmap>()

    val models: List<Pair<String, LogoSpec>>
        get() = Directory.choices.mapNotNull { c -> (c.driver.factory() as? SupportsLogo)?.let { c.label to it.logoSpec } }

    /** From the Rádio menu: no downloaded radio needed, the user picks the model. */
    fun openStandalone() {
        val m = models.firstOrNull { it.first.startsWith("Baofeng UV-21 Pro (") } ?: models.firstOrNull() ?: return
        standalone = true
        modelLabel = m.first
        spec = m.second
        show()
    }

    /** From Configurações › Tela ao ligar: model fixed to the open radio. */
    fun openFor(doc: RadioDocument) {
        val radio = doc.radio as? SupportsLogo ?: return
        standalone = false
        modelLabel = "${doc.radio.vendor} ${doc.radio.model}"
        spec = radio.logoSpec
        show()
    }

    private fun show() {
        error = null; progress = null; done = false
        open = true
        if (gallery.isEmpty()) scope.launch {
            gallery = runCatching {
                listOf("", "terceiros/").flatMap { folder ->
                    fetchText("logos/${folder}index.txt").lines().map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .map { WebLogo("logos/$folder$it") }
                }
            }.getOrElse { error = "Não foi possível carregar a galeria: ${it.message}"; emptyList() }
        }
    }

    fun selectModel(label: String) {
        val m = models.firstOrNull { it.first == label } ?: return
        modelLabel = m.first; spec = m.second; done = false
    }

    fun choose(logo: WebLogo) = scope.launch {
        runCatching { imageBytes(logo) }
            .onSuccess { selected = logo; source = it; sourceName = logo.title; error = null; done = false }
            .onFailure { error = "Não foi possível abrir ${logo.title}: ${it.message}" }
    }

    fun pickFromComputer() = scope.launch {
        val (name, data) = pickFile("image/*") ?: return@launch
        selected = null; source = data; sourceName = name; error = null; done = false
    }

    suspend fun imageBytes(logo: WebLogo): ByteArray = bytes.getOrPut(logo.path) { fetchBytes(logo.path) }

    /** Picture rendered for the radio and reduced to RGB565, exactly as the radio will show it. */
    suspend fun preview(data: ByteArray, key: String, s: LogoSpec, f: LogoFit): ImageBitmap =
        previews.getOrPut("$key|${s.width}x${s.height}|$f") {
            val raw = BaofengLogo.toRgb565(renderImage(data, s.width, s.height, f.ordinal), s.width, s.height)
            argbToBitmap(BaofengLogo.fromRgb565(raw), s.width, s.height)
        }

    /** Must be started from the button click: the browser only shows its port chooser during a user gesture. */
    fun upload() {
        val s = spec ?: return
        val data = source ?: return
        running = true; error = null; done = false
        scope.launch {
            try {
                // Ask for the port first, while the click still counts as a user gesture.
                val port = WebSerialPort.request(BaofengLogo.BAUD_RATE)
                try {
                    val pixels = renderImage(data, s.width, s.height, fit.ordinal)
                    val raw = BaofengLogo.toRgb565(pixels, s.width, s.height)
                    BaofengLogo.upload(port, s, raw) { progress = it }
                    done = true
                    state.status = "Logo '$sourceName' enviada para o rádio"
                } finally {
                    runCatching { port.close() }
                }
            } catch (t: Throwable) {
                error = t.message ?: t::class.simpleName
                state.status = "Falha ao enviar a logo: $error"
            } finally {
                running = false
            }
        }
    }
}

private fun argbToBitmap(argb: IntArray, w: Int, h: Int): ImageBitmap {
    val rgba = ByteArray(w * h * 4)
    for (i in argb.indices) {
        val p = argb[i]
        rgba[4 * i] = (p shr 16).toByte()
        rgba[4 * i + 1] = (p shr 8).toByte()
        rgba[4 * i + 2] = p.toByte()
        rgba[4 * i + 3] = 0xFF.toByte()
    }
    val info = ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    return SkImage.makeRaster(info, rgba, w * 4).toComposeImageBitmap()
}

@Composable
fun WebLogoDialog(c: WebLogoState) {
    val spec = c.spec ?: return
    Dialog(
        onDismissRequest = { if (!c.running) c.open = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f).height(640.dp),
        ) {
            Row(Modifier.fillMaxSize()) {
                Gallery(c, spec, Modifier.weight(1f).fillMaxHeight())
                VerticalDivider()
                Controls(c, spec, Modifier.width(380.dp).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun Gallery(c: WebLogoState, spec: LogoSpec, modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Galeria (${c.gallery.size})", style = MaterialTheme.typography.titleMedium)
        Text(
            "Miniaturas já processadas para ${spec.width} × ${spec.height} (${c.fit.label.substringBefore(" (")}).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyVerticalGrid(
            GridCells.Adaptive(minSize = 110.dp),
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val own = c.gallery.filter { !it.path.contains("/terceiros/") }
            val others = c.gallery.filter { it.path.contains("/terceiros/") }
            for ((title, group) in listOf("Logos do CHIRP-REA" to own, "Imagens de terceiros" to others)) {
                if (group.isEmpty()) continue
                item(key = "h-$title", span = { GridItemSpan(maxLineSpan) }) {
                    Text("$title (${group.size})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                }
                items(group, key = { it.path }) { logo ->
                    Thumb(c, logo, spec, selected = logo == c.selected)
                }
            }
        }
    }
}

@Composable
private fun Thumb(c: WebLogoState, logo: WebLogo, spec: LogoSpec, selected: Boolean) {
    val bitmap by produceState<ImageBitmap?>(null, logo, spec, c.fit) {
        value = runCatching { c.preview(c.imageBytes(logo), logo.path, spec, c.fit) }.getOrNull()
    }
    Column(
        Modifier
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            .clickable(enabled = !c.running) { c.choose(logo) }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(spec.width.toFloat() / spec.height).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val b = bitmap
            if (b != null) Image(b, logo.title, Modifier.fillMaxSize(), filterQuality = FilterQuality.Low)
            else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        Text(logo.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Controls(c: WebLogoState, spec: LogoSpec, modifier: Modifier) {
    Column(modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Pré-visualização", style = MaterialTheme.typography.titleMedium)
        Text(
            "Exatamente como o rádio vai mostrar: ${spec.width} × ${spec.height}, cores RGB565.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            Modifier.size((spec.width * 2).dp, (spec.height * 2).dp).border(1.dp, MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center,
        ) {
            val src = c.source
            val preview by produceState<ImageBitmap?>(null, src, spec, c.fit) {
                value = if (src == null) null else runCatching { c.preview(src, c.sourceName + src.size, spec, c.fit) }.getOrNull()
            }
            val p = preview
            if (p != null) Image(p, "Prévia da logo", Modifier.fillMaxSize(), filterQuality = FilterQuality.None)
            else Text("Escolha uma imagem na galeria", style = MaterialTheme.typography.bodySmall)
        }
        if (c.sourceName.isNotEmpty()) Text(c.sourceName, style = MaterialTheme.typography.bodySmall, maxLines = 2)
        OutlinedButton(onClick = { c.pickFromComputer() }, enabled = !c.running) { Text("Outra imagem do computador…") }
        Picker("Modelo", c.modelLabel ?: "", c.models.map { it.first }, c.standalone && !c.running) { c.selectModel(it) }
        Picker("Enquadramento", c.fit.label, LogoFit.entries.map { it.label }, !c.running) { l -> c.fit = LogoFit.entries.first { it.label == l } }
        Text(
            "A logo é gravada direto na memória flash do rádio. Ao enviar, escolha a porta do cabo na janela do " +
                "navegador e não desconecte o cabo durante o envio.",
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
            OutlinedButton(onClick = { c.open = false }, enabled = !c.running) { Text("Fechar") }
            Box(Modifier.padding(start = 8.dp)) {
                Button(onClick = { c.upload() }, enabled = !c.running && c.source != null && hasWebSerial()) {
                    Text("Escolher porta e enviar")
                }
            }
        }
    }
}

@Composable
private fun Picker(label: String, value: String, options: List<String>, enabled: Boolean, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { open = true }, enabled = enabled && options.isNotEmpty()) { Text(value, maxLines = 2) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o -> DropdownMenuItem(text = { Text(o) }, onClick = { open = false; onPick(o) }) }
            }
        }
    }
}
