package chirprea.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import chirprea.core.drivers.baofeng.BaofengLogo
import chirprea.core.drivers.baofeng.LogoSpec
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

enum class LogoFit(val label: String) {
    Fit("Ajustar (bordas pretas)"),
    Fill("Preencher (corta as sobras)"),
    Stretch("Esticar"),
}

/**
 * Turns any picture into a radio logo. Nothing is pre-processed or stored:
 * every call renders the original image for the requested [LogoSpec], so the
 * same picture works for radios with different logo sizes.
 */
object LogoProcessing {
    /** Scale [src] into the logo area of [spec] (transparent areas become black). */
    fun render(src: BufferedImage, spec: LogoSpec, fit: LogoFit): BufferedImage {
        val out = BufferedImage(spec.width, spec.height, BufferedImage.TYPE_INT_RGB)
        val g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.BLACK
        g.fillRect(0, 0, spec.width, spec.height)
        val sx = spec.width.toDouble() / src.width
        val sy = spec.height.toDouble() / src.height
        when (fit) {
            LogoFit.Stretch -> g.drawImage(src, 0, 0, spec.width, spec.height, null)
            LogoFit.Fill, LogoFit.Fit -> {
                val k = if (fit == LogoFit.Fill) maxOf(sx, sy) else minOf(sx, sy)
                val w = Math.round(src.width * k).toInt()
                val h = Math.round(src.height * k).toInt()
                g.drawImage(src, (spec.width - w) / 2, (spec.height - h) / 2, w, h, null)
            }
        }
        g.dispose()
        return out
    }

    /** The bytes the radio receives (RGB565 little-endian). */
    fun rgb565(src: BufferedImage, spec: LogoSpec, fit: LogoFit): ByteArray {
        val img = render(src, spec, fit)
        return BaofengLogo.toRgb565(img.getRGB(0, 0, spec.width, spec.height, null, 0, spec.width), spec.width, spec.height)
    }

    /** What the radio will really show: rendered, reduced to RGB565 and expanded back. */
    fun preview(src: BufferedImage, spec: LogoSpec, fit: LogoFit): ImageBitmap {
        val img = BufferedImage(spec.width, spec.height, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, spec.width, spec.height, BaofengLogo.fromRgb565(rgb565(src, spec, fit)), 0, spec.width)
        return img.toComposeImageBitmap()
    }

    fun read(file: File): BufferedImage? = runCatching { ImageIO.read(file) }.getOrNull()
}

/** Where a gallery picture comes from. */
enum class LogoSource(val label: String) {
    /** Shipped inside the app (resources/logos, listed in index.txt); available to every user. */
    Bundled("Incluídas no app"),
    /** The user's own folder (not in Git). */
    User("Minhas imagens"),
}

/** A picture available in the logo gallery. */
data class LogoEntry(val source: LogoSource, val name: String, val file: File? = null) {
    /** File name without folder or extension, used as the caption. */
    val title: String get() = name.substringAfterLast('/').substringBeforeLast('.')

    /** Stable identity for caches and list keys. */
    val key: String get() = "$source:${file?.path ?: name}@${file?.lastModified() ?: 0}"

    fun read(): BufferedImage? = when (source) {
        LogoSource.User -> file?.let(LogoProcessing::read)
        LogoSource.Bundled -> LogoLibrary::class.java.getResourceAsStream("${LogoLibrary.BUNDLED_DIR}/$name")
            ?.use { runCatching { ImageIO.read(it) }.getOrNull() }
    }
}

/**
 * The logo gallery: pictures shipped with the app plus the user's own folder,
 * both read at runtime. The user folder is `~/.chirp-rea/logos` (or the
 * `chirprea.logos` system property, if set). Originals
 * are cached; rendered previews are cached per (picture, spec, fit) so
 * switching models re-renders on demand.
 */
object LogoLibrary {
    val EXTENSIONS = setOf("png", "jpg", "jpeg", "jfif", "bmp", "gif")
    const val BUNDLED_DIR = "/logos"

    val folder: File by lazy {
        val dir = System.getProperty("chirprea.logos")?.let(::File)
            ?: File(System.getProperty("user.home"), ".chirp-rea/logos")
        dir.also { it.mkdirs() }
    }

    /** Sub-folders of resources/logos with their own index.txt; "" = the project's original logos. */
    val BUNDLED_FOLDERS = listOf("", "terceiros/")

    /**
     * Bundled pictures are listed in index.txt files (classpath folders can't be
     * listed inside a jar): first the original logos, then third-party pictures.
     */
    fun bundled(): List<LogoEntry> = BUNDLED_FOLDERS.flatMap { folder ->
        (LogoLibrary::class.java.getResourceAsStream("$BUNDLED_DIR/${folder}index.txt")?.bufferedReader()?.use { it.readLines() } ?: emptyList())
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.substringAfterLast('.').lowercase() in EXTENSIONS }
            .map { LogoEntry(LogoSource.Bundled, folder + it) }
    }

    fun user(): List<LogoEntry> =
        (folder.listFiles() ?: emptyArray())
            .filter { it.isFile && it.extension.lowercase() in EXTENSIONS }
            .sortedBy { it.name.lowercase() }
            .map { LogoEntry(LogoSource.User, it.name, it) }

    fun entries(): List<LogoEntry> = bundled() + user()

    private val originals = ConcurrentHashMap<String, BufferedImage>()
    private val previews = ConcurrentHashMap<String, ImageBitmap>()

    fun original(e: LogoEntry): BufferedImage? {
        originals[e.key]?.let { return it }
        return e.read()?.also { originals[e.key] = it }
    }

    /** Rendered for [spec]/[fit]; computed the first time each combination is requested. */
    fun preview(e: LogoEntry, spec: LogoSpec, fit: LogoFit): ImageBitmap? {
        val key = "${e.key}|${spec.width}x${spec.height}|$fit"
        previews[key]?.let { return it }
        val src = original(e) ?: return null
        return LogoProcessing.preview(src, spec, fit).also { previews[key] = it }
    }
}
