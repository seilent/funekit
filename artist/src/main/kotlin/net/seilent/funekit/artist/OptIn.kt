package net.seilent.funekit.artist

data class Sources(
    val theAudioDb: Boolean = false,
    val discogs: Boolean = false,
    val fanartTv: Boolean = false,
    val fanartTvKey: String = "",
    val deezer: Boolean = false,
    val order: List<String> = ORDER,
    val commons: Boolean = false,
    val plugins: Map<String, Boolean> = emptyMap(),
) {
    val enabled: Set<String>
        get() = buildSet {
            if (commons) add(Wikimedia.SOURCE)
            if (theAudioDb) add(THE_AUDIO_DB)
            if (fanartTv && fanartTvKey.isNotBlank()) add(FANART_TV)
            if (discogs) add(DISCOGS)
            plugins.forEach { (id, on) -> if (on) add(id) }
            if (deezer) add(DEEZER)
        }

    val anyOn: Boolean
        get() = enabled.isNotEmpty()

    val ordered: List<String>
        get() = enabled.let { on -> order.filter { it in on } }

    fun moved(source: String, by: Int): Sources {
        val i = order.indexOf(source)
        val j = i + by
        if (i < 0 || j !in order.indices) return this
        return copy(order = order.toMutableList().apply { add(j, removeAt(i)) })
    }

    companion object {
        val ORDER = listOf("theaudiodb", "fanarttv", "discogs", Wikimedia.SOURCE, "deezer")
        private val NONE_ORDER = listOf(Wikimedia.SOURCE, "theaudiodb", "fanarttv", "discogs", "deezer")
        val NONE = Sources(theAudioDb = false, discogs = false, fanartTv = false, deezer = false, commons = false, order = NONE_ORDER)
        val COMMONS = NONE.copy(commons = true)

        fun order(s: String?, base: List<String> = ORDER): List<String> {
            val stored = (s ?: "").split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            return stored + base.filter { it !in stored }
        }

        const val THE_AUDIO_DB = "theaudiodb"
        const val FANART_TV = "fanarttv"
        const val DISCOGS = "discogs"
        const val DEEZER = "deezer"
        const val LINK = "link"
        const val MUSICBRAINZ = "musicbrainz"
        const val GALLERY = "gallery"

        private val LABELS = mapOf(
            Wikimedia.SOURCE to "commons",
            THE_AUDIO_DB to "theaudiodb",
            DISCOGS to "discogs",
            FANART_TV to "fanart.tv",
            DEEZER to "deezer",
            LINK to "link",
            MUSICBRAINZ to "musicbrainz",
            GALLERY to "gallery",
        )

        fun label(id: String) = LABELS[id] ?: id
    }
}

class TheAudioDb(private val api: Api, private val base: String = "https://www.theaudiodb.com/api/v1/json/123") {
    companion object {
        fun small(url: String) = if (url.contains("://r2.theaudiodb.com/")) "$url/small" else url
    }

    fun candidates(mbid: String): List<Candidate> {
        val a = api.json("$base/artist-mb.php?i=$mbid")?.get("artists")?.get(0) ?: return emptyList()
        return listOf("strArtistThumb", "strArtistFanart", "strArtistFanart2", "strArtistFanart3")
            .mapNotNull { a[it].str?.takeIf { u -> u.startsWith("http") } }
            .map { Candidate(it, Sources.THE_AUDIO_DB) }
    }
}

class FanartTv(private val api: Api, private val key: () -> String, private val base: String = "https://webservice.fanart.tv/v3/music") {
    fun candidates(mbid: String): List<Candidate> {
        val r = api.json("$base/$mbid?api_key=${Api.enc(key())}") ?: return emptyList()
        return listOf("artistthumb", "artistbackground").flatMap { kind ->
            r[kind].list.sortedByDescending { it["likes"].str?.toIntOrNull() ?: it["likes"].long?.toInt() ?: 0 }
                .mapNotNull { it["url"].str }
        }.map { Candidate(it, Sources.FANART_TV) }
    }
}

class Discogs(private val api: Api, private val base: String = "https://api.discogs.com") {
    fun candidates(a: MbArtist): List<Candidate> = images(a).mapNotNull { i ->
        i["uri"].str?.takeIf { it.startsWith("http") }?.let { Candidate(it, Sources.DISCOGS, w = i["width"].long?.toInt() ?: 0, h = i["height"].long?.toInt() ?: 0, group = if (i["type"].str == "primary") 0 else 1) }
    }

    fun previews(a: MbArtist): List<Pair<String, String>> = images(a).mapNotNull { i ->
        val full = i["uri"].str?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
        (i["uri150"].str?.takeIf { it.startsWith("http") } ?: full) to full
    }

    private fun images(a: MbArtist): List<J> {
        val id = a.rels("discogs").firstNotNullOfOrNull { Regex("/artist/(\\d+)").find(it.url)?.groupValues?.get(1) }
            ?: return emptyList()
        val r = api.json("$base/artists/$id") ?: return emptyList()
        return r["images"].list.sortedBy { if (it["type"].str == "primary") 0 else 1 }
    }
}

class Deezer(private val api: Api, val placeholders: Set<String> = PLACEHOLDERS, private val base: String = "https://api.deezer.com") {
    fun placeholder(src: ByteArray): Boolean = ArtCache.sha256(src) in placeholders

    fun search(name: String): List<J> = api.json("$base/search/artist?q=${Api.enc(name.trim())}")?.get("data")?.list ?: emptyList()

    fun candidates(name: String): List<Candidate> {
        val want = Names.fold(name)
        return search(name).filter { Names.fold(it["name"].str) == want }
            .sortedWith(compareBy<J> { it["name"].str?.trim() != name.trim() }.thenByDescending { it["nb_fan"].long ?: 0 })
            .mapNotNull { picture(it, "picture_xl") }
            .map { Candidate(it, Sources.DEEZER) }
    }

    companion object {
        val PLACEHOLDERS = setOf(
            "0d659e802c2d9c13e8cf4e0fab3f85b176173a649b60d9ddc03af2397fb43275",
            "bd8dae144dc585a7eb090e2071fe386bbe0df6ebcc47ee0d96d5ec5c23274530",
            "5caf39f825879e7982065e4402d7dde41be7ffcee984a0e1d273a7efb2b78f00",
        )
        private const val EMPTY = "d41d8cd98f00b204e9800998ecf8427e"
        const val XL = 1000
        const val REAL = 1500
        private val SIDE = Regex("/\\d+x\\d+-")

        fun resizable(url: String): Boolean = SIDE.containsMatchIn(url)

        fun sized(url: String, side: Int): String = url.replace(SIDE, "/${side}x$side-")

        fun picture(a: J, size: String): String? = a[size].str?.takeIf { u -> u.startsWith("http") && !u.contains("/artist//") && !u.contains("/artist/$EMPTY/") }
    }
}
