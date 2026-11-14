package chirprea.app

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import javax.imageio.ImageIO

/** CHIRP-REA icon for windows, dialogs and the taskbar (replaces the default Java cup). */
object AppIcon {
    val painter: Painter by lazy {
        val img = AppIcon::class.java.getResourceAsStream("/icon/chirp-rea-256.png")!!.use(ImageIO::read)
        BitmapPainter(img.toComposeImageBitmap())
    }
}
