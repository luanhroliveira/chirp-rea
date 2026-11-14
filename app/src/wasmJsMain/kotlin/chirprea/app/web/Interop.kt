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

@JsFun("(url) => window.open(url, '_blank', 'noopener')")
external fun openInNewTab(url: String)

@JsFun("() => 'serial' in navigator")
external fun hasWebSerial(): Boolean
