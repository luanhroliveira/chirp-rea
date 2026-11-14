package chirprea.app.web

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import chirprea.app.ui.ChirpTheme
import chirprea.core.drivers.Drivers
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    Drivers.registerAll()
    ComposeViewport(document.body!!) {
        ChirpTheme(dark = isSystemInDarkTheme()) {
            WebApp()
        }
    }
}
