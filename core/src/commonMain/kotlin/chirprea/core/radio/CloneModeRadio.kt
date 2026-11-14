package chirprea.core.radio

import chirprea.core.bitwise.Bitwise
import chirprea.core.bitwise.StructElement
import chirprea.core.memmap.MemoryMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A radio that is programmed by downloading/uploading its whole memory image
 * ("clone mode"), like `chirp_common.CloneModeRadio`.
 */
abstract class CloneModeRadio : Radio {
    abstract val info: DriverInfo
    abstract val memSize: Int
    /** The bitwise `MEM_FORMAT` of this radio. */
    abstract val memFormat: String

    override val vendor get() = info.vendor
    override val model get() = info.model
    override val variant get() = info.variant

    var mmap: MemoryMap = MemoryMap(0)
        private set
    protected lateinit var memobj: StructElement
        private set

    /** Parsed memory structure, for the developer "Navegador" view. */
    val memoryObject: StructElement get() = memobj

    override fun info(): List<InfoItem> = super.info() + listOf(
        InfoItem("Variante", variant.ifEmpty { "—" }),
        InfoItem("Driver (classe CHIRP)", info.chirpClass),
        InfoItem("Velocidade da serial", "${info.baudRate} baud"),
        InfoItem("Tamanho da imagem", "${mmap.size} bytes (0x${mmap.size.toString(16).uppercase()})"),
        InfoItem("Bandas", features().validBands.joinToString("\n") { "${mhz(it.first)} – ${mhz(it.last)} MHz" }),
    ) + metadata.entries.sortedBy { it.key }.map { (k, v) ->
        InfoItem("Metadado: $k", (v as? JsonPrimitive)?.content ?: v.toString())
    }

    private fun mhz(hz: Long) = chirprea.core.model.Fmt.fixed(hz / 1_000_000.0, 3)

    /** Extra metadata stored in `.img` files (round-tripped untouched). */
    var metadata: Map<String, JsonElement> = emptyMap()

    /** Load a memory image and parse it with [memFormat]. */
    open fun loadMmap(map: MemoryMap) {
        mmap = map
        processMmap()
    }

    protected open fun processMmap() {
        memobj = Bitwise.parse(memFormat, mmap)
    }

    /** Download the memory image from the radio. */
    abstract suspend fun syncIn(port: SerialPort, status: (CloneStatus) -> Unit = {})

    /** Upload the memory image to the radio. */
    abstract suspend fun syncOut(port: SerialPort, status: (CloneStatus) -> Unit = {})

    fun toImage(): ByteArray = ImageFile.encode(mmap.toByteArray(), buildMetadata())

    fun loadImage(bytes: ByteArray) {
        val (data, meta) = ImageFile.decode(bytes)
        metadata = meta
        loadMmap(MemoryMap(data))
    }

    private fun buildMetadata(): Map<String, JsonElement> =
        metadata + mapOf(
            "rclass" to JsonPrimitive(info.chirpClass),
            "vendor" to JsonPrimitive(vendor),
            "model" to JsonPrimitive(model),
            "variant" to JsonPrimitive(variant),
            "chirp_version" to JsonPrimitive(ImageFile.APP_VERSION),
        )
}

/**
 * CHIRP `.img` format: raw memory image, then the magic marker, then a
 * base64-encoded JSON metadata blob. Compatible with files written by CHIRP.
 */
@OptIn(ExperimentalEncodingApi::class)
object ImageFile {
    const val APP_VERSION = "chirp-rea-0.1.0"

    val MAGIC: ByteArray = byteArrayOf(0x00, 0xFF.toByte()) +
        "chirp".encodeToByteArray() + byteArrayOf(0xEE.toByte()) +
        "img".encodeToByteArray() + byteArrayOf(0x00, 0x01)

    fun encode(data: ByteArray, metadata: Map<String, JsonElement>): ByteArray {
        val json = Json.encodeToString(JsonElement.serializer(), JsonObject(metadata))
        return data + MAGIC + Base64.encodeToByteArray(json.encodeToByteArray())
    }

    fun decode(bytes: ByteArray): Pair<ByteArray, Map<String, JsonElement>> {
        val idx = indexOf(bytes, MAGIC)
        if (idx < 0) return bytes to emptyMap()
        val blob = bytes.copyOfRange(idx + MAGIC.size, bytes.size)
        val meta = runCatching {
            val text = Base64.decode(blob).decodeToString()
            Json.parseToJsonElement(text) as JsonObject
        }.getOrNull() ?: emptyMap()
        return bytes.copyOfRange(0, idx) to meta
    }

    /** CHIRP class name stored in an image, used to pick the driver. */
    fun rclass(bytes: ByteArray): String? =
        decode(bytes).second["rclass"]?.jsonPrimitive?.content

    private fun indexOf(haystack: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
