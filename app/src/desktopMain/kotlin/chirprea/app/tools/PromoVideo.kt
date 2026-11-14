package chirprea.app.tools

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.CubicCurve2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Promotional video of CHIRP-REA, drawn frame by frame in code (original art only:
 * the app icon and the project's own logos) and encoded with FFmpeg.
 *
 *   ./gradlew :app:promoVideo                      -> build/promo/chirp-rea-promo.mp4
 *   ./gradlew :app:promoVideo -Pstills=3,10,20     -> PNG stills for review
 *   -Pffmpeg=C:\path\ffmpeg.exe                     (default: ffmpeg on PATH)
 *   -Pnarration=<dir with 1.wav … 7.wav>            (voice-over; see app/promo/narration)
 */
private const val W = 1920
private const val H = 1080
private const val FPS = 30
// Scene lengths (s), sized so each narration line fits with some air.
private const val L_INTRO = 7.0
private const val L_CONNECT = 8.0
private const val L_MEM = 14.0
private const val L_BANKS = 10.0
private const val L_LOGO = 14.5
private const val L_PLAT = 6.5
private const val L_OUTRO = 6.0
private val SCENE_LENGTHS = listOf(L_INTRO, L_CONNECT, L_MEM, L_BANKS, L_LOGO, L_PLAT, L_OUTRO)
private val SCENE_STARTS = SCENE_LENGTHS.runningFold(0.0) { a, b -> a + b }
private val DURATION = SCENE_STARTS.last()

/** Narration line n (1..7) starts this many seconds into its scene. */
private val NARRATION_OFFSETS = listOf(0.8, 0.6, 0.5, 0.5, 0.5, 0.4, 0.4)

private val FONT = if ("Segoe UI" in java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames) "Segoe UI" else Font.SANS_SERIF

private val TEAL = Color(0x12B5A6)
private val CYAN = Color(0x5FF2E0)
private val INDIGO = Color(0x2E2A7A)
private val AMBER = Color(0xFFC83D)
private val INK = Color(0xE9ECF5)
private val MUTED = Color(0x9AA3B8)
private val PANEL = Color(0x161A26)
private val PANEL2 = Color(0x1E2433)

// ------------------------------------------------------------------ helpers

private fun clamp01(x: Double) = x.coerceIn(0.0, 1.0)
/** Progress of [t] between [a] and [b], clamped to 0..1. */
private fun p(t: Double, a: Double, b: Double) = clamp01((t - a) / (b - a))
private fun easeOut(x: Double) = 1 - (1 - x).pow(3)
private fun easeInOut(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun easeBack(x: Double): Double { val c1 = 1.70158; val c3 = c1 + 1; return 1 + c3 * (x - 1).pow(3) + c1 * (x - 1).pow(2) }
private fun lerp(a: Double, b: Double, x: Double) = a + (b - a) * x
private fun alpha(c: Color, a: Double) = Color(c.red, c.green, c.blue, (clamp01(a) * c.alpha).roundToInt())

private fun Graphics2D.setup() {
    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
}

private inline fun Graphics2D.withAlpha(a: Double, block: Graphics2D.() -> Unit) {
    if (a <= 0.0) return
    val old = composite
    composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, clamp01(a).toFloat() * ((old as? AlphaComposite)?.alpha ?: 1f))
    block()
    composite = old
}

private inline fun Graphics2D.withTransform(block: Graphics2D.() -> Unit) {
    val old = transform
    block()
    transform = old
}

private fun Graphics2D.text(s: String, x: Double, y: Double, size: Float, c: Color, bold: Boolean = false, center: Boolean = false) {
    font = Font(FONT, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(size)
    color = c
    val w = if (center) fontMetrics.stringWidth(s) / 2.0 else 0.0
    drawString(s, (x - w).toFloat(), y.toFloat())
}

private fun Graphics2D.textWidth(s: String, size: Float, bold: Boolean = false): Int {
    font = Font(FONT, if (bold) Font.BOLD else Font.PLAIN, 1).deriveFont(size)
    return fontMetrics.stringWidth(s)
}

private fun rr(x: Double, y: Double, w: Double, h: Double, r: Double) = RoundRectangle2D.Double(x, y, w, h, r, r)

private fun res(path: String): BufferedImage =
    ImageIO.read(object {}.javaClass.getResourceAsStream(path) ?: error("missing resource $path"))

private val ICON by lazy { res("/icon/chirp-rea-512.png") }
private val LOGOS by lazy {
    listOf("fogueira", "montanhas", "bussola", "radio-73", "torre-cq", "lua-floresta", "estrada", "sinal").associateWith { res("/logos/$it.png") }
}

/** The chosen logo as the radio stores it: 128x160 portrait, fitted with black borders. */
private val LOGO_128x160 by lazy {
    val src = LOGOS.getValue("fogueira")
    BufferedImage(128, 160, BufferedImage.TYPE_INT_RGB).also { out ->
        out.createGraphics().apply {
            setup(); color = Color.BLACK; fillRect(0, 0, 128, 160)
            drawImage(src, 0, 16, 128, 128, null); dispose()
        }
    }
}

// ------------------------------------------------------------------ shared scenery

/** Night-blue background with a slowly moving spectrum and grid (radio context). */
private fun Graphics2D.background(t: Double) {
    paint = GradientPaint(0f, 0f, Color(0x0A0E1A), 0f, H.toFloat(), Color(0x111833))
    fill(Rectangle2D.Double(0.0, 0.0, W.toDouble(), H.toDouble()))
    stroke = BasicStroke(1f)
    color = Color(255, 255, 255, 10)
    var x = 0.0
    while (x < W) { draw(Line2D.Double(x, 0.0, x, H.toDouble())); x += 80 }
    var y = 0.0
    while (y < H) { draw(Line2D.Double(0.0, y, W.toDouble(), y)); y += 80 }
    // spectrum line along the bottom
    for ((i, c) in listOf(alpha(TEAL, 0.35), alpha(CYAN, 0.18)).withIndex()) {
        val path = Path2D.Double()
        for (px in 0..W step 8) {
            val v = sin(px / 90.0 + t * (1.4 + i * 0.6)) * 18 + sin(px / 23.0 - t * 2.1) * 7 +
                exp(-((px - W / 2.0 - sin(t * 0.5) * 500) / 160).pow(2)) * 70
            val yy = H - 90 - v - i * 14
            if (px == 0) path.moveTo(px.toDouble(), yy) else path.lineTo(px.toDouble(), yy)
        }
        stroke = BasicStroke(2.5f)
        color = c
        draw(path)
    }
}

private fun exp(x: Double) = kotlin.math.exp(x)

/** Section caption at the top-left: title + subtitle sliding in. */
private fun Graphics2D.caption(title: String, subtitle: String, t: Double, out: Double) {
    val a = easeOut(p(t, 0.0, 0.7)) * (1 - p(t, out - 0.5, out))
    withAlpha(a) {
        val dx = (1 - easeOut(p(t, 0.0, 0.7))) * -40
        color = TEAL
        fill(rr(110 + dx, 92.0, 10.0, 96.0, 6.0))
        text(title, 142 + dx, 140.0, 58f, INK, bold = true)
        text(subtitle, 144 + dx, 182.0, 28f, MUTED)
    }
}

private fun Graphics2D.icon(cx: Double, cy: Double, size: Double) {
    drawImage(ICON, (cx - size / 2).toInt(), (cy - size / 2).toInt(), size.toInt(), size.toInt(), null)
}

/** Expanding radio rings around a point. */
private fun Graphics2D.rings(cx: Double, cy: Double, t: Double, maxR: Double, color0: Color, count: Int = 4, speed: Double = 0.55) {
    stroke = BasicStroke(3f)
    for (k in 0 until count) {
        val ph = ((t * speed + k.toDouble() / count) % 1.0)
        val r = ph * maxR
        color = alpha(color0, (1 - ph) * 0.6)
        draw(Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r))
    }
}

/** Generic handheld radio (no brand), with a 128x160 portrait colour screen. */
private fun Graphics2D.radio(x: Double, y: Double, s: Double, label: String, screen: Graphics2D.(Rectangle2D.Double) -> Unit) {
    withTransform {
        translate(x, y); scale(s, s)
        // antenna
        paint = GradientPaint(0f, 0f, Color(0x2A2E36), 30f, 0f, Color(0x111318))
        fill(rr(48.0, -250.0, 44.0, 290.0, 30.0))
        // body
        paint = GradientPaint(0f, 0f, Color(0x2C313B), 340f, 0f, Color(0x15181E))
        fill(rr(0.0, 0.0, 340.0, 700.0, 70.0))
        stroke = BasicStroke(4f); color = Color(0x3E4552); draw(rr(0.0, 0.0, 340.0, 700.0, 70.0))
        // knob
        color = Color(0x1A1D23); fill(rr(230.0, -60.0, 60.0, 80.0, 18.0))
        // speaker slots
        color = Color(0x0E1014)
        for (i in 0 until 5) fill(rr(70.0, 34.0 + i * 14, 200.0, 6.0, 6.0))
        // screen bezel + screen (128x160 * 1.8)
        color = Color(0x0B0C10); fill(rr(42.0, 112.0, 256.0, 312.0, 16.0))
        val scr = Rectangle2D.Double(55.0, 124.0, 230.4, 288.0)
        color = Color(0x050608); fill(scr)
        val clip0 = clip
        clip(scr)
        screen(scr)
        clip = clip0
        // label
        text(label, 170.0, 462.0, 22f, MUTED, bold = true, center = true)
        // keypad
        for (r in 0 until 4) for (c in 0 until 3) {
            color = Color(0x2F3540)
            fill(rr(58.0 + c * 80, 490.0 + r * 48, 64.0, 34.0, 12.0))
        }
    }
}

/** Laptop with an app window on its screen. */
private fun Graphics2D.laptop(x: Double, y: Double, w: Double, content: Graphics2D.(Rectangle2D.Double) -> Unit) {
    val h = w * 0.62
    color = Color(0x2A2F3A); fill(rr(x, y, w, h, 22.0))
    val scr = Rectangle2D.Double(x + 16, y + 16, w - 32, h - 32)
    color = Color(0x0D1018); fill(scr)
    val clip0 = clip
    clip(scr); content(scr); clip = clip0
    // base
    val base = Path2D.Double().apply {
        moveTo(x - 60, y + h + 4); lineTo(x + w + 60, y + h + 4); lineTo(x + w + 20, y + h + 34); lineTo(x - 20, y + h + 34); closePath()
    }
    paint = GradientPaint(0f, (y + h).toFloat(), Color(0x3A404D), 0f, (y + h + 34).toFloat(), Color(0x1E222A))
    fill(base)
}

/** App window chrome: title bar with icon, menus, optional sub-tabs. Returns the content rect. */
private fun Graphics2D.appWindow(x: Double, y: Double, w: Double, h: Double, tabs: List<String> = emptyList(), active: Int = 0): Rectangle2D.Double {
    color = Color(0, 0, 0, 90); fill(rr(x + 10, y + 16, w, h, 22.0))
    color = PANEL; fill(rr(x, y, w, h, 22.0))
    color = Color(0x232838); fill(rr(x, y, w, 52.0, 22.0)); fill(Rectangle2D.Double(x, y + 30, w, 22.0))
    drawImage(ICON, (x + 18).toInt(), (y + 11).toInt(), 30, 30, null)
    text("CHIRP-REA", x + 58, y + 35, 20f, INK, bold = true)
    for ((i, m) in listOf("Arquivo", "Rádio", "Ajuda").withIndex()) text(m, x + 200 + i * 110, y + 35, 19f, MUTED)
    var cy = y + 52
    if (tabs.isNotEmpty()) {
        color = Color(0x1A1F2C); fill(Rectangle2D.Double(x, cy, w, 46.0))
        var tx = x + 16
        for ((i, tab) in tabs.withIndex()) {
            val tw = textWidth(tab, 19f) + 36.0
            if (i == active) { color = Color(0x3B3480); fill(rr(tx, cy + 6, tw, 34.0, 10.0)) }
            text(tab, tx + 18, cy + 30, 19f, if (i == active) INK else MUTED, bold = i == active)
            tx += tw + 6
        }
        cy += 46
    }
    return Rectangle2D.Double(x, cy, w, y + h - cy)
}

private fun Graphics2D.cursor(x: Double, y: Double, pressed: Double = 0.0) {
    if (pressed > 0) {
        color = alpha(CYAN, 0.6 * (1 - pressed)); val r = 10 + pressed * 40
        stroke = BasicStroke(3f); draw(Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r))
    }
    val arrow = Path2D.Double().apply {
        moveTo(x, y); lineTo(x, y + 44); lineTo(x + 11, y + 33); lineTo(x + 20, y + 52); lineTo(x + 28, y + 48)
        lineTo(x + 19, y + 30); lineTo(x + 34, y + 30); closePath()
    }
    color = Color.WHITE; fill(arrow)
    stroke = BasicStroke(2.5f); color = Color(0x10131A); draw(arrow)
}

private fun cubicPoint(c: CubicCurve2D.Double, u: Double): Point2D.Double {
    val m = 1 - u
    return Point2D.Double(
        m * m * m * c.x1 + 3 * m * m * u * c.ctrlx1 + 3 * m * u * u * c.ctrlx2 + u * u * u * c.x2,
        m * m * m * c.y1 + 3 * m * m * u * c.ctrly1 + 3 * m * u * u * c.ctrly2 + u * u * u * c.y2,
    )
}

/** USB cable with data packets moving along it while [active] (0..1 intensity). */
private fun Graphics2D.cable(c: CubicCurve2D.Double, t: Double, active: Double, reverse: Boolean = false) {
    stroke = BasicStroke(14f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    color = Color(0x2B303B); draw(c)
    stroke = BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
    color = alpha(TEAL, 0.25 + 0.5 * active); draw(c)
    if (active <= 0) return
    for (k in 0 until 9) {
        var u = ((t * 0.45 + k / 9.0) % 1.0)
        if (reverse) u = 1 - u
        val pt = cubicPoint(c, u)
        color = alpha(if (k % 3 == 0) AMBER else CYAN, active)
        fill(rr(pt.x - 9, pt.y - 9, 18.0, 18.0, 6.0))
    }
}

private fun Graphics2D.progressBar(x: Double, y: Double, w: Double, frac: Double, label: String) {
    color = Color(0x2A3042); fill(rr(x, y, w, 16.0, 16.0))
    paint = GradientPaint(x.toFloat(), 0f, TEAL, (x + w).toFloat(), 0f, CYAN)
    fill(rr(x, y, max(16.0, w * clamp01(frac)), 16.0, 16.0))
    text(label, x, y + 50, 21f, MUTED)
}

// ------------------------------------------------------------------ scenes

/** 0–6 s: rings, icon, title. */
private fun Graphics2D.sceneIntro(t: Double) {
    val cx = W / 2.0; val cy = 430.0
    val fade = 1 - p(t, L_INTRO - 0.6, L_INTRO)
    withAlpha(fade) {
        rings(cx, cy, t, 560.0, TEAL, 5, 0.45)
        val s = easeBack(p(t, 0.6, 1.6))
        if (s > 0) {
            paint = RadialGradientPaint(cx.toFloat(), cy.toFloat(), 320f, floatArrayOf(0f, 1f), arrayOf(alpha(TEAL, 0.35 * s), alpha(TEAL, 0.0)))
            fill(Ellipse2D.Double(cx - 320, cy - 320, 640.0, 640.0))
            icon(cx, cy, 300 * s)
        }
        withAlpha(easeOut(p(t, 1.7, 2.5))) {
            text("CHIRP-REA", cx, 720 - 24 * (1 - easeOut(p(t, 1.7, 2.5))), 110f, INK, bold = true, center = true)
        }
        withAlpha(easeOut(p(t, 2.5, 3.3))) {
            text("Programe o seu rádio — no Windows ou no navegador", cx, 800.0, 40f, CYAN, center = true)
        }
        withAlpha(easeOut(p(t, 3.3, 4.1))) {
            text("Compatível com o CHIRP  ·  Código aberto  ·  Validado no UV-21 Pro GPS", cx, 860.0, 28f, MUTED, center = true)
        }
    }
}

/** 6–14 s: radio connected to a laptop, downloading. */
private fun Graphics2D.sceneConnect(t: Double) {
    val fade = easeOut(p(t, 0.0, 0.6)) * (1 - p(t, L_CONNECT - 0.6, L_CONNECT))
    withAlpha(fade) {
        caption("Conecte pelo cabo USB", "Rádio › Baixar do rádio — 1000 canais e configurações em segundos", t, L_CONNECT)
        val slideR = (1 - easeOut(p(t, 0.2, 1.2))) * -300
        val slideL = (1 - easeOut(p(t, 0.2, 1.2))) * 300
        val dl = p(t, 2.2, 6.4)
        radio(300.0 + slideR, 430.0, 0.8, "UV-21 Pro GPS") { r ->
            color = Color(0x0A1424); fill(r)
            text("CH 001", r.x + 20, r.y + 60, 30f, CYAN, bold = true)
            text("146.520", r.x + 20, r.y + 120, 44f, INK, bold = true)
            text("SIMPLEX 2M", r.x + 20, r.y + 170, 24f, MUTED)
            if (dl in 0.01..0.99 && ((t * 4).toInt() % 2 == 0)) text("PC", r.x + 170, r.y + 60, 24f, AMBER, bold = true)
        }
        val lx = 980.0 + slideL
        laptop(lx, 330.0, 760.0) { scr ->
            val c = appWindow(scr.x + 18, scr.y + 18, scr.width - 36, scr.height - 36)
            // dialog
            val dx = c.x + 70; val dy = c.y + 40
            color = PANEL2; fill(rr(dx, dy, c.width - 140, 250.0, 18.0))
            text("Baixar do rádio", dx + 26, dy + 48, 26f, INK, bold = true)
            text("Porta: COM6 — USB-SERIAL CH340", dx + 26, dy + 92, 19f, MUTED)
            text("Modelo: Baofeng UV-21 Pro", dx + 26, dy + 122, 19f, MUTED)
            val blocks = (dl * 526).toInt()
            progressBar(dx + 26, dy + 150, c.width - 192, dl, if (dl >= 1.0) "Download concluído!" else "Lendo do rádio…  $blocks/526")
        }
        // cable from radio side to laptop
        val c = CubicCurve2D.Double(
            300.0 + slideR + 340 * 0.8, 430.0 + 200,
            700.0, 980.0, 860.0, 640.0, lx + 2, 330.0 + 520,
        )
        cable(c, t, if (dl in 0.0001..0.999) 1.0 else 0.25 * fade)
    }
}

private data class Row(val loc: String, val freq: String, val name: String, val tmode: String, val tone: String, val dcs: String, val dup: String, val off: String, val mode: String, val pow: String)

private val ROWS = listOf(
    Row("1", "146.520000", "SIMPLEX 2M", "", "88.5", "023", "", "0.000000", "FM", "High"),
    Row("2", "147.000000", "RPT CENTRO", "Tone", "100.0", "023", "+", "0.600000", "FM", "High"),
    Row("3", "439.500000", "UHF DCS", "DTCS", "88.5", "754", "-", "5.000000", "NFM", "Low"),
    Row("4", "145.800000", "ISS", "", "88.5", "023", "", "0.000000", "FM", "High"),
    Row("5", "118.100000", "TORRE", "", "88.5", "023", "", "0.000000", "AM", "High"),
    Row("6", "433.500000", "LPD 1", "TSQL", "67.0", "023", "", "0.000000", "NFM", "Low"),
    Row("7", "146.940000", "RPT SERRA", "Tone", "141.3", "023", "-", "0.600000", "FM", "High"),
)

/** 14–26 s: memory grid filling up, highlights on tones and duplex. */
private fun Graphics2D.sceneMemories(t: Double) {
    val fade = easeOut(p(t, 0.0, 0.6)) * (1 - p(t, L_MEM - 0.6, L_MEM))
    withAlpha(fade) {
        caption("Memórias", "Frequência, nome, tons CTCSS/DCS, duplex, offset, modo e potência", t, L_MEM)
        val wy = 250 + (1 - easeOut(p(t, 0.2, 1.0))) * 80
        val c = appWindow(150.0, wy, 1620.0, 720.0, listOf("Memórias", "Configurações", "Bancos", "Informações", "Navegador"), 0)
        val cols = listOf("Loc" to 80.0, "Frequência" to 210.0, "Nome" to 200.0, "Modo do tom" to 170.0, "Tom" to 110.0, "DCS" to 100.0,
            "Duplex" to 110.0, "Offset" to 160.0, "Modo" to 110.0, "Potência" to 130.0)
        var x0 = c.x + 24
        val hy = c.y + 20
        color = Color(0x232A3C); fill(rr(c.x + 12, hy - 6, c.width - 24, 52.0, 10.0))
        val colX = cols.map { (name, w) -> val x = x0; text(name, x, hy + 28, 21f, INK, bold = true); x0 += w; x }
        ROWS.forEachIndexed { i, r ->
            val start = 1.2 + i * 0.75
            val a = p(t, start, start + 0.35)
            if (a <= 0) return@forEachIndexed
            val y = hy + 60 + i * 60.0
            withAlpha(a) {
                if (i % 2 == 1) { color = Color(255, 255, 255, 6); fill(Rectangle2D.Double(c.x + 12, y, c.width - 24, 60.0)) }
                val typed = (p(t, start, start + 0.55) * r.freq.length).toInt()
                val cells = listOf(r.loc, r.freq.take(typed), r.name, r.tmode, r.tone, r.dcs, r.dup, r.off, r.mode, r.pow)
                cells.forEachIndexed { k, v ->
                    val show = k <= 1 || p(t, start + 0.5, start + 0.8) > 0
                    if (show) withAlpha(if (k <= 1) 1.0 else p(t, start + 0.5, start + 0.8)) {
                        text(v, colX[k], y + 39, 21f, if (k == 1) CYAN else INK, bold = k == 1)
                    }
                }
            }
        }
        // highlights
        fun highlight(k0: Int, k1: Int, a: Double, b: Double, label: String) {
            val h = p(t, a, a + 0.4) * (1 - p(t, b - 0.4, b))
            if (h <= 0) return
            val x = colX[k0] - 14
            val w = (colX.getOrElse(k1 + 1) { c.x + c.width - 30 }) - x - 6
            stroke = BasicStroke(4f); color = alpha(AMBER, h)
            draw(rr(x, hy - 10, w, 60.0 + ROWS.size * 60 + 8, 16.0))
            withAlpha(h) {
                val bw = textWidth(label, 22f, true) + 36.0
                color = AMBER; fill(rr(x, hy + 74 + ROWS.size * 60.0, bw, 46.0, 23.0))
                text(label, x + 18, hy + 105 + ROWS.size * 60.0, 22f, Color(0x1A1406), bold = true)
            }
        }
        highlight(3, 5, 7.0, 8.9, "Tons CTCSS e DCS")
        highlight(6, 7, 8.9, 10.8, "Repetidoras: duplex e offset")
    }
}

/** 26–36 s: channels flying into 10 fixed banks, names typed. */
private fun Graphics2D.sceneBanks(t: Double) {
    val fade = easeOut(p(t, 0.0, 0.6)) * (1 - p(t, L_BANKS - 0.6, L_BANKS))
    withAlpha(fade) {
        caption("Bancos", "10 bancos de 100 canais, com nomes editáveis", t, L_BANKS)
        val names = listOf("RADIOAMADOR", "AVIACAO", "REPETIDORAS", "GMRS", "", "", "", "", "", "")
        val gx = 360.0; val gy = 300.0; val cw = 270.0; val ch = 250.0
        for (i in 0 until 10) {
            val col = i % 5; val row = i / 5
            val a = easeBack(p(t, 0.5 + i * 0.08, 1.1 + i * 0.08))
            if (a <= 0) continue
            val x = gx + col * (cw + 26); val y = gy + row * (ch + 30)
            withTransform {
                translate(x + cw / 2, y + ch / 2); scale(a, a); translate(-(x + cw / 2), -(y + ch / 2))
                color = PANEL; fill(rr(x, y, cw, ch, 22.0))
                stroke = BasicStroke(2f); color = Color(0x2C3550); draw(rr(x, y, cw, ch, 22.0))
                color = INDIGO; fill(rr(x + 18, y + 18, 54.0, 54.0, 14.0))
                text("${i + 1}", x + 45, y + 56, 28f, INK, bold = true, center = true)
                text("Canais ${i * 100 + 1}–${(i + 1) * 100}", x + 86, y + 52, 19f, MUTED)
                val nameT = p(t, 4.2 + i * 0.6, 5.0 + i * 0.6)
                val nm = names[i].take((nameT * names[i].length).toInt())
                color = Color(0x10141E); fill(rr(x + 18, y + 92, cw - 36, 50.0, 12.0))
                text(nm.ifEmpty { if (names[i].isEmpty()) "(sem nome)" else "" }, x + 32, y + 126, 22f, if (nm.isEmpty()) Color(0x56607A) else CYAN, bold = nm.isNotEmpty())
                if (nameT in 0.01..0.99 && (t * 3).toInt() % 2 == 0) { color = CYAN; fill(Rectangle2D.Double(x + 34 + textWidth(nm, 22f, true), y + 104, 3.0, 28.0)) }
                val used = listOf(3, 1, 2, 1, 0, 0, 0, 0, 0, 0)[i]
                val shown = if (t > 3.6) used else 0
                text("$shown em uso", x + 18, y + 190, 19f, if (shown > 0) INK else Color(0x56607A))
                for (k in 0 until shown) { color = alpha(TEAL, 0.9); fill(rr(x + 150 + k * 28, y + 174, 20.0, 20.0, 6.0)) }
            }
        }
        // channel chips flying into banks 1–4
        val chips = listOf("146.520" to 0, "147.000" to 0, "145.800" to 0, "118.100" to 1, "439.500" to 2, "146.940" to 2, "462.562" to 3)
        chips.forEachIndexed { k, (f, bank) ->
            val u = easeInOut(p(t, 1.6 + k * 0.25, 2.6 + k * 0.25))
            if (u <= 0 || u >= 1) return@forEachIndexed
            val sx = 120.0; val sy = 330.0 + k * 70
            val col = bank % 5; val row = bank / 5
            val ex = gx + col * (cw + 26) + 160; val ey = gy + row * (ch + 30) + 184
            val x = lerp(sx, ex, u); val y = lerp(sy, ey, u) - sin(u * PI) * 120
            color = alpha(PANEL2, 1.0); fill(rr(x - 70, y - 22, 140.0, 44.0, 22.0))
            stroke = BasicStroke(2f); color = TEAL; draw(rr(x - 70, y - 22, 140.0, 44.0, 22.0))
            text(f, x, y + 8, 20f, CYAN, bold = true, center = true)
        }
    }
}

/** 36–50 s: pick a logo, pixelate to 128x160, send over the cable, radio boots with it. */
private fun Graphics2D.sceneLogo(t: Double) {
    val fade = easeOut(p(t, 0.0, 0.6)) * (1 - p(t, L_LOGO - 0.6, L_LOGO))
    withAlpha(fade) {
        caption("Logo da tela ao ligar", "UV-21 Pro GPS: imagem 128 × 160, escolhida e enviada em segundos", t, L_LOGO)
        val names = LOGOS.keys.toList()
        val gx = 140.0; val gy = 280.0; val tw = 190.0; val th = 238.0
        val pick = 0
        val galleryA = 1 - p(t, 4.0, 4.6)
        // gallery
        withAlpha(galleryA) {
            color = PANEL; fill(rr(gx - 30, gy - 30, 4 * (tw + 20) + 40, 2 * (th + 20) + 50, 24.0))
            names.forEachIndexed { i, n ->
                val a = easeOut(p(t, 0.4 + i * 0.07, 0.9 + i * 0.07))
                val x = gx + (i % 4) * (tw + 20); val y = gy + (i / 4) * (th + 20)
                withAlpha(a) {
                    color = Color.BLACK; fill(rr(x, y, tw, th, 12.0))
                    drawImage(LOGOS.getValue(n), (x + 5).toInt(), (y + (th - tw) / 2 + 5).toInt(), (tw - 10).toInt(), (tw - 10).toInt(), null)
                    if (i == pick && t > 2.3) { stroke = BasicStroke(5f); color = CYAN; draw(rr(x - 3, y - 3, tw + 6, th + 6, 14.0)) }
                }
            }
            // cursor moves to the first thumbnail and clicks
            val m = easeInOut(p(t, 1.2, 2.2))
            val cx = lerp(900.0, gx + tw * 0.6, m); val cy = lerp(800.0, gy + th * 0.6, m)
            cursor(cx, cy, p(t, 2.2, 2.8))
        }
        // pixelated 128x160 preview growing in the centre
        val pv = easeOut(p(t, 3.6, 4.6)) * (1 - p(t, 7.0, 7.6))
        if (pv > 0) withAlpha(pv) {
            val scale = 3.6
            val w = 128 * scale; val h = 160 * scale
            val x = W / 2.0 - w / 2 - 200; val y = 280.0
            val old = getRenderingHint(RenderingHints.KEY_INTERPOLATION)
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            drawImage(LOGO_128x160, x.toInt(), y.toInt(), w.toInt(), h.toInt(), null)
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, old)
            // pixel grid flash
            val g = p(t, 4.4, 5.6) * (1 - p(t, 5.6, 6.4))
            if (g > 0) {
                stroke = BasicStroke(1f); color = Color(255, 255, 255, (40 * g).toInt())
                var gx2 = x; while (gx2 <= x + w) { draw(Line2D.Double(gx2, y, gx2, y + h)); gx2 += scale * 4 }
                var gy2 = y; while (gy2 <= y + h) { draw(Line2D.Double(x, gy2, x + w, gy2)); gy2 += scale * 4 }
            }
            stroke = BasicStroke(3f); color = alpha(CYAN, 0.8); draw(Rectangle2D.Double(x, y, w, h))
            text("128 × 160 · RGB565 · 40 960 bytes", x + w + 40, y + 120, 30f, INK, bold = true)
            text("Pré-visualização exata antes de enviar", x + w + 40, y + 168, 24f, MUTED)
        }
        // radio + cable + transfer
        val ra = easeOut(p(t, 6.6, 7.6))
        if (ra > 0) withAlpha(ra) {
            val rx = 1220.0 + (1 - ra) * 200
            val send = p(t, 7.6, 10.4)
            val boot = p(t, 10.8, 11.6)
            radio(rx, 390.0, 0.85, "UV-21 Pro GPS") { r ->
                color = Color.BLACK; fill(r)
                if (send in 0.001..0.999) {
                    text("Gravando…", r.x + 30, r.y + 150, 26f, AMBER, bold = true)
                }
                if (boot > 0) {
                    withAlpha(easeOut(boot)) { drawImage(LOGO_128x160, r.x.toInt(), r.y.toInt(), r.width.toInt(), r.height.toInt(), null) }
                    val flash = 1 - p(t, 10.8, 11.3)
                    if (flash > 0) { color = Color(255, 255, 255, (200 * flash).toInt()); fill(r) }
                }
            }
            val laptopX = 160.0
            laptop(laptopX, 360.0, 700.0) { scr ->
                val c = appWindow(scr.x + 16, scr.y + 16, scr.width - 32, scr.height - 32)
                drawImage(LOGO_128x160, (c.x + 40).toInt(), (c.y + 24).toInt(), 128 * 2, 160 * 2, null)
                val px = c.x + 330
                text("Logo da tela ao ligar", px, c.y + 60, 24f, INK, bold = true)
                text("Modelo: Baofeng UV-21 Pro", px, c.y + 100, 18f, MUTED)
                progressBar(px, c.y + 140, c.width - 370, send, if (send >= 1.0) "Logo gravada!" else "Gravando a logo…  ${(send * 43).toInt()}/43")
            }
            val c = CubicCurve2D.Double(laptopX + 700, 360.0 + 400, 1000.0, 1000.0, 1100.0, 620.0, rx + 4, 390.0 + 250)
            cable(c, t, if (send in 0.001..0.999) 1.0 else 0.2, reverse = true)
        }
        val ok = easeOut(p(t, 11.8, 12.4))
        withAlpha(ok) {
            val msg = "Pronto! A nova logo aparece ao ligar o rádio."
            val bw = textWidth(msg, 30f, true) + 60.0
            color = TEAL; fill(rr(W / 2.0 - bw / 2 - 160, 960.0, bw, 64.0, 32.0))
            text(msg, W / 2.0 - 160, 1003.0, 30f, Color(0x04201D), bold = true, center = true)
        }
    }
}

/** 50–56 s: Windows installer and web version side by side. */
private fun Graphics2D.scenePlatforms(t: Double) {
    val fade = easeOut(p(t, 0.0, 0.6)) * (1 - p(t, L_PLAT - 0.6, L_PLAT))
    withAlpha(fade) {
        caption("No Windows ou no navegador", "Mesmas funções, mesmo formato de arquivos do CHIRP", t, L_PLAT)
        val a1 = easeOut(p(t, 0.3, 1.1)); val a2 = easeOut(p(t, 0.7, 1.5))
        withAlpha(a1) {
            val x = 180.0 - (1 - a1) * 120; val y = 290.0
            color = PANEL; fill(rr(x, y, 720.0, 560.0, 28.0))
            // installer card
            color = INDIGO; fill(rr(x + 50, y + 60, 150.0, 150.0, 30.0))
            drawImage(ICON, (x + 65).toInt(), (y + 75).toInt(), 120, 120, null)
            text("Windows", x + 230, y + 120, 44f, INK, bold = true)
            text("Instalador com um clique", x + 232, y + 170, 26f, MUTED)
            color = Color(0x10141E); fill(rr(x + 50, y + 260, 620.0, 90.0, 18.0))
            text("CHIRP-REA-1.0.1.exe", x + 80, y + 316, 28f, CYAN, bold = true)
            val d = p(t, 1.6, 3.4)
            progressBar(x + 50, y + 390, 620.0, d, if (d >= 1.0) "Instalado — sem precisar de Java" else "Instalando…")
        }
        withAlpha(a2) {
            val x = 1020.0 + (1 - a2) * 120; val y = 290.0
            color = PANEL; fill(rr(x, y, 720.0, 560.0, 28.0))
            color = Color(0x232838); fill(rr(x, y, 720.0, 70.0, 28.0)); fill(Rectangle2D.Double(x, y + 40, 720.0, 30.0))
            for (k in 0 until 3) { color = listOf(Color(0xFF5F57), Color(0xFEBC2E), Color(0x28C840))[k]; fill(Ellipse2D.Double(x + 26 + k * 28, y + 26, 16.0, 16.0)) }
            color = Color(0x10141E); fill(rr(x + 130, y + 16, 560.0, 38.0, 19.0))
            text("luanhroliveira.github.io/chirp-rea", x + 150, y + 43, 20f, INK)
            val c = appWindow(x + 30, y + 92, 660.0, 300.0)
            drawImage(ICON, (c.x + 270).toInt(), (c.y + 40).toInt(), 120, 120, null)
            text("Rodando no navegador", c.x + 330, c.y + 200, 22f, MUTED, center = true)
            text("Web", x + 40, y + 450, 44f, INK, bold = true)
            text("Chrome e Edge · Web Serial · sem instalar", x + 42, y + 500, 26f, MUTED)
        }
    }
}

/** 56–61 s: closing card. */
private fun Graphics2D.sceneOutro(t: Double) {
    val a = easeOut(p(t, 0.0, 0.8)) * (1 - p(t, L_OUTRO - 0.7, L_OUTRO))
    withAlpha(a) {
        val cx = W / 2.0
        rings(cx, 400.0, t, 420.0, CYAN, 4, 0.5)
        icon(cx, 400.0, 240.0 * (0.9 + 0.1 * easeOut(p(t, 0.0, 0.8))))
        text("CHIRP-REA", cx, 640.0, 96f, INK, bold = true, center = true)
        text("github.com/luanhroliveira/chirp-rea", cx, 720.0, 40f, CYAN, center = true)
        text("Código aberto · GPL-3.0 · Baseado no CHIRP", cx, 780.0, 28f, MUTED, center = true)
    }
}

private fun renderFrame(img: BufferedImage, t: Double) {
    val g = img.createGraphics()
    g.setup()
    g.background(t)
    when {
        t < SCENE_STARTS[1] -> g.sceneIntro(t)
        t < SCENE_STARTS[2] -> g.sceneConnect(t - SCENE_STARTS[1])
        t < SCENE_STARTS[3] -> g.sceneMemories(t - SCENE_STARTS[2])
        t < SCENE_STARTS[4] -> g.sceneBanks(t - SCENE_STARTS[3])
        t < SCENE_STARTS[5] -> g.sceneLogo(t - SCENE_STARTS[4])
        t < SCENE_STARTS[6] -> g.scenePlatforms(t - SCENE_STARTS[5])
        else -> g.sceneOutro(t - SCENE_STARTS[6])
    }
    // global fade in/out
    val f = max(1 - p(t, 0.0, 0.6), p(t, DURATION - 0.7, DURATION))
    if (f > 0) { g.color = Color(0, 0, 0, (255 * f).toInt()); g.fillRect(0, 0, W, H) }
    g.dispose()
}

fun main(args: Array<String>) {
    val outDir = File(args.getOrElse(0) { "build/promo" }).also { it.mkdirs() }
    val ffmpeg = args.getOrElse(1) { "ffmpeg" }
    val stills = args.getOrNull(2)?.takeIf { it.isNotBlank() }?.split(',')?.map { it.trim().toDouble() }
    val img = BufferedImage(W, H, BufferedImage.TYPE_3BYTE_BGR)

    if (stills != null) {
        for (s in stills) {
            renderFrame(img, s)
            ImageIO.write(img, "png", File(outDir, "still-${"%05.1f".format(s).replace(',', '.')}.png"))
        }
        println("stills written to ${outDir.absolutePath}")
        return
    }

    // Optional narration: <dir>/1.wav … 7.wav, one per scene, placed at scene start + offset.
    val narration = args.getOrNull(3)?.takeIf { it.isNotBlank() }?.let(::File)
        ?.let { dir -> (1..SCENE_LENGTHS.size).map { File(dir, "$it.wav") }.takeIf { l -> l.all(File::isFile) } }
    val audioArgs = if (narration == null) emptyList() else buildList {
        narration.forEach { add("-i"); add(it.absolutePath) }
        val delays = narration.indices.joinToString(";") { i ->
            val ms = ((SCENE_STARTS[i] + NARRATION_OFFSETS[i]) * 1000).roundToInt()
            "[${i + 1}:a]aresample=48000,adelay=$ms:all=1[n$i]"
        }
        val mix = narration.indices.joinToString("") { "[n$it]" } +
            "amix=inputs=${narration.size}:normalize=0:dropout_transition=0,volume=1.6,alimiter=limit=0.89:level=disabled,apad,atrim=0:$DURATION[aout]"
        add("-filter_complex"); add("$delays;$mix")
        add("-map"); add("0:v"); add("-map"); add("[aout]")
        add("-c:a"); add("aac"); add("-b:a"); add("160k")
    }
    println(if (narration == null) "no narration (video only)" else "narration: ${narration.size} lines")

    val out = File(outDir, "chirp-rea-promo.mp4")
    val proc = ProcessBuilder(
        listOf(ffmpeg, "-y", "-loglevel", "error",
            "-f", "rawvideo", "-pix_fmt", "bgr24", "-s", "${W}x$H", "-r", "$FPS", "-i", "-") +
            audioArgs +
            listOf("-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
                out.absolutePath),
    ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.INHERIT).start()
    val buf = (img.raster.dataBuffer as DataBufferByte).data
    val frames = (DURATION * FPS).toInt()
    proc.outputStream.buffered(1 shl 20).use { os ->
        for (i in 0 until frames) {
            renderFrame(img, i.toDouble() / FPS)
            os.write(buf)
            if (i % (FPS * 5) == 0) println("frame $i/$frames")
        }
    }
    check(proc.waitFor() == 0) { "ffmpeg failed" }
    println("video written to ${out.absolutePath}")
}

