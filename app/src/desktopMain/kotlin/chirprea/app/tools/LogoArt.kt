package chirprea.app.tools

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.GeneralPath
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Original power-on logos shipped with CHIRP-REA, drawn in code so the artwork
 * is reproducible and distributed under the project's licence (GPL-3.0).
 *
 * Usage: ./gradlew :app:generateLogos   (writes app/src/commonMain/resources/logos)
 *
 * Canvas is square with a near-black background so the app's "Ajustar" fit
 * blends with the black letterbox on portrait (128x160) and landscape (150x128) radios.
 */
private const val S = 800.0

private class Logo(val file: String, val title: String, val draw: Graphics2D.() -> Unit)

private val BG = Color(0x0B0F14)

private fun rgb(hex: Int) = Color(hex)

private fun Graphics2D.setup() {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    color = BG
    fill(Rectangle2D.Double(0.0, 0.0, S, S))
}

private fun poly(vararg pts: Double): Path2D.Double = Path2D.Double().apply {
    moveTo(pts[0], pts[1])
    var i = 2
    while (i < pts.size) { lineTo(pts[i], pts[i + 1]); i += 2 }
    closePath()
}

/** Layered pine tree: three stacked triangles and a trunk. */
private fun Graphics2D.pine(cx: Double, baseY: Double, h: Double, c: Color) {
    val w = h * 0.55
    color = rgb(0x3B2A1E)
    fill(Rectangle2D.Double(cx - h * 0.04, baseY - h * 0.12, h * 0.08, h * 0.12))
    color = c
    for (k in 0 until 3) {
        val top = baseY - h + k * h * 0.25
        val bottom = baseY - h * 0.1 - (2 - k) * h * 0.18
        val half = w / 2 * (0.6 + 0.2 * k)
        fill(poly(cx, top, cx + half, bottom, cx - half, bottom))
    }
}

private fun Graphics2D.stars(n: Int, seed: Int, maxY: Double, clip: java.awt.Shape? = null) {
    val r = Random(seed)
    repeat(n) {
        val x = r.nextDouble() * S
        val y = r.nextDouble() * maxY
        if (clip != null && !clip.contains(x, y)) return@repeat
        val s = 2.0 + r.nextDouble() * 4.0
        color = Color(255, 255, 230, 140 + r.nextInt(115))
        fill(Ellipse2D.Double(x - s / 2, y - s / 2, s, s))
    }
}

private fun Graphics2D.textCentered(text: String, cx: Double, baseline: Double, size: Float, c: Color, style: Int = Font.BOLD) {
    font = Font(Font.SANS_SERIF, style, size.toInt())
    val w = fontMetrics.stringWidth(text)
    color = c
    drawString(text, (cx - w / 2).toFloat(), baseline.toFloat())
}

/** Flame as a teardrop made of two cubic curves. */
private fun flame(cx: Double, baseY: Double, w: Double, h: Double): Path2D.Double = Path2D.Double().apply {
    moveTo(cx, baseY - h)
    curveTo(cx + w * 0.15, baseY - h * 0.6, cx + w * 0.6, baseY - h * 0.45, cx + w * 0.45, baseY - h * 0.1)
    curveTo(cx + w * 0.35, baseY + h * 0.05, cx - w * 0.35, baseY + h * 0.05, cx - w * 0.45, baseY - h * 0.1)
    curveTo(cx - w * 0.6, baseY - h * 0.45, cx - w * 0.15, baseY - h * 0.6, cx, baseY - h)
    closePath()
}

private val LOGOS = listOf(
    Logo("fogueira.png", "Fogueira") {
        val badge = Ellipse2D.Double(60.0, 60.0, 680.0, 680.0)
        paint = GradientPaint(0f, 60f, rgb(0x0E1B3D), 0f, 740f, rgb(0x25385F))
        fill(badge)
        stars(70, 7, 520.0, badge)
        // ground
        val oldClip = clip
        clip(badge)
        color = rgb(0x1C2A1A)
        fill(Ellipse2D.Double(20.0, 560.0, 760.0, 400.0))
        pine(170.0, 600.0, 260.0, rgb(0x15331F))
        pine(630.0, 600.0, 280.0, rgb(0x15331F))
        clip = oldClip
        // glow
        paint = RadialGradientPaint(400f, 540f, 260f, floatArrayOf(0f, 1f), arrayOf(Color(255, 150, 40, 170), Color(255, 150, 40, 0)))
        fill(Ellipse2D.Double(140.0, 280.0, 520.0, 520.0))
        // logs
        stroke = BasicStroke(46f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        // flames first, then the logs in front of their base
        color = rgb(0xE8401C); fill(flame(400.0, 590.0, 280.0, 330.0))
        color = rgb(0xFF8A1E); fill(flame(400.0, 590.0, 200.0, 250.0))
        color = rgb(0xFFD54A); fill(flame(400.0, 590.0, 110.0, 150.0))
        color = rgb(0x7A4A28)
        draw(java.awt.geom.Line2D.Double(240.0, 660.0, 560.0, 585.0))
        color = rgb(0x6B3F22)
        draw(java.awt.geom.Line2D.Double(240.0, 585.0, 560.0, 660.0))
        // ring
        stroke = BasicStroke(22f)
        color = rgb(0xF2E3C6)
        draw(badge)
    },
    Logo("montanhas.png", "Montanhas") {
        val badge = Ellipse2D.Double(60.0, 60.0, 680.0, 680.0)
        paint = GradientPaint(0f, 60f, rgb(0xF27B35), 0f, 520f, rgb(0xFFD08A))
        fill(badge)
        val oldClip = clip
        clip(badge)
        color = rgb(0xFFF1C9); fill(Ellipse2D.Double(470.0, 170.0, 150.0, 150.0))
        color = rgb(0x6B4E71); fill(poly(40.0, 620.0, 300.0, 250.0, 560.0, 620.0))
        color = rgb(0x4A3657); fill(poly(260.0, 640.0, 520.0, 210.0, 800.0, 640.0))
        color = rgb(0xF5F5F5)
        fill(poly(300.0, 250.0, 352.0, 324.0, 322.0, 310.0, 300.0, 330.0, 276.0, 306.0, 250.0, 322.0))
        fill(poly(520.0, 210.0, 580.0, 298.0, 548.0, 282.0, 522.0, 306.0, 494.0, 284.0, 462.0, 298.0))
        color = rgb(0x1F3B2A); fill(Rectangle2D.Double(0.0, 600.0, S, 200.0))
        for ((x, h) in listOf(130.0 to 230.0, 210.0 to 290.0, 290.0 to 210.0, 520.0 to 220.0, 600.0 to 300.0, 680.0 to 240.0)) {
            pine(x, 700.0, h, rgb(0x14291C))
        }
        clip = oldClip
        stroke = BasicStroke(24f)
        color = rgb(0x2B2133)
        draw(badge)
    },
    Logo("bussola.png", "Bússola") {
        val c = 400.0
        color = rgb(0xEFE6D2); fill(Ellipse2D.Double(c - 330, c - 330, 660.0, 660.0))
        stroke = BasicStroke(14f); color = rgb(0x2A2A2A); draw(Ellipse2D.Double(c - 330, c - 330, 660.0, 660.0))
        stroke = BasicStroke(4f); draw(Ellipse2D.Double(c - 270, c - 270, 540.0, 540.0))
        // ticks
        for (i in 0 until 72) {
            val a = i * 2 * PI / 72
            val r1 = if (i % 9 == 0) 245.0 else 258.0
            stroke = BasicStroke(if (i % 9 == 0) 6f else 3f)
            draw(java.awt.geom.Line2D.Double(c + cos(a) * r1, c + sin(a) * r1, c + cos(a) * 270, c + sin(a) * 270))
        }
        fun point(angle: Double, len: Double, width: Double, dark: Color, light: Color) {
            val tipX = c + cos(angle) * len; val tipY = c + sin(angle) * len
            val lx = c + cos(angle - PI / 2) * width; val ly = c + sin(angle - PI / 2) * width
            val rx = c + cos(angle + PI / 2) * width; val ry = c + sin(angle + PI / 2) * width
            color = dark; fill(poly(c, c, tipX, tipY, lx, ly))
            color = light; fill(poly(c, c, tipX, tipY, rx, ry))
        }
        for (k in 0 until 4) point(PI / 4 + k * PI / 2, 150.0, 34.0, rgb(0x5A5A5A), rgb(0xBDBDBD))
        for (k in 0 until 4) {
            val north = k == 3
            point(k * PI / 2, 230.0, 50.0, if (north) rgb(0x9E1B1B) else rgb(0x222222), if (north) rgb(0xE53935) else rgb(0x777777))
        }
        color = rgb(0x2A2A2A); fill(Ellipse2D.Double(c - 22, c - 22, 44.0, 44.0))
        // Cardinal letters sit in the outer ring (radius ~300), dark on the cream band.
        textCentered("N", c, c - 300 + 22, 58f, rgb(0x9E1B1B))
        textCentered("S", c, c + 300 + 22, 58f, rgb(0x2A2A2A))
        textCentered("L", c + 300, c + 22, 58f, rgb(0x2A2A2A))
        textCentered("O", c - 300, c + 22, 58f, rgb(0x2A2A2A))
    },
    Logo("radio-73.png", "Rádio 73") {
        // waves
        stroke = BasicStroke(26f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        for ((i, r) in listOf(120.0, 200.0, 280.0).withIndex()) {
            color = Color(0x4D, 0xD0, 0xE1, 255 - i * 60)
            draw(Arc2D.Double(380.0 - r, 150.0 - r, 2 * r, 2 * r, -40.0, 80.0, Arc2D.OPEN))
            draw(Arc2D.Double(380.0 - r, 150.0 - r, 2 * r, 2 * r, 140.0, 80.0, Arc2D.OPEN))
        }
        // antenna
        color = rgb(0x2B2B2B); fill(RoundRectangle2D.Double(355.0, 60.0, 50.0, 260.0, 40.0, 40.0))
        color = rgb(0x3A3A3A); fill(Ellipse2D.Double(350.0, 40.0, 60.0, 60.0))
        // body
        color = rgb(0x1E1E1E); fill(RoundRectangle2D.Double(240.0, 280.0, 320.0, 480.0, 70.0, 70.0))
        stroke = BasicStroke(8f); color = rgb(0x4A4A4A); draw(RoundRectangle2D.Double(240.0, 280.0, 320.0, 480.0, 70.0, 70.0))
        // screen
        paint = GradientPaint(0f, 320f, rgb(0x9FE870), 0f, 450f, rgb(0x5DBB3A))
        fill(RoundRectangle2D.Double(275.0, 320.0, 250.0, 140.0, 24.0, 24.0))
        textCentered("73", 400.0, 435.0, 110f, rgb(0x123B10))
        // keypad
        color = rgb(0x5A5A5A)
        for (row in 0 until 4) for (col in 0 until 3) {
            fill(RoundRectangle2D.Double(285.0 + col * 82, 495.0 + row * 62, 66.0, 44.0, 16.0, 16.0))
        }
    },
    Logo("torre-cq.png", "Torre CQ") {
        paint = GradientPaint(0f, 0f, rgb(0x0D1B2A), 0f, 800f, rgb(0x274060))
        fill(Rectangle2D.Double(40.0, 40.0, 720.0, 720.0))
        stars(50, 11, 500.0)
        // tower lattice
        stroke = BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        color = rgb(0xC9D6DF)
        val top = 210.0; val bottom = 700.0
        draw(java.awt.geom.Line2D.Double(400.0, top, 300.0, bottom))
        draw(java.awt.geom.Line2D.Double(400.0, top, 500.0, bottom))
        stroke = BasicStroke(6f)
        var y = top + 60
        var left = true
        while (y < bottom) {
            val w1 = (y - top) / (bottom - top) * 100
            val y2 = minOf(y + 70, bottom)
            val w2 = (y2 - top) / (bottom - top) * 100
            draw(java.awt.geom.Line2D.Double(400 - w1, y, 400 + w1, y))
            if (left) draw(java.awt.geom.Line2D.Double(400 - w1, y, 400 + w2, y2)) else draw(java.awt.geom.Line2D.Double(400 + w1, y, 400 - w2, y2))
            left = !left
            y += 70
        }
        color = rgb(0xFF5252); fill(Ellipse2D.Double(380.0, 180.0, 40.0, 40.0))
        // waves
        stroke = BasicStroke(18f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        for ((i, r) in listOf(80.0, 135.0, 190.0).withIndex()) {
            color = Color(0xFF, 0xC1, 0x07, 255 - i * 65)
            draw(Arc2D.Double(400.0 - r, 200.0 - r, 2 * r, 2 * r, -30.0, 60.0, Arc2D.OPEN))
            draw(Arc2D.Double(400.0 - r, 200.0 - r, 2 * r, 2 * r, 150.0, 60.0, Arc2D.OPEN))
        }
        textCentered("CQ", 400.0, 125.0, 96f, rgb(0xFFC107))
        stroke = BasicStroke(14f); color = rgb(0xC9D6DF); draw(Rectangle2D.Double(40.0, 40.0, 720.0, 720.0))
    },
    Logo("lua-floresta.png", "Lua e floresta") {
        paint = GradientPaint(0f, 0f, rgb(0x050B1A), 0f, 560f, rgb(0x1B2A4A))
        fill(Rectangle2D.Double(0.0, 0.0, S, 560.0))
        stars(90, 3, 520.0)
        paint = RadialGradientPaint(400f, 280f, 230f, floatArrayOf(0f, 1f), arrayOf(Color(255, 245, 200, 120), Color(255, 245, 200, 0)))
        fill(Ellipse2D.Double(170.0, 50.0, 460.0, 460.0))
        color = rgb(0xF6EFD2); fill(Ellipse2D.Double(290.0, 170.0, 220.0, 220.0))
        color = Color(0xD9, 0xCF, 0xA8)
        fill(Ellipse2D.Double(340.0, 220.0, 50.0, 40.0)); fill(Ellipse2D.Double(420.0, 290.0, 36.0, 30.0)); fill(Ellipse2D.Double(370.0, 320.0, 26.0, 22.0))
        // lake
        paint = GradientPaint(0f, 560f, rgb(0x1B2A4A), 0f, 800f, rgb(0x050B1A))
        fill(Rectangle2D.Double(0.0, 560.0, S, 240.0))
        color = Color(246, 239, 210, 110)
        for (k in 0 until 6) fill(Rectangle2D.Double(400.0 - 90 + k * 8, 600.0 + k * 30, 180.0 - k * 16, 8.0))
        // tree line
        for (x in 0..800 step 55) {
            val h = 170.0 + ((x * 37) % 120)
            pine(x.toDouble(), 575.0, h, rgb(0x07120C))
        }
    },
    Logo("estrada.png", "Estrada") {
        paint = GradientPaint(0f, 0f, rgb(0x2B1055), 0f, 420f, rgb(0xF5576C))
        fill(Rectangle2D.Double(0.0, 0.0, S, 440.0))
        color = rgb(0xFFD166); fill(Ellipse2D.Double(300.0, 240.0, 200.0, 200.0))
        color = rgb(0x3D1E5C); fill(poly(0.0, 440.0, 180.0, 250.0, 330.0, 440.0))
        color = rgb(0x52276F); fill(poly(220.0, 440.0, 470.0, 200.0, 760.0, 440.0))
        color = rgb(0x3D1E5C); fill(poly(560.0, 440.0, 700.0, 300.0, 820.0, 440.0))
        color = rgb(0x1A1A2E); fill(Rectangle2D.Double(0.0, 430.0, S, 370.0))
        // road
        color = rgb(0x3A3A4A); fill(poly(380.0, 430.0, 420.0, 430.0, 760.0, 800.0, 40.0, 800.0))
        color = rgb(0xFFD166)
        var t = 0.0
        while (t < 1.0) {
            val y1 = 440 + t * 360; val y2 = 440 + (t + 0.06) * 360
            val w1 = 3 + t * 14; val w2 = 3 + (t + 0.06) * 14
            fill(poly(400 - w1, y1, 400 + w1, y1, 400 + w2, y2, 400 - w2, y2))
            t += 0.14
        }
    },
    Logo("sinal.png", "Sinal") {
        color = rgb(0x0A1A12); fill(RoundRectangle2D.Double(60.0, 140.0, 680.0, 520.0, 50.0, 50.0))
        stroke = BasicStroke(3f); color = rgb(0x1E4D33)
        for (i in 1 until 8) draw(java.awt.geom.Line2D.Double(60.0 + i * 85, 150.0, 60.0 + i * 85, 650.0))
        for (i in 1 until 6) draw(java.awt.geom.Line2D.Double(70.0, 140.0 + i * 87, 730.0, 140.0 + i * 87))
        val wave = GeneralPath()
        for (x in 80..720) {
            val p = (x - 80) / 640.0
            val env = sin(p * PI)
            val y = 400 - sin(p * 2 * PI * 6) * 190 * env
            if (x == 80) wave.moveTo(x.toDouble(), y) else wave.lineTo(x.toDouble(), y)
        }
        stroke = BasicStroke(28f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); color = Color(0x39, 0xFF, 0x88, 70); draw(wave)
        stroke = BasicStroke(12f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND); color = rgb(0x39FF88); draw(wave)
        stroke = BasicStroke(14f); color = rgb(0x5C6B63); draw(RoundRectangle2D.Double(60.0, 140.0, 680.0, 520.0, 50.0, 50.0))
    },
)

fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "src/commonMain/resources/logos").also { it.mkdirs() }
    for (logo in LOGOS) {
        val img = BufferedImage(S.toInt(), S.toInt(), BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.setup()
        logo.draw(g)
        g.dispose()
        ImageIO.write(img, "png", File(dir, logo.file))
        println("wrote ${logo.file}")
    }
    File(dir, "index.txt").writeText(
        """
        # Logos shipped with CHIRP-REA, shown under "Incluídas no app" in the logo dialog.
        # One file name per line (files live next to this index). Only add pictures you
        # are allowed to redistribute: your own work, public domain or a free licence.
        # Record each picture's author and licence in LOGOS.md.
        # The entries below are generated by: ./gradlew :app:generateLogos
        """.trimIndent() + "\n" + LOGOS.joinToString("\n") { it.file } + "\n",
    )
    File(dir, "LOGOS.md").writeText(
        """
        # Logos incluídas no CHIRP-REA

        Imagens distribuídas junto com o app, na seção "Incluídas no app" da galeria de
        logos. Só entram aqui imagens que podem ser redistribuídas.

        | Arquivo | Título | Autor | Licença |
        |---|---|---|---|
        """.trimIndent() + "\n" +
            LOGOS.joinToString("\n") { "| `${it.file}` | ${it.title} | CHIRP-REA (desenho em código, `tools/LogoArt.kt`) | GPL-3.0, como o projeto |" } + "\n",
    )
}
