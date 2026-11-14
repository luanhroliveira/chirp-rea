package chirprea.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import chirprea.app.ui.ChirpApp
import chirprea.app.ui.ChirpTheme
import chirprea.core.csv.CsvRadio
import chirprea.core.drivers.Drivers
import chirprea.core.radio.CloneModeRadio
import chirprea.core.radio.Directory
import chirprea.core.radio.ImageFile
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JOptionPane

private object StockConfigs {
    val names: List<String> by lazy {
        javaClass.getResourceAsStream("/stock_configs/index.txt")
            ?.bufferedReader()?.readLines()?.filter { it.isNotBlank() } ?: emptyList()
    }

    fun load(name: String): String =
        javaClass.getResourceAsStream("/stock_configs/$name")!!.readBytes().decodeToString()
}

private class DesktopActions(private val state: AppState, private val frame: () -> Frame?) {

    fun newCsv() = state.open(RadioDocument(CsvRadio(), null, "Sem título.csv"))

    fun open() {
        val file = chooseFile("Abrir", FileDialog.LOAD) ?: return
        runCatching { openFile(file) }.onFailure { error("Não foi possível abrir ${file.name}", it) }
    }

    fun openFile(file: File) {
        var warning: String? = null
        val doc = when (file.extension.lowercase()) {
            "csv" -> {
                val radio = CsvRadio.fromText(file.readText())
                if (radio.errors.isNotEmpty()) warning = "${radio.errors.size} aviso(s): ${radio.errors.first()}"
                RadioDocument(radio, file.path, file.name)
            }
            "img" -> {
                val bytes = file.readBytes()
                val rclass = ImageFile.rclass(bytes) ?: throw IllegalStateException("Imagem sem metadados do CHIRP")
                val info = Directory.byChirpClass(rclass)
                    ?: throw IllegalStateException("O driver '$rclass' ainda não foi portado para o CHIRP-REA")
                val radio = info.factory() as CloneModeRadio
                radio.loadImage(bytes)
                RadioDocument(radio, file.path, file.name)
            }
            else -> throw IllegalArgumentException("Formato não suportado: .${file.extension}")
        }
        state.open(doc)
        state.status = warning ?: "Aberto: ${file.path}"
    }

    fun openStock(name: String) {
        state.open(RadioDocument(CsvRadio.fromText(StockConfigs.load(name)), null, name))
    }

    fun save(saveAs: Boolean = false) {
        val doc = state.current ?: return
        val path = doc.path.takeUnless { saveAs }?.let(::File)
            ?: chooseFile("Salvar como", FileDialog.SAVE, doc.title)?.let(::withExtension)
            ?: return
        runCatching {
            when (val radio = doc.radio) {
                is CsvRadio -> path.writeText(radio.save())
                is CloneModeRadio -> path.writeBytes(radio.toImage())
                else -> throw IllegalStateException("Este tipo de rádio não pode ser salvo em arquivo")
            }
            doc.path = path.path
            doc.title = path.name
            doc.dirty = false
            state.status = "Salvo: ${path.path}"
        }.onFailure { error("Não foi possível salvar", it) }
    }

    private fun withExtension(f: File): File {
        val ext = if (state.current?.radio is CsvRadio) "csv" else "img"
        return if (f.extension.isEmpty()) File(f.path + ".$ext") else f
    }

    fun about() = JOptionPane.showMessageDialog(
        frame(),
        "CHIRP-REA 0.1.0\n\nReescrita em Kotlin + Compose do CHIRP (chirpmyradio.com).\n" +
            "Licença GPL-3.0. Baseado no trabalho de Dan Smith e dos colaboradores do CHIRP.",
        "Sobre", JOptionPane.INFORMATION_MESSAGE,
    )

    private fun chooseFile(title: String, mode: Int, suggested: String? = null): File? {
        val dialog = FileDialog(frame(), title, mode)
        if (suggested != null) dialog.file = suggested
        if (mode == FileDialog.LOAD) dialog.setFilenameFilter { _, name -> name.endsWith(".csv", true) || name.endsWith(".img", true) }
        dialog.isVisible = true
        return dialog.file?.let { File(dialog.directory, it) }
    }

    private fun error(message: String, t: Throwable) {
        state.status = "$message: ${t.message}"
        JOptionPane.showMessageDialog(frame(), "$message\n\n${t.message}", "Erro", JOptionPane.ERROR_MESSAGE)
    }
}

fun main(args: Array<String>) = application {
    remember { Drivers.registerAll() }
    val state = remember { AppState() }
    val windowState = rememberWindowState(width = 1280.dp, height = 760.dp)
    var frameRef: Frame? = null
    val actions = remember { DesktopActions(state) { frameRef } }
    val clone = remember { CloneController(state) }
    val logo = remember { LogoController(state) }
    remember { args.map(::File).filter { it.exists() }.forEach { actions.openFile(it) } }

    Window(onCloseRequest = ::exitApplication, state = windowState, title = "CHIRP-REA", icon = AppIcon.painter) {
        frameRef = window
        MenuBar {
            Menu("Arquivo", mnemonic = 'A') {
                Item("Novo", shortcut = KeyShortcut(Key.N, ctrl = true), onClick = actions::newCsv)
                Item("Abrir…", shortcut = KeyShortcut(Key.O, ctrl = true), onClick = actions::open)
                Menu("Abrir configuração pronta") {
                    StockConfigs.names.forEach { name ->
                        Item(name.removeSuffix(".csv"), onClick = { actions.openStock(name) })
                    }
                }
                Separator()
                Item("Salvar", shortcut = KeyShortcut(Key.S, ctrl = true), enabled = state.current != null) { actions.save() }
                Item("Salvar como…", enabled = state.current != null) { actions.save(saveAs = true) }
                Item("Fechar aba", shortcut = KeyShortcut(Key.W, ctrl = true), enabled = state.current != null) {
                    state.close(state.selected)
                }
                Separator()
                Item("Sair", onClick = ::exitApplication)
            }
            Menu("Rádio", mnemonic = 'R') {
                Item("Baixar do rádio…", shortcut = KeyShortcut(Key.D, alt = true)) { clone.open(CloneDirection.Download) }
                Item(
                    "Enviar para o rádio…",
                    shortcut = KeyShortcut(Key.U, alt = true),
                    enabled = state.current?.radio is CloneModeRadio,
                ) { clone.open(CloneDirection.Upload) }
                Separator()
                Item("Alterar logo do rádio…", shortcut = KeyShortcut(Key.L, alt = true)) { logo.openStandalone() }
            }
            Menu("Ajuda") {
                Item("Sobre o CHIRP-REA", onClick = actions::about)
            }
        }
        ChirpTheme(dark = isSystemInDarkTheme()) {
            ChirpApp(state, onChangeLogo = logo::open, appIcon = AppIcon.painter)
        }
        CloneDialog(clone)
        LogoDialog(logo) { frameRef }
    }
}
