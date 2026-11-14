package chirprea.app.web

import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import kotlin.js.Promise

// Small bridges to browser APIs (file picker, downloads, fetch, Web Serial).

fun ByteArray.toUint8Array(): Uint8Array {
    val u = Uint8Array(size)
    for (i in indices) u[i] = this[i]
    return u
}

fun Uint8Array.toByteArray(): ByteArray = ByteArray(length) { this[it] }

/** A file chosen by the user in the browser's file picker. */
external interface PickedFile : JsAny {
    val name: String
    val data: Uint8Array
}

@JsFun(
    """(accept) => new Promise((resolve) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = accept;
        input.onchange = async () => {
            const f = input.files && input.files[0];
            if (!f) { resolve(null); return; }
            resolve({ name: f.name, data: new Uint8Array(await f.arrayBuffer()) });
        };
        input.oncancel = () => resolve(null);
        input.click();
    })""",
)
private external fun pickFileJs(accept: String): Promise<PickedFile?>

/** Opens the browser's file picker; null if the user cancels. */
suspend fun pickFile(accept: String): Pair<String, ByteArray>? =
    pickFileJs(accept).await<PickedFile?>()?.let { it.name to it.data.toByteArray() }

@JsFun(
    """(name, data, mime) => {
        const url = URL.createObjectURL(new Blob([data], { type: mime }));
        const a = document.createElement('a');
        a.href = url; a.download = name;
        document.body.appendChild(a); a.click(); a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 2000);
    }""",
)
private external fun downloadJs(name: String, data: Uint8Array, mime: String)

/** Saves [data] through a browser download named [name]. */
fun download(name: String, data: ByteArray, mime: String = "application/octet-stream") =
    downloadJs(name, data.toUint8Array(), mime)

@JsFun("(url) => fetch(url).then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.text(); })")
private external fun fetchTextJs(url: String): Promise<JsString>

/** Fetches a file published next to the page (e.g. stock_configs/index.txt). */
suspend fun fetchText(url: String): String = fetchTextJs(url).await<JsString>().toString()

@JsFun("(url) => fetch(url).then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.arrayBuffer(); }).then(b => new Uint8Array(b))")
private external fun fetchBytesJs(url: String): Promise<Uint8Array>

/** Fetches a binary file published next to the page (e.g. logos/fogueira.png). */
suspend fun fetchBytes(url: String): ByteArray = fetchBytesJs(url).await<Uint8Array>().toByteArray()

/**
 * Decodes an image (PNG, JPEG/JFIF, BMP, GIF…) with the browser and draws it into
 * a w×h black canvas: fit 0 = fit with black borders, 1 = fill/crop, 2 = stretch.
 * Returns RGBA bytes (w*h*4). Same framing rules as the desktop app.
 */
@JsFun(
    """async (data, w, h, fit) => {
        const bmp = await createImageBitmap(new Blob([data]));
        const c = new OffscreenCanvas(w, h);
        const g = c.getContext('2d');
        g.fillStyle = '#000';
        g.fillRect(0, 0, w, h);
        g.imageSmoothingEnabled = true;
        g.imageSmoothingQuality = 'high';
        if (fit === 2) {
            g.drawImage(bmp, 0, 0, w, h);
        } else {
            const sx = w / bmp.width, sy = h / bmp.height;
            const k = fit === 1 ? Math.max(sx, sy) : Math.min(sx, sy);
            const dw = Math.round(bmp.width * k), dh = Math.round(bmp.height * k);
            g.drawImage(bmp, Math.floor((w - dw) / 2), Math.floor((h - dh) / 2), dw, dh);
        }
        bmp.close();
        return new Uint8Array(g.getImageData(0, 0, w, h).data.buffer);
    }""",
)
private external fun renderImageJs(data: Uint8Array, w: Int, h: Int, fit: Int): Promise<Uint8Array>

/** Renders [image] for a w×h logo; returns ARGB pixels (row-major), as BaofengLogo.toRgb565 expects. */
suspend fun renderImage(image: ByteArray, w: Int, h: Int, fit: Int): IntArray {
    val rgba = renderImageJs(image.toUint8Array(), w, h, fit).await<Uint8Array>().toByteArray()
    return IntArray(w * h) { i ->
        val r = rgba[4 * i].toInt() and 0xFF
        val g = rgba[4 * i + 1].toInt() and 0xFF
        val b = rgba[4 * i + 2].toInt() and 0xFF
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

@JsFun("(url) => window.open(url, '_blank', 'noopener')")
external fun openInNewTab(url: String)

@JsFun("() => 'serial' in navigator")
external fun hasWebSerial(): Boolean
