package chirprea.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import chirprea.core.model.Memory
import chirprea.core.radio.Radio
import chirprea.core.radio.RadioFeatures

/** One open tab: a radio (or CSV file) and the editable list of its memories. */
class RadioDocument(val radio: Radio, path: String?, title: String) {
    var path by mutableStateOf(path)
    var title by mutableStateOf(title)
    var dirty by mutableStateOf(false)
    var tab by mutableStateOf(chirprea.app.ui.RadioTab.Memories)

    val features: RadioFeatures get() = radio.features()
    val rows = mutableStateListOf<Memory>().apply { addAll(radio.memories()) }

    /**
     * Validates and stores [mem]; returns the validation errors, or an empty
     * list when the change was applied.
     */
    fun update(mem: Memory): List<String> {
        val errors = features.validate(mem)
        if (errors.isNotEmpty()) return errors
        radio.setMemory(mem)
        refresh(mem.number)
        dirty = true
        return emptyList()
    }

    fun erase(number: Int) {
        radio.eraseMemory(number)
        refresh(number)
        dirty = true
    }

    private fun refresh(number: Int) {
        val fresh = radio.getMemory(number)
        val idx = rows.indexOfFirst { it.number == number }
        if (idx >= 0) rows[idx] = fresh else { rows.clear(); rows.addAll(radio.memories()) }
    }

    fun reload() {
        rows.clear()
        rows.addAll(radio.memories())
    }
}

class AppState {
    val documents = mutableStateListOf<RadioDocument>()
    var selected by mutableIntStateOf(0)
    var status by mutableStateOf("Pronto")

    val current: RadioDocument? get() = documents.getOrNull(selected)

    fun open(doc: RadioDocument) {
        documents += doc
        selected = documents.lastIndex
    }

    fun close(index: Int) {
        if (index !in documents.indices) return
        documents.removeAt(index)
        selected = selected.coerceAtMost(documents.lastIndex).coerceAtLeast(0)
    }
}
