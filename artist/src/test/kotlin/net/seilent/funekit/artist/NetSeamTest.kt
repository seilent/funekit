package net.seilent.funekit.artist

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

class NetSeamTest {
    private val open = UrlHttp.open

    @After
    fun restore() {
        UrlHttp.open = open
    }

    @Test
    fun aReplacedOpenStopsARealHttpBeforeAnyConnection() {
        val blocked = ArrayList<String>()
        UrlHttp.open = {
            blocked += it.toString()
            throw IOException("tests never reach the network: $it")
        }
        val url = "https://musicbrainz.org/ws/2/artist/91352441-9f79-48c2-81ad-ccdd1e46915a"
        val get = assertThrows(IOException::class.java) { UrlHttp().get(url, emptyMap()) }
        assertEquals("tests never reach the network: $url", get.message)
        assertThrows(IOException::class.java) { UrlHttp().post(url, emptyMap(), ByteArray(0)) }
        assertThrows(IOException::class.java) { UrlHttp().download(url, emptyMap()) { _, _, _ -> true } }
        assertEquals(listOf(url, url, url), blocked)
    }

    @Test
    fun theTestJvmSendsEveryHostButLoopbackToADeadProxy() {
        for (url in listOf("https://musicbrainz.org/ws/2/", "http://lrclib.net/api/get")) {
            val p = ProxySelector.getDefault().select(URI(url)).single()
            assertEquals(url, Proxy.Type.HTTP, p.type())
            val a = p.address() as InetSocketAddress
            assertEquals(url, "127.0.0.1" to 9, a.hostString to a.port)
        }
        assertEquals(Proxy.NO_PROXY, ProxySelector.getDefault().select(URI("http://127.0.0.1:8080/big")).single())
    }
}
