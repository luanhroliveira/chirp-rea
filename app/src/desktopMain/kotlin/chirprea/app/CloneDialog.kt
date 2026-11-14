package chirprea.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import chirprea.app.serial.JSerialCommProvider
import chirprea.app.ui.ChirpTheme
import chirprea.core.radio.CloneModeRadio
import chirprea.core.radio.CloneStatus
import chirprea.core.radio.Directory
import chirprea.core.radio.DriverInfo
import chirprea.core.radio.ModelChoice
import chirprea.core.radio.PortInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

enum class CloneDirection { Download, Upload }

/** State of the download/upload dialog. */
class CloneController(private val state: AppState) {
    var direction by mutableStateOf<CloneDirection?>(null)
    var ports by mutableStateOf(listOf<PortInfo>())
    var port by mutableStateOf<String?>(null)
    var driver by mutableStateOf<DriverInfo?>(null)
    var modelLabel by mutableStateOf<String?>(null)
    var progress by mutableStateOf<CloneStatus?>(null)
    var running by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun open(dir: CloneDirection) {
        if (dir == CloneDirection.Upload) {
            val radio = state.current?.radio
            if (radio !is CloneModeRadio) {
                state.status = "Abra uma imagem de rádio (.img) ou baixe do rádio antes de enviar"
                return
            }
            driver = radio.info
            modelLabel = radio.info.displayName
        } else if (Directory.choices.none { it.label == modelLabel }) {
            val default = Directory.choices.firstOrNull { it.label.startsWith("Baofeng UV-21 Pro (") }
                ?: Directory.choices.firstOrNull()
            pick(default)
        }
        error = null
        progress = null
        direction = dir
        refreshPorts()
    }

    fun pick(choice: ModelChoice?) {
        driver = choice?.driver
        modelLabel = choice?.label
    }

    fun refreshPorts() = scope.launch {
        ports = JSerialCommProvider.list()
        // List is sorted with programming cables first, so the default is the USB cable.
        if (ports.none { it.name == port }) port = ports.firstOrNull()?.name
    }

    fun close() { if (!running) direction = null }

    fun start() {
        val dir = direction ?: return
        val portName = port ?: return
        val info = driver ?: return
        running = true
        error = null
        scope.launch {
            val serial = runCatching { JSerialCommProvider.open(portName, info.baudRate) }
                .getOrElse { fail(it); return@launch }
            try {
                when (dir) {
                    CloneDirection.Download -> {
                        val radio = info.factory() as CloneModeRadio
                        radio.syncIn(serial) { progress = it }
                        state.open(RadioDocument(radio, null, "${info.displayName} (rádio)").also { it.dirty = true })
                        state.status = "Download concluído de ${info.displayName} em $portName"
                    }
                    CloneDirection.Upload -> {
                        val radio = state.current!!.radio as CloneModeRadio
                        radio.syncOut(serial) { progress = it }
                        state.status = "Upload concluído para ${info.displayName} em $portName"
                    }
                }
                running = false
                direction = null
            } catch (t: Throwable) {
                fail(t)
            } finally {
                runCatching { serial.close() }
            }
        }
    }

    private fun fail(t: Throwable) {
        running = false
        error = t.message ?: t::class.simpleName
        state.status = "Falha na comunicação: $error"
    }
}

@Composable
fun CloneDialog(c: CloneController) {
    val dir = c.direction ?: return
    val title = if (dir == CloneDirection.Download) "Baixar do rádio" else "Enviar para o rádio"
    DialogWindow(
        onCloseRequest = c::close,
        title = title,
        state = rememberDialogState(size = DpSize(480.dp, 420.dp)),
        resizable = false,
        icon = AppIcon.painter,
    ) {
        ChirpTheme(dark = isSystemInDarkTheme()) { CloneDialogContent(c, dir, title) }
    }
}

@Composable
private fun CloneDialogContent(c: CloneController, dir: CloneDirection, title: String) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(
                    "1. Desligue o rádio  2. Conecte o cabo  3. Ligue o rádio  4. Clique em " +
                        (if (dir == CloneDirection.Download) "Baixar" else "Enviar") + ".",
                    style = MaterialTheme.typography.bodySmall,
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Picker(
                        "Porta",
                        c.ports.firstOrNull { it.name == c.port }?.label ?: "(nenhuma porta encontrada)",
                        c.ports.map { it.label },
                        enabled = !c.running,
                    ) { label -> c.port = c.ports.first { it.label == label }.name }
                    TextButton(onClick = { c.refreshPorts() }, enabled = !c.running) { Text("Atualizar") }
                }
                Picker(
                    "Modelo", c.modelLabel ?: "", Directory.choices.map { it.label },
                    enabled = !c.running && dir == CloneDirection.Download,
                ) { label -> c.pick(Directory.choices.first { it.label == label }) }

                c.progress?.let { p ->
                    LinearProgressIndicator(progress = { p.fraction }, modifier = Modifier.fillMaxWidth())
                    Text("${p.message} ${p.current}/${p.max}", style = MaterialTheme.typography.bodySmall)
                }
                c.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = c::close, enabled = !c.running) { Text("Cancelar") }
                    Box(Modifier.padding(start = 8.dp)) {
                        Button(onClick = c::start, enabled = !c.running && c.port != null && c.driver != null) {
                            Text(if (dir == CloneDirection.Download) "Baixar" else "Enviar")
                        }
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
            OutlinedButton(onClick = { open = true }, enabled = enabled && options.isNotEmpty()) { Text(value) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(opt) }, onClick = { open = false; onPick(opt) })
                }
            }
        }
    }
}
