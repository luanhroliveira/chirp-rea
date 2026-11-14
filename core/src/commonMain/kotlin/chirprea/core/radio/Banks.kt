package chirprea.core.radio

/** A memory bank (group of channels), like `chirp_common.Bank`. */
data class Bank(val index: Int, val name: String)

/** How a radio groups its memories into banks, like `chirp_common.BankModel`. */
interface BankModel {
    val banks: List<Bank>

    /** Fixed banks cannot have memories added/removed (CHIRP's StaticBankModel). */
    val isStatic: Boolean

    /** Max length of a bank name, or 0 if names cannot be edited. */
    val nameLength: Int

    /** Bank containing memory [number], or null. */
    fun bankOf(number: Int): Bank?

    /** Memory numbers belonging to [bank]. */
    fun memoriesIn(bank: Bank): IntRange

    fun rename(bank: Bank, name: String): Unit = throw UnsupportedOperationException("Bank names are fixed")
}

/**
 * Fixed banks that split the memory range into [count] equal parts, as in
 * `chirp_common.StaticBankModel` (bank 1 = first slice, ...).
 */
open class StaticBankModel(
    private val bounds: IntRange,
    private val count: Int,
    private val names: () -> List<String> = { List(count) { "Bank" } },
) : BankModel {
    private val perBank get() = (bounds.last - bounds.first + 1) / count

    override val banks: List<Bank> get() = names().mapIndexed { i, n -> Bank(i + 1, n) }
    override val isStatic = true
    override val nameLength = 0

    override fun bankOf(number: Int): Bank? {
        if (number !in bounds) return null
        return banks.getOrNull((number - bounds.first) / perBank)
    }

    override fun memoriesIn(bank: Bank): IntRange {
        val start = bounds.first + (bank.index - 1) * perBank
        return start until start + perBank
    }
}

/** A labelled fact shown on the "Informações" tab. */
data class InfoItem(val label: String, val value: String)
