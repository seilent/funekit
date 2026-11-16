package net.seilent.funekit.artist

import java.io.IOException
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

class FakeExtension(override val source: String = "fake", override val sections: List<String> = listOf(source, "$source search")) : ArtExtension {
    data class Page(val url: String) : PickLink.Link

    val calls = CopyOnWriteArrayList<String>()
    var vetoed: Set<String> = emptySet()
    var links: Map<String, PickLink.Link> = emptyMap()
    var linkHosts: Map<String, PickLink.Link> = emptyMap()
    var offered: Map<PickLink.Link, String> = emptyMap()
    var searched: BrowseFeed.Search = BrowseFeed.Search.Unavailable(gated = true)
    var tiles: List<Pair<String, String>> = emptyList()
    var due = false
    var keys: Map<String, String> = emptyMap()
    var resolved: Map<String, ArtExtension.Resolved> = emptyMap()
    var found: List<Candidate> = emptyList()
    var resized: Map<String, String> = emptyMap()
    var resizeFails = false

    override fun identify(name: String): Boolean {
        calls += "identify $name"
        return name !in vetoed
    }

    override fun link(uri: URI, host: String): PickLink.Link? {
        calls += "link $host"
        return linkHosts[host] ?: links[uri.toString()]
    }

    override fun offer(link: PickLink.Link, host: ArtExtension.Host): Offered? {
        val url = offered[link] ?: return null
        calls += "offer $link"
        return host.image(source, url, url.substringAfterLast('/'), null, "page")
    }

    override fun browse(section: String, b: ArtExtension.Browse) {
        calls += "browse $section"
        if (section == source) {
            b.each { a -> tiles.forEach { (preview, full) -> b.tile(source, b.caption(a), preview, full, full) } }
        } else {
            b.search(searched)
        }
    }

    override fun due(kept: Map<String, String>, now: Long): Boolean {
        calls += "due ${kept.toSortedMap()}"
        return due
    }

    override fun resolve(name: String, skip: Set<String>, kept: MutableMap<String, String>, host: ArtExtension.Host): ArtExtension.Resolved? {
        calls += "resolve $name"
        kept.putAll(keys)
        return resolved[name]?.takeIf { it.tag !in skip }
    }

    override fun candidates(id: String?, artist: MbArtist?, host: ArtExtension.Host): Sequence<Candidate> {
        calls += "candidates $id"
        return found.asSequence()
    }

    override fun resize(c: Candidate, side: Int, host: ArtExtension.Host): Pair<String, ByteArray>? {
        val url = resized[c.url] ?: return null
        calls += "resize ${c.url} $side"
        if (resizeFails) throw IOException("resize down")
        return host.api.get(url)?.body?.let { url to it }
    }

    override fun imageId(url: String): String? = url.substringBefore('=')
}
