package net.seilent.funekit.artist

import java.net.URI

interface ArtExtension {
    val source: String

    val sections: List<String> get() = listOf(source)

    val limits: List<HostLimit> get() = emptyList()

    fun identify(name: String): Boolean = true

    fun link(uri: URI, host: String): PickLink.Link? = null

    fun offer(link: PickLink.Link, host: Host): Offered? = null

    fun browse(section: String, b: Browse) {}

    fun due(kept: Map<String, String>, now: Long): Boolean = false

    fun resolve(name: String, skip: Set<String>, kept: MutableMap<String, String>, host: Host): Resolved? = null

    fun candidates(id: String?, artist: MbArtist?, host: Host): Sequence<Candidate> = emptySequence()

    fun resize(c: Candidate, side: Int, host: Host): Pair<String, ByteArray>? = null

    fun imageId(url: String): String? = null

    class Resolved(val tag: String, val mbid: String?, val avatar: Candidate?)

    interface Host {
        val api: Api
        val mb: MusicBrainz
        val codec: Codec

        fun image(source: String, url: String, id: String, credit: Credit?, title: String, label: String = Sources.label(source)): Offered
    }

    interface Browse {
        val name: String
        val mbid: String?
        val host: Host

        fun each(f: (MbArtist) -> Unit)

        fun first(): MbArtist?

        fun caption(a: MbArtist): String

        fun tile(source: String, caption: String, preview: String, full: String, id: String)

        fun <T> attempt(block: () -> T): T?

        fun search(outcome: BrowseFeed.Search)

        fun log(line: String)
    }
}
