package chirprea.core.radio

/**
 * Radio-wide settings, the counterpart of `chirp/settings.py`.
 *
 * Settings are snapshots: each one carries its current value and a `write`
 * callback that stores a new value straight into the radio's memory image.
 * After writing, callers re-read [Radio.settings] to see the new state.
 * A setting is only written when the user changes it, so unknown raw values
 * already in the radio are preserved.
 */
sealed interface Setting {
    /** Stable id, e.g. `settings.squelch` (same path CHIRP uses). */
    val id: String
    val label: String
    val doc: String
}

class BoolSetting(
    override val id: String,
    override val label: String,
    override val doc: String,
    val value: Boolean,
    private val write: (Boolean) -> Unit,
) : Setting {
    fun set(v: Boolean) = write(v)
}

class ChoiceSetting(
    override val id: String,
    override val label: String,
    override val doc: String,
    val options: List<String>,
    /** Index of the current option; -1 if the radio holds a value outside [options]. */
    val index: Int,
    private val write: (Int) -> Unit,
) : Setting {
    val current: String? get() = options.getOrNull(index)

    fun set(i: Int) {
        require(i in options.indices) { "Opção inválida" }
        write(i)
    }
}

class TextSetting(
    override val id: String,
    override val label: String,
    override val doc: String,
    val value: String,
    val maxLength: Int,
    /** Allowed characters, or null for any printable ASCII. */
    val charset: String? = null,
    private val write: (String) -> Unit,
) : Setting {
    /** Validates and stores [v]; throws [IllegalArgumentException] with a user-facing message. */
    fun set(v: String) {
        require(v.length <= maxLength) { "Máximo de $maxLength caracteres" }
        val allowed = charset
        if (allowed != null) {
            val bad = v.filter { it !in allowed }
            require(bad.isEmpty()) { "Caracteres não permitidos: '$bad'" }
        } else {
            require(v.all { it.code in 0x20..0x7E }) { "Use apenas letras sem acento, números e símbolos" }
        }
        write(v)
    }
}

data class SettingGroup(
    val id: String,
    val label: String,
    val items: List<Setting> = emptyList(),
    val subgroups: List<SettingGroup> = emptyList(),
) {
    /** Every setting in this group and its subgroups. */
    fun all(): List<Setting> = items + subgroups.flatMap { it.all() }
}
