package chirprea.app.tools

import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO

/**
 * The CHIRP-REA application icon: a magnifying glass (reverse engineering)
 * whose lens holds a transmitting antenna (radio programming). Original
 * artwork drawn in code, GPL-3.0 like the rest of the project.
 *
 * Usage: ./gradlew :app:generateIcon
 * Writes src/desktopMain/resources/icon/chirp-rea-<size>.png and packaging/chirp-rea.ico
 */
private const val U = 1024.0

private fun Graphics2D.drawIcon() {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    // Rounded-square tile with a teal -> indigo gradient.
    val tile = RoundRectangle2D.Double(40.0, 40.0, U - 80, U - 80, 230.0, 230.0)
    paint = GradientPaint(0f, 40f, Color(0x12B5A6), U.toFloat(), (U - 40).toFloat(), Color(0x2E2A7A))
    fill(tile)

    // Magnifier: lens ring + handle.
    val cx = 450.0; val cy = 440.0; val r = 260.0
    color = Color(0x0B1530)
    fill(Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r))
    stroke = BasicStroke(120f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    color = Color.WHITE
    draw(Line2D.Double(cx + r * 0.72, cy + r * 0.72, 860.0, 850.0))
    stroke = BasicStroke(70f)
    draw(Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r))

    // Antenna inside the lens: mast, emitter and two pairs of waves.
    stroke = BasicStroke(46f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    color = Color.WHITE
    draw(Line2D.Double(cx, cy - 10, cx - 90, cy + 190))
    draw(Line2D.Double(cx, cy - 10, cx + 90, cy + 190))
    draw(Line2D.Double(cx - 55, cy + 110, cx + 55, cy + 110))
    color = Color(0xFFC83D)
    fill(Ellipse2D.Double(cx - 42, cy - 72, 84.0, 84.0))
    stroke = BasicStroke(40f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    for ((i, wr) in listOf(105.0, 175.0).withIndex()) {
        color = if (i == 0) Color(0x5FF2E0) else Color(0x5FF2E0).let { Color(it.red, it.green, it.blue, 200) }
        draw(Arc2D.Double(cx - wr, cy - 30 - wr, 2 * wr, 2 * wr, -40.0, 80.0, Arc2D.OPEN))
        draw(Arc2D.Double(cx - wr, cy - 30 - wr, 2 * wr, 2 * wr, 140.0, 80.0, Arc2D.OPEN))
    }
}

/** Renders the master at 1024 px and downsamples in halving steps for crisp small icons. */
private fun render(size: Int): BufferedImage {
    var img = BufferedImage(U.toInt(), U.toInt(), BufferedImage.TYPE_INT_ARGB)
    img.createGraphics().apply { drawIcon(); dispose() }
    var w = img.width
    while (w / 2 >= size) {
        w /= 2
        img = scale(img, w)
    }
    return if (w == size) img else scale(img, size)
}

private fun scale(src: BufferedImage, size: Int): BufferedImage {
    val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    out.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        drawImage(src, 0, 0, size, size, null)
        dispose()
    }
    return out
}

private fun png(img: BufferedImage): ByteArray = ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()

/** Windows .ico with PNG-compressed entries (supported since Windows Vista). */
private fun ico(sizes: List<Int>): ByteArray {
    val images = sizes.map { png(render(it)) }
    val header = ByteBuffer.allocate(6 + 16 * sizes.size).order(ByteOrder.LITTLE_ENDIAN)
    header.putShort(0).putShort(1).putShort(sizes.size.toShort())
    var offset = 6 + 16 * sizes.size
    sizes.forEachIndexed { i, s ->
        header.put((if (s >= 256) 0 else s).toByte()).put((if (s >= 256) 0 else s).toByte())
        header.put(0).put(0).putShort(1).putShort(32)
        header.putInt(images[i].size).putInt(offset)
        offset += images[i].size
    }
    return header.array() + images.reduce(ByteArray::plus)
}

fun main(args: Array<String>) {
    val res = File(args.getOrElse(0) { "src/desktopMain/resources/icon" }).also { it.mkdirs() }
    val pkg = File(args.getOrElse(1) { "packaging" }).also { it.mkdirs() }
    for (s in listOf(16, 32, 48, 64, 128, 256, 512)) {
        ImageIO.write(render(s), "png", File(res, "chirp-rea-$s.png"))
    }
    File(pkg, "chirp-rea.ico").writeBytes(ico(listOf(16, 24, 32, 48, 64, 128, 256)))
    ImageIO.write(render(1024), "png", File(pkg, "chirp-rea-1024.png"))
    println("icon written to ${res.path} and ${pkg.path}")
}
