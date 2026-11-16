package net.seilent.funekit.artist

import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

class Offer(
    val kind: String,
    val source: String,
    val url: String,
    val id: String,
    val credit: Credit?,
    val mbid: String?,
    val title: String,
    val src: ByteArray?,
    val label: String = Sources.label(source),
)

class Tile(val source: String, val caption: String, val preview: ByteArray, val full: String, val id: String, val credit: Credit?)

sealed interface Offered {
    class Ok(val offers: List<Offer>) : Offered {
        constructor(offer: Offer) : this(listOf(offer))
    }
    class Refused(val reason: String) : Offered
}

object PickLink {
    interface Link
    data class MusicBrainz(val mbid: String) : Link
    data class Commons(val file: String) : Link
    data class Direct(val url: String) : Link
    data object Unsupported : Link

    const val UNSUPPORTED = "unsupported link"
    const val UNREACHABLE = "link not reachable"
    const val NOT_IMAGE = "not an image"
    const val TOO_SMALL = "image too small"
    const val NOT_FOUND = "artist not found"

    private val MB_HOSTS = setOf("musicbrainz.org", "beta.musicbrainz.org")
    private val PAGES = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "open.spotify.com", "spotify.com", "www.spotify.com", "x.com", "www.x.com", "twitter.com", "www.twitter.com", "mobile.twitter.com")
    private val ARTIST = Regex("^/artist/([0-9A-Fa-f-]{36})(/.*)?$")

    fun isLink(text: String): Boolean {
        val lower = text.trim().lowercase(Locale.ROOT)
        return lower.startsWith("https://") || lower.startsWith("http://")
    }

    fun parse(text: String, extensions: List<ArtExtension> = emptyList()): Link {
        if (!isLink(text)) return Unsupported
        val t = text.trim()
        val url = if (t.lowercase(Locale.ROOT).startsWith("http://")) "https://" + t.substring("http://".length) else t
        val u = try {
            URI(url)
        } catch (e: URISyntaxException) {
            return Unsupported
        }
        val host = u.host?.lowercase(Locale.ROOT) ?: return Unsupported
        val path = u.rawPath ?: ""
        extensions.firstNotNullOfOrNull { it.link(u, host) }?.let { return it }
        return when {
            host in MB_HOSTS -> ARTIST.find(path)?.let { m -> Names.mbid(m.groupValues[1])?.let { MusicBrainz(it) } } ?: Unsupported
            host == "commons.wikimedia.org" -> Wikimedia.commonsFile(url.substringBefore('?').substringBefore('#'))?.let { Commons(it) } ?: Unsupported
            host == "upload.wikimedia.org" -> Wikimedia.uploadFile(url)?.let { Commons(it) } ?: Unsupported
            host in PAGES -> Unsupported
            else -> Direct(url)
        }
    }
}
