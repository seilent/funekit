package net.seilent.funekit.artist

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPOutputStream

class FetchTest {
    private val open = UrlHttp.open
    private val wires = ArrayList<Wire>()
    private val url = "https://example.org/list"
    private val n = 50_000

    @After
    fun restore() {
        UrlHttp.open = open
    }

    private class Wire(uri: URI, private val serve: Wire.() -> Unit) : HttpURLConnection(uri.toURL()) {
        var status = 200
        var bytes = ByteArray(0)
        val reply = LinkedHashMap<String, String>()
        var accept: String? = null
        var follow = false

        override fun connect() {
            accept = getRequestProperty("Accept-Encoding")
            follow = instanceFollowRedirects
            serve()
        }

        override fun disconnect() {}

        override fun usingProxy() = false

        override fun getResponseCode() = status

        override fun getInputStream(): InputStream = if (status >= 400) throw IOException("http $status") else ByteArrayInputStream(bytes)

        override fun getErrorStream(): InputStream? = if (status >= 400) ByteArrayInputStream(bytes) else null

        override fun getHeaderField(name: String?): String? = reply.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        override fun getHeaderFields(): Map<String, List<String>> = reply.mapValues { listOf(it.value) }
    }

    private fun serve(block: Wire.() -> Unit) {
        UrlHttp.open = { Wire(it, block).also { w -> wires += w } }
    }

    private fun body(size: Int) = ByteArray(size) { (it % 16).toByte() }

    private fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(b) } }.toByteArray()

    @Test
    fun theOldPathFollowsRedirectsAndHandsAGzipBodyOverUntouched() {
        val zipped = gzip(body(1000))
        serve {
            reply["Content-Encoding"] = "gzip"
            bytes = zipped
        }
        for (r in listOf(UrlHttp().get(url, emptyMap()), UrlHttp().get(url, emptyMap(), Fetch()))) {
            assertArrayEquals(zipped, r.body)
            assertEquals("gzip", r.headers["content-encoding"])
        }
        assertEquals(listOf(true, true), wires.map { it.follow })
        assertEquals(listOf<String?>(null, null), wires.map { it.accept })
        serve { bytes = body(101) }
        val e = assertThrows(HttpTooBig::class.java) { UrlHttp(maxBytes = 100).get(url, emptyMap()) }
        assertEquals(100, e.maxBytes)
        assertEquals("body over 100 bytes: $url", e.message)
        serve { bytes = body(100) }
        assertEquals(100, UrlHttp(maxBytes = 100).get(url, emptyMap()).body.size)
    }

    @Test
    fun aCapCountsTheInflatedBytes() {
        assertTrue(gzip(body(n + 1)).size < n / 10)
        for (code in listOf(200, 404)) {
            serve {
                status = code
                bytes = gzip(body(n))
                reply["Content-Encoding"] = "gzip"
                reply["Content-Length"] = "${bytes.size}"
                reply["X-Tag"] = "t"
            }
            val r = UrlHttp().get(url, emptyMap(), Fetch(maxBytes = n))
            assertEquals("gzip", wires.last().accept)
            assertEquals(code, r.code)
            assertArrayEquals(body(n), r.body)
            assertNull(r.headers["content-encoding"])
            assertNull(r.headers["content-length"])
            assertEquals("t", r.headers["x-tag"])
            serve {
                status = code
                bytes = gzip(body(n + 1))
                reply["Content-Encoding"] = "gzip"
            }
            val e = assertThrows("$code", HttpTooBig::class.java) { UrlHttp().get(url, emptyMap(), Fetch(maxBytes = n)) }
            assertEquals(n, e.maxBytes)
        }
    }

    @Test
    fun aRedirectComesBackWhenRedirectsAreOff() {
        serve {
            if (follow) {
                bytes = body(10)
            } else {
                status = 302
                reply["Location"] = "https://example.org/next"
                bytes = "moved".toByteArray()
            }
        }
        val r = UrlHttp().get(url, emptyMap(), Fetch(redirects = false))
        assertEquals(false, wires.single().follow)
        assertEquals(302, r.code)
        assertEquals("https://example.org/next", r.headers["location"])
        assertEquals("moved", r.text)
    }

    @Test
    fun aDownloadWithRedirectsOffNeverHandsTheSinkARedirect() {
        serve {
            if (follow) {
                bytes = body(10)
            } else {
                status = 302
                reply["Location"] = "https://example.org/next"
            }
        }
        var calls = 0
        val r = UrlHttp().download(url, emptyMap(), Fetch(redirects = false)) { _, _, _ ->
            calls++
            true
        }
        assertEquals(302, r.code)
        assertEquals(0, calls)
    }

    @Test
    fun aDownloadCapCountsTheBytesHandedToTheSink() {
        serve { bytes = body(n) }
        var got = 0
        UrlHttp().download(url, emptyMap(), Fetch(maxBytes = n)) { _, _, k ->
            got += k
            true
        }
        assertEquals(n, got)
        serve { bytes = body(n + 1) }
        val e = assertThrows(HttpTooBig::class.java) { UrlHttp().download(url, emptyMap(), Fetch(maxBytes = n)) { _, _, _ -> true } }
        assertEquals(n, e.maxBytes)
        val time = FakeTime()
        val api = Api(Http { _, _ -> Response(200, body(n + 1)) }, "ua", RateLimiter(0) { time.now }, time.sleep)
        val a = assertThrows(HttpTooBig::class.java) { api.download(url, n) { _, _ -> false } }
        assertEquals(n, a.maxBytes)
        assertEquals("body over $n bytes: $url", a.message)
    }

    @Test
    fun aCappedDownloadCountsTheInflatedBytesHandedToTheSink() {
        serve {
            bytes = gzip(body(n))
            reply["Content-Encoding"] = "gzip"
        }
        val got = ByteArrayOutputStream()
        UrlHttp().download(url, emptyMap(), Fetch(maxBytes = n)) { _, buf, k ->
            got.write(buf, 0, k)
            true
        }
        assertArrayEquals(body(n), got.toByteArray())
        serve {
            bytes = gzip(body(n + 1))
            reply["Content-Encoding"] = "gzip"
        }
        val e = assertThrows(HttpTooBig::class.java) { UrlHttp().download(url, emptyMap(), Fetch(maxBytes = n)) { _, _, _ -> true } }
        assertEquals(n, e.maxBytes)
    }

    @Test
    fun aPerCallCapAboveTheInstanceCapIsTaken() {
        serve { bytes = body(n) }
        assertEquals(n, UrlHttp(maxBytes = 100).get(url, emptyMap(), Fetch(maxBytes = n)).body.size)
        var got = 0
        UrlHttp(maxBytes = 100).download(url, emptyMap(), Fetch(maxBytes = n)) { _, _, k ->
            got += k
            true
        }
        assertEquals(n, got)
    }

    @Test
    fun anHttpThatKnowsNoOptionsRefusesThem() {
        val calls = ArrayList<String>()
        val h = Http { u, _ ->
            calls += u
            Response(200, body(10))
        }
        assertEquals(10, h.get(url, emptyMap(), Fetch()).body.size)
        var got = 0
        h.download(url, emptyMap(), Fetch()) { _, _, k ->
            got += k
            true
        }
        assertEquals(10, got)
        assertEquals(listOf(url, url), calls)
        for (f in listOf(Fetch(maxBytes = 10), Fetch(redirects = false))) {
            val get = assertThrows("$f", IOException::class.java) { h.get(url, emptyMap(), f) }
            assertTrue(get.message!!.contains(url))
            val down = assertThrows("$f", IOException::class.java) { h.download(url, emptyMap(), f) { _, _, _ -> true } }
            assertTrue(down.message!!.contains(url))
        }
        assertEquals(listOf(url, url), calls)
    }

    @Test
    fun anIdentityBodyIsCountedAsItComes() {
        for (encoding in listOf(null, "identity")) {
            serve {
                encoding?.let { reply["Content-Encoding"] = it }
                bytes = body(n)
                reply["Content-Length"] = "${bytes.size}"
            }
            val r = UrlHttp().get(url, emptyMap(), Fetch(maxBytes = n))
            assertArrayEquals(body(n), r.body)
            assertEquals("$n", r.headers["content-length"])
            serve {
                encoding?.let { reply["Content-Encoding"] = it }
                bytes = body(n + 1)
            }
            val e = assertThrows("$encoding", HttpTooBig::class.java) { UrlHttp().get(url, emptyMap(), Fetch(maxBytes = n)) }
            assertEquals(n, e.maxBytes)
        }
    }

    @Test
    fun anotherEncodingOnACappedCallIsRefused() {
        serve {
            reply["Content-Encoding"] = "br"
            bytes = body(10)
        }
        val e = assertThrows(IOException::class.java) { UrlHttp().get(url, emptyMap(), Fetch(maxBytes = n)) }
        assertEquals("content encoding br: $url", e.message)
    }

    @Test
    fun aCallerEncodingOtherThanIdentityIsRefusedBeforeAnyRequest() {
        serve { bytes = body(10) }
        for (h in listOf(mapOf("Accept-Encoding" to "br"), mapOf("Accept-Encoding" to "gzip"), mapOf("accept-encoding" to "br"))) {
            val e = assertThrows("$h", IllegalArgumentException::class.java) { UrlHttp().get(url, h, Fetch(maxBytes = n)) }
            assertTrue(e.message!!.contains("Accept-Encoding"))
        }
        assertEquals(0, wires.size)
    }

    @Test
    fun aCallerIdentityGoesOutInPlaceOfGzip() {
        val h = mapOf("Accept-Encoding" to "identity")
        serve { bytes = body(n) }
        assertArrayEquals(body(n), UrlHttp().get(url, h, Fetch(maxBytes = n)).body)
        assertEquals("identity", wires.single().accept)
        serve { bytes = body(n + 1) }
        val e = assertThrows(HttpTooBig::class.java) { UrlHttp().get(url, h, Fetch(maxBytes = n)) }
        assertEquals(n, e.maxBytes)
    }

    @Test
    fun aGzipLabelWithNoBodyIsNotInflated() {
        for (code in listOf(404, 204, 304)) {
            serve {
                status = code
                reply["Content-Encoding"] = "gzip"
            }
            val r = UrlHttp().get(url, emptyMap(), Fetch(maxBytes = n))
            assertEquals(code, r.code)
            assertEquals(0, r.body.size)
        }
        serve {
            status = 204
            reply["Content-Encoding"] = "gzip"
            bytes = "raw".toByteArray()
        }
        val raw = UrlHttp().get(url, emptyMap(), Fetch(maxBytes = n))
        assertEquals(204, raw.code)
        assertEquals("raw", raw.text)
    }
}
