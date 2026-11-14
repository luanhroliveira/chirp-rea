package chirprea.app

import chirprea.core.drivers.baofeng.BaofengUV17ProGPS
import chirprea.core.drivers.baofeng.BaofengUV21ProGPS
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LogoProcessingTest {
    private val uv21 = BaofengUV21ProGPS().logoSpec   // 128x160
    private val uv17 = BaofengUV17ProGPS().logoSpec   // 150x128

    /** A solid white landscape picture, 300x100. */
    private fun landscape() = BufferedImage(300, 100, BufferedImage.TYPE_INT_RGB).apply {
        createGraphics().apply { color = Color.WHITE; fillRect(0, 0, 300, 100); dispose() }
    }

    @Test
    fun sameSourceRendersForEachModelSize() {
        val src = landscape()
        assertEquals(128 to 160, LogoProcessing.render(src, uv21, LogoFit.Fit).let { it.width to it.height })
        assertEquals(150 to 128, LogoProcessing.render(src, uv17, LogoFit.Fit).let { it.width to it.height })
        assertEquals(uv21.byteCount, LogoProcessing.rgb565(src, uv21, LogoFit.Fit).size)
        assertEquals(uv17.byteCount, LogoProcessing.rgb565(src, uv17, LogoFit.Fit).size)
    }

    @Test
    fun fitLetterboxesAndFillCovers() {
        val src = landscape()
        // 300x100 into 128x160 with Fit -> 128x43 band in the middle, black above/below.
        val fit = LogoProcessing.render(src, uv21, LogoFit.Fit)
        assertEquals(Color.BLACK.rgb, fit.getRGB(64, 5))
        assertEquals(Color.WHITE.rgb, fit.getRGB(64, 80))
        // Fill covers the whole area.
        val fill = LogoProcessing.render(src, uv21, LogoFit.Fill)
        assertEquals(Color.WHITE.rgb, fill.getRGB(64, 5))
        assertEquals(Color.WHITE.rgb, fill.getRGB(2, 158))
    }

    @Test
    fun transparencyBecomesBlack() {
        val src = BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB) // fully transparent
        assertEquals(Color.BLACK.rgb, LogoProcessing.render(src, uv21, LogoFit.Stretch).getRGB(10, 10))
    }

    @Test
    fun everyBundledLogoIsListedReadableAndLicensed() {
        val bundled = LogoLibrary.bundled()
        kotlin.test.assertTrue(bundled.size >= 8, "expected the generated logos, got ${bundled.map { it.name }}")
        val licences = LogoLibrary::class.java.getResource("/logos/LOGOS.md")!!.readText()
        for (e in bundled) {
            val img = assertNotNull(LogoLibrary.original(e), "cannot read ${e.name}")
            assertEquals(800, img.width)
            kotlin.test.assertTrue("`${e.name}`" in licences, "${e.name} has no author/licence row in LOGOS.md")
            assertEquals(uv21.byteCount, LogoProcessing.rgb565(img, uv21, LogoFit.Fit).size)
        }
    }

    @Test
    fun readsJpegWithJfifExtension() {
        val f = File.createTempFile("logo", ".jfif")
        try {
            ImageIO.write(landscape(), "jpg", f)
            val img = assertNotNull(LogoProcessing.read(f))
            assertEquals(300, img.width)
        } finally {
            f.delete()
        }
    }
}
