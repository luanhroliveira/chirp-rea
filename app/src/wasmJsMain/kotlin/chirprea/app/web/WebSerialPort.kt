package chirprea.app.web

import chirprea.core.radio.RadioException
import chirprea.core.radio.SerialPort
import kotlinx.coroutines.await
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

/** JavaScript object wrapping a Web Serial port with a buffered background reader. */
external interface WebSerialHandle : JsAny {
    val label: String
    fun write(data: Uint8Array): Promise<JsAny?>
    fun read(count: Int, timeoutMs: Int): Promise<Uint8Array>
    fun flush()
    fun setSignals(dtr: Boolean, rts: Boolean): Promise<JsAny?>
    fun setBaud(baud: Int): Promise<JsAny?>
    fun close(): Promise<JsAny?>
}

external interface OpenResult : JsAny {
    val error: String?
    val handle: WebSerialHandle?
}

/**
 * Asks the user to pick a port (browser dialog) and opens it. Must run from a
 * user gesture (button click). Bytes arriving from the radio are buffered in JS
 * so reads with a timeout behave like on the desktop.
 */
@JsFun(
    """async (baud) => {
        try {
            if (!('serial' in navigator)) {
                return { error: 'Este navegador não tem Web Serial. Use o Google Chrome ou o Microsoft Edge no computador.' };
            }
            const port = await navigator.serial.requestPort();
            const h = { buf: [], baud: baud, closing: false, reader: null, loop: null };
            const start = async (b) => {
                await port.open({ baudRate: b, dataBits: 8, stopBits: 1, parity: 'none', bufferSize: 65536 });
                h.baud = b;
                h.loop = (async () => {
                    while (port.readable && !h.closing) {
                        h.reader = port.readable.getReader();
                        try {
                            for (;;) {
                                const { value, done } = await h.reader.read();
                                if (done) break;
                                for (const x of value) h.buf.push(x);
                            }
                        } catch (e) {
                        } finally {
                            h.reader.releaseLock();
                        }
                    }
                })();
            };
            const stop = async () => {
                h.closing = true;
                try { if (h.reader) await h.reader.cancel(); } catch (e) {}
                try { await h.loop; } catch (e) {}
                try { await port.close(); } catch (e) {}
                h.closing = false;
            };
            h.write = async (data) => {
                const w = port.writable.getWriter();
                try { await w.write(data); } finally { w.releaseLock(); }
            };
            h.read = async (count, timeoutMs) => {
                const end = Date.now() + timeoutMs;
                while (h.buf.length < count && Date.now() < end) {
                    await new Promise(r => setTimeout(r, 4));
                }
                return new Uint8Array(h.buf.splice(0, Math.min(count, h.buf.length)));
            };
            h.flush = () => { h.buf = []; };
            h.setSignals = async (dtr, rts) => {
                try { await port.setSignals({ dataTerminalReady: dtr, requestToSend: rts }); } catch (e) {}
            };
            h.setBaud = async (b) => { if (b !== h.baud) { await stop(); await start(b); } };
            h.close = stop;
            const info = port.getInfo();
            h.label = info.usbVendorId
                ? 'USB ' + info.usbVendorId.toString(16).padStart(4, '0') + ':' + info.usbProductId.toString(16).padStart(4, '0')
                : 'Porta serial';
            await start(baud);
            return { handle: h };
        } catch (e) {
            if (e && e.name === 'NotFoundError') return { error: 'Nenhuma porta selecionada.' };
            if (e && e.name === 'InvalidStateError') return { error: 'A porta já está em uso (feche outros programas, como o CHIRP).' };
            return { error: (e && e.message) ? e.message : String(e) };
        }
    }""",
)
private external fun openSerialJs(baud: Int): Promise<OpenResult>

/** [SerialPort] over the Web Serial API (Chrome/Edge on desktop, HTTPS only). */
class WebSerialPort private constructor(private val h: WebSerialHandle, private var openBaud: Int) : SerialPort {
    override val name: String get() = h.label

    /** Web Serial can't change speed on an open port; the change is applied (reopen) before the next I/O. */
    override var baudRate: Int = openBaud

    private suspend fun applyBaud() {
        if (baudRate != openBaud) {
            h.setBaud(baudRate).await<JsAny?>()
            openBaud = baudRate
        }
    }

    override suspend fun write(bytes: ByteArray) {
        applyBaud()
        h.write(bytes.toUint8Array()).await<JsAny?>()
    }

    override suspend fun read(count: Int, timeoutMs: Long): ByteArray {
        applyBaud()
        return h.read(count, timeoutMs.toInt()).await<Uint8Array>().toByteArray()
    }

    override suspend fun flushInput() {
        applyBaud()
        h.flush()
    }

    override suspend fun setDtr(on: Boolean) { h.setSignals(on, true).await<JsAny?>() }
    override suspend fun setRts(on: Boolean) { h.setSignals(true, on).await<JsAny?>() }

    override suspend fun close() { h.close().await<JsAny?>() }

    companion object {
        /** Shows the browser's port chooser and opens the selected port at [baud]. */
        suspend fun request(baud: Int): WebSerialPort {
            val r = openSerialJs(baud).await<OpenResult>()
            val handle = r.handle ?: throw RadioException(r.error ?: "Não foi possível abrir a porta serial")
            return WebSerialPort(handle, baud)
        }
    }
}
