package net.seilent.funekit.artist

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PushbackInputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.util.zip.GZIPInputStream

class HttpDenied(val code: Int, url: String) : IOException("http $code: $url")

class HttpTooBig(val maxBytes: Int, url: String) : IOException("body over $maxBytes bytes: $url")

class HttpTimeout : SocketTimeoutException("read timed out")

class Response(val code: Int, val body: ByteArray, val headers: Map<String, String> = emptyMap()) {
    val text: String get() = String(body, Charsets.UTF_8)
}

data class Fetch(val maxBytes: Int? = null, val redirects: Boolean = true)

fun interface Http {
    fun get(url: String, headers: Map<String, String>): Response

    fun get(url: String, headers: Map<String, String>, fetch: Fetch): Response =
        if (fetch == Fetch()) get(url, headers) else throw IOException("no fetch options: $url")

    fun post(url: String, headers: Map<String, String>, body: ByteArray): Response = throw IOException("no post: $url")

    fun download(url: String, headers: Map<String, String>, sink: (Int, ByteArray, Int) -> Boolean): Response {
        val r = get(url, headers)
        if (r.code !in 200..299) return r
        sink(r.code, r.body, r.body.size)
        return Response(r.code, ByteArray(0), r.headers)
    }

    fun download(url: String, headers: Map<String, String>, fetch: Fetch, sink: (Int, ByteArray, Int) -> Boolean): Response =
        if (fetch == Fetch()) download(url, headers, sink) else throw IOException("no fetch options: $url")
}

class HttpRun(private val onCall: (String) -> Unit = {}) {
    @Volatile var cancelled = false
        private set
    private val open = HashSet<HttpURLConnection>()
    private val hooks = ArrayList<() -> Unit>()
    private val waits = HashMap<Cond, Int>()

    fun cancel() {
        val drop: List<HttpURLConnection>
        val run: List<() -> Unit>
        val wake: List<Cond>
        synchronized(this) {
            cancelled = true
            drop = open.toList().also { open.clear() }
            run = hooks.toList().also { hooks.clear() }
            wake = waits.keys.toList()
        }
        drop.forEach { it.disconnect() }
        run.forEach { it() }
        wake.forEach { it.signalAll() }
    }

    fun onCancel(hook: () -> Unit) {
        val now = synchronized(this) {
            if (!cancelled) hooks += hook
            cancelled
        }
        if (now) hook()
    }

    internal fun enter(c: Cond) = synchronized(this) { waits[c] = (waits[c] ?: 0) + 1 }

    internal fun leave(c: Cond) = synchronized(this) {
        val n = (waits[c] ?: 1) - 1
        if (n <= 0) waits.remove(c) else waits[c] = n
    }

    fun <T> on(block: () -> T): T {
        val prev = local.get()
        local.set(this)
        try {
            return block()
        } finally {
            local.set(prev)
        }
    }

    internal fun call(url: String) {
        if (cancelled) throw IOException("cancelled")
        onCall(url)
    }

    internal fun add(c: HttpURLConnection) = synchronized(this) {
        if (cancelled) throw IOException("cancelled")
        open += c
    }

    internal fun remove(c: HttpURLConnection) = synchronized(this) { open -= c }

    companion object {
        private val local = ThreadLocal<HttpRun?>()

        fun current(): HttpRun? = local.get()
    }
}

class UrlHttp(private val maxBytes: Int = 16 shl 20, private val timeoutMs: Int = 15_000) : Http {
    override fun get(url: String, headers: Map<String, String>): Response = send(url, headers, null, Fetch())

    override fun get(url: String, headers: Map<String, String>, fetch: Fetch): Response = send(url, headers, null, fetch)

    override fun post(url: String, headers: Map<String, String>, body: ByteArray): Response = send(url, headers, body, Fetch())

    override fun download(url: String, headers: Map<String, String>, sink: (Int, ByteArray, Int) -> Boolean): Response = send(url, headers, null, Fetch(), sink)

    override fun download(url: String, headers: Map<String, String>, fetch: Fetch, sink: (Int, ByteArray, Int) -> Boolean): Response = send(url, headers, null, fetch, sink)

    private fun send(url: String, headers: Map<String, String>, body: ByteArray?, fetch: Fetch, sink: ((Int, ByteArray, Int) -> Boolean)? = null): Response {
        val cap = fetch.maxBytes
        val accept = headers.entries.firstOrNull { it.key.equals("Accept-Encoding", ignoreCase = true) }?.value
        require(cap == null || accept == null || accept == "identity") { "Accept-Encoding $accept on a capped call: $url" }
        val limit = cap ?: maxBytes
        val run = HttpRun.current()
        val c = open(URI(url))
        run?.add(c)
        var connected = false
        try {
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.instanceFollowRedirects = fetch.redirects
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (cap != null && accept == null) c.setRequestProperty("Accept-Encoding", "gzip")
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setFixedLengthStreamingMode(body.size)
            }
            c.connect()
            connected = true
            if (body != null) c.outputStream.use { it.write(body) }
            val code = c.responseCode
            val encoding = if (cap == null) null else c.contentEncoding?.lowercase()
            if (encoding != null && encoding != "gzip" && encoding != "identity") throw IOException("content encoding $encoding: $url")
            var inflated = false
            val out = ByteArrayOutputStream()
            var given = 0L
            (if (code >= 400) c.errorStream else c.inputStream)?.use { raw ->
                val stream = if (encoding == "gzip" && code != 204 && code != 304) {
                    val peek = PushbackInputStream(raw, 1)
                    val first = peek.read()
                    if (first >= 0) peek.unread(first)
                    inflated = first >= 0
                    if (inflated) GZIPInputStream(peek) else peek
                } else {
                    raw
                }
                stream.use { input ->
                    val buf = ByteArray(16384)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        if (sink != null && code in 200..299) {
                            if (run?.cancelled == true) throw IOException("cancelled")
                            if (cap != null && given + n > cap) throw HttpTooBig(cap, url)
                            given += n
                            if (!sink(code, buf, n)) break
                            continue
                        }
                        if (out.size() + n > limit) throw HttpTooBig(limit, url)
                        out.write(buf, 0, n)
                    }
                }
            }
            val h = c.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.firstOrNull() ?: "" }
            return Response(code, out.toByteArray(), if (inflated) h - "content-encoding" - "content-length" else h)
        } catch (e: SocketTimeoutException) {
            throw if (connected) HttpTimeout() else e
        } finally {
            run?.remove(c)
            c.disconnect()
        }
    }

    companion object {
        @Volatile var open: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection }
    }
}

class Api(
    private val http: Http,
    private val userAgent: String,
    private val gate: Gate,
    private val tries: Int = 4,
    private val backoffMs: Long = 2000,
    private val maxRetryAfterMs: Long = 60_000,
    private val jitter: () -> Double = { 0.0 },
) {
    constructor(
        http: Http,
        userAgent: String,
        limiter: RateLimiter,
        sleep: (Long) -> Unit,
        tries: Int = 4,
        backoffMs: Long = 2000,
        maxRetryAfterMs: Long = 60_000,
        jitter: () -> Double = { 0.0 },
    ) : this(http, userAgent, PlainGate(limiter, sleep), tries, backoffMs, maxRetryAfterMs, jitter)

    fun get(url: String): Response? = send(url) { http.get(it, mapOf("User-Agent" to userAgent)) }

    fun post(url: String, form: Map<String, String>, headers: Map<String, String>): Response? {
        val body = form.entries.joinToString("&") { (k, v) -> formEnc(k) + "=" + formEnc(v) }.toByteArray()
        return send(url) { http.post(it, mapOf("User-Agent" to userAgent) + headers, body) }
    }

    fun download(url: String, maxBytes: Int, stop: (ByteArray, Int) -> Boolean): Response? = send(url) { u ->
        val out = Body()
        val r = http.download(u, mapOf("User-Agent" to userAgent)) { _, buf, n ->
            if (out.size() + n > maxBytes) throw HttpTooBig(maxBytes, u)
            out.write(buf, 0, n)
            !stop(out.bytes, out.size())
        }
        if (r.code in 200..299) Response(r.code, out.toByteArray(), r.headers) else r
    }

    private class Body : ByteArrayOutputStream() {
        val bytes: ByteArray get() = buf
    }

    private fun send(url: String, call: (String) -> Response): Response? {
        val u = if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
        val limiter = gate.limiter(u)
        repeat(tries) { attempt ->
            val wait = limiter.reserve()
            if (wait > 0) gate.pause(wait)
            HttpRun.current()?.call(u)
            val r = gate.call(u) { call(u) }
            when {
                r.code in 200..299 -> return r
                r.code == 503 || r.code == 429 -> {
                    val after = r.headers["retry-after"]?.trim()?.toLongOrNull()?.times(1000)
                    if (after != null && after > maxRetryAfterMs) throw IOException("retry after ${after / 1000} s: $u")
                    limiter.backoff(((after ?: (backoffMs shl attempt)) * (1.0 + jitter())).toLong())
                }
                r.code == 401 || r.code == 403 -> throw HttpDenied(r.code, u)
                r.code in 100..499 -> return null
                else -> throw IOException("http ${r.code}: $u")
            }
        }
        throw IOException("busy after $tries tries: $u")
    }

    fun json(url: String): J? = get(url)?.let {
        try {
            J.parse(it.text)
        } catch (e: IllegalArgumentException) {
            throw IOException("${e.message}: $url")
        }
    }

    companion object {
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        fun formEnc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("*", "%2A").replace("%7E", "~")
    }
}
