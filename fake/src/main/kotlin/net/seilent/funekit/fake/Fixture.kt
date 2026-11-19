package net.seilent.funekit.fake

import net.seilent.funekit.core.Section
import net.seilent.funekit.api.ArtRef
import net.seilent.funekit.api.Format
import net.seilent.funekit.api.Kind

enum class FakeSet { Main, Empty, Huge, Unavailable, Demo, DemoProps, DemoNoPlaylists }

internal enum class Art { Embedded, Cover, None }

internal enum class Codec(val word: String, val depth: Boolean) {
    Flac("flac", true),
    Mp3("mp3", false),
    Aac("aac", false),
    Wav("wav", true),
    Dsf("dsf", false),
}

internal class TrackSpec(
    val title: String,
    val no: Int?,
    val file: String,
    val dir: String? = null,
    val codec: Codec = Codec.Flac,
    val format: Format? = null,
    val art: Art? = null,
    val ms: Long? = null,
    val artist: String? = null,
)

internal class AlbumSpec(
    val title: String,
    val artist: String,
    val genre: String,
    val root: Int,
    val art: Art,
    val format: Format,
    val year: Int?,
    val dir: String,
    val tracks: List<TrackSpec>,
    val albumArtist: String? = null,
)

internal class Track(
    val id: Long,
    val title: String,
    val artists: List<Long>,
    val credit: String,
    val album: Long,
    val year: Int?,
    val genre: Long?,
    val no: Int?,
    val ms: Long,
    val format: Format,
    val codec: Codec,
    val art: ArtRef?,
    val broken: Boolean,
    val dir: String,
    val file: String,
)

internal class Album(
    val id: Long,
    val title: String,
    val credit: String,
    val genre: Long?,
    val root: Int,
    val mtime: Long,
    val year: Int?,
    val tracks: List<Track>,
)

internal class Named(val id: Long, val name: String)

internal class Shelf(
    val roots: List<String>,
    val albums: List<Album>,
    val artists: List<Named>,
    val genres: List<Named>,
    val playlists: List<PlaylistDoc>,
    val history: List<Long>,
    val pins: List<Pin>,
    val favorites: List<Long>,
    val lyrics: Map<Long, String>,
    val lyricShapes: Map<String, Long>,
) {
    private val genreById = genres.associateBy { it.id }

    val folders: Map<Pair<Int, String>, Long> = albums.flatMap { a -> a.tracks.flatMap { t -> prefixes(t.dir).map { a.root to it } } }
        .distinct()
        .sortedWith(compareBy<Pair<Int, String>> { it.first }.thenBy { it.second })
        .mapIndexed { i, key -> key to Fixture.FOLDER_BASE + i }
        .toMap()

    fun genreName(id: Long?): String = id?.let { genreById[it]?.name }.orEmpty()

    companion object {
        fun prefixes(dir: String): List<String> {
            if (dir.isEmpty()) return emptyList()
            val parts = dir.split('/')
            return parts.indices.map { parts.subList(0, it + 1).joinToString("/") }
        }

        fun split(raw: String, albumArtist: String?): List<String> {
            val text = raw.trim()
            if (text.isEmpty()) return emptyList()
            if (albumArtist != null && text.equals(albumArtist, ignoreCase = true)) return listOf(text)
            return text.replace(" / ", "; ").replace(", ", "; ").split("; ").filter { it.isNotEmpty() }
        }

        fun build(
            roots: List<String>,
            specs: List<AlbumSpec>,
            mtime: (Int) -> Long,
            ms: (Long) -> Long,
            broken: String?,
            seed: (List<Album>, List<Named>) -> Seed,
        ): Shelf {
            val artists = specs.flatMap { s -> s.tracks.flatMap { split(it.artist ?: s.artist, s.albumArtist) } }.distinct().mapIndexed { i, n -> Named(i + 1L, n) }
            val genres = specs.map { it.genre }.filter { it.isNotEmpty() }.distinct().mapIndexed { i, n -> Named(i + 1L, n) }
            val artist = artists.associate { it.name to it.id }
            val genre = genres.associate { it.name to it.id }
            var next = 1L
            val albums = specs.mapIndexed { i, s ->
                val album = i + 1L
                val tracks = s.tracks.map { t ->
                    val id = next++
                    val art = when (t.art ?: s.art) {
                        Art.Embedded -> ArtRef.Embedded(id, ART_OFFSET, ART_BASE + id * ART_STEP)
                        Art.Cover -> ArtRef.Cover(id, COVER)
                        Art.None -> null
                    }
                    val credit = t.artist ?: s.artist
                    Track(
                        id, t.title, split(credit, s.albumArtist).map { artist.getValue(it) }, credit, album, s.year, genre[s.genre],
                        t.no, t.ms ?: ms(id), t.format ?: s.format, t.codec, art, t.title == broken, t.dir ?: s.dir, t.file,
                    )
                }
                Album(album, s.title, s.albumArtist ?: s.artist, genre[s.genre], s.root, mtime(i), s.year, tracks)
            }
            val sd = seed(albums, artists)
            return Shelf(roots, albums, artists, genres, sd.playlists, sd.history, sd.pins, sd.favorites, sd.lyrics, sd.shapes)
        }

        private const val ART_OFFSET = 4096L
        private const val ART_BASE = 120_000L
        private const val ART_STEP = 1_000L
        private const val COVER = "cover.jpg"
    }
}

internal class Seed(
    val playlists: List<PlaylistDoc>,
    val history: List<Long>,
    val pins: List<Pin>,
    val favorites: List<Long>,
    val lyrics: Map<Long, String>,
    val shapes: Map<String, Long>,
)

internal object Fixture {
    val ROOTS = listOf(
        "content://com.android.externalstorage.documents/tree/primary%3AMusic",
        "content://com.android.externalstorage.documents/tree/1A2B-3C4D%3AAlbums",
    )
    const val UNAVAILABLE_ROOT = 1
    const val BROKEN = "Worn Groove"
    const val REFUSED = "Room 12"
    const val HUGE = 50_000
    const val HUGE_BASE = 1_000_000L
    const val FOLDER_BASE = 1_000L
    const val MISSING = 9_999_999L

    private val CD = Format(44100, 16, 2)
    private val HI = Format(96000, 24, 2)
    private val MAX = Format(192000, 24, 2)
    private val VIDEO = Format(48000, 24, 2)
    private val SURROUND = Format(192000, 24, 6)

    private const val MIKA = "Mika Arden"
    private const val TIDES = "The Low Tides"
    private const val COBALT = "Cobalt Rooms"
    private const val NOA = "Noa Berg"
    private const val BELLS = "Saint Wilhelmina and the Extraordinarily Long Named Evening Bell Orchestra of the Northern Quarter"
    private const val ASAHI = "あさひ"
    private const val KANATA = "カナタ"
    private const val TODAI = "灯台守"
    private const val LAILA = "ليلى والقمر"
    private const val SHACHAR = "שחר"
    private const val ENGINE = "ｴﾝｼﾞﾝﾙｰﾑ"
    private const val OSTEN = "Østen Vale"

    private const val MS_BASE = 150_000L
    private const val MS_STEP = 37_000L
    private const val MS_SPAN = 180_000L
    private const val MTIME_BASE = 1_760_000_000_000L
    private const val DAY = 86_400_000L
    private const val YEAR_BASE = 1990
    private const val YEAR_STEP = 7
    private const val YEAR_SPAN = 33

    private class Spec(val title: String, val artist: String, val genre: String, val root: Int, val art: Art, val format: Format, val tracks: List<String>)

    private val SPECS = listOf(
        Spec("17 Lanterns", MIKA, "Folk", 0, Art.Embedded, CD, listOf("Lantern One", "Seventeen Steps", "Paper Moon Ferry")),
        Spec("Amber Field", MIKA, "Folk", 0, Art.Embedded, HI, listOf("Amber Field", "Slow Comet", "Porchlight", "Hayfever Waltz")),
        Spec("Blue Hour Ferry", TIDES, "Rock", 0, Art.Cover, CD, listOf("Blue Hour", "Deckhand", "Ropes and Rain")),
        Spec("Cobalt Rooms", COBALT, "Jazz", 0, Art.Embedded, MAX, (1..12).map { "Room $it" } + "Brass Corridor"),
        Spec("Dune Sketches", NOA, "Ambient", 0, Art.None, VIDEO, listOf("Dune I", "Dune II", "Dune III")),
        Spec("Ember Choir", BELLS, "Ambient", 0, Art.Embedded, CD, listOf("Ember", "Choir Loft", "Candle Smoke")),
        Spec("Fennel and Smoke", TIDES, "Rock", 0, Art.Embedded, CD, listOf("Fennel", "Smokestack Lullaby", "Kitchen Radio")),
        Spec("Glass Orchard", NOA, "Folk", 0, Art.Cover, HI, listOf("Glass Orchard", "Pear Thief", "Frost Line")),
        Spec("Harbor Lights at Four", COBALT, "Jazz", 0, Art.Embedded, CD, listOf("Harbor Lights", "Four in the Morning", "Tugboat Blues")),
        Spec("Iron Meadow", TIDES, "Rock", 0, Art.None, CD, listOf("Iron Meadow", "Rust Bloom", "Field Static")),
        Spec("Juniper Static", OSTEN, "Electronic", 0, Art.Embedded, CD, listOf("Juniper", "Static Garden", "Low Battery")),
        Spec("Kite Season", MIKA, "Folk", 0, Art.Embedded, CD, listOf("Kite Season", "String Theory for Beginners", "Updraft")),
        Spec(
            "Long Walks Through the Northern Quarter on a Rainy Thursday Evening With Nothing but a Borrowed Umbrella and a Head Full of Half Remembered Melodies (Extended Edition)",
            BELLS, "Ambient", 0, Art.Embedded, HI,
            listOf(
                "A Very Long Song Title That Keeps Going Past the Edge of Any Reasonable Screen Width Just to See What Happens to the Ellipsis",
                "Umbrella",
                "Northern Quarter",
            ),
        ),
        Spec("Moth Radio", OSTEN, "Electronic", 0, Art.Cover, CD, listOf("Moth Radio", "Night Frequency", "Tuning Fork")),
        Spec("Night Bus Diaries", NOA, "Jazz", 0, Art.Embedded, CD, listOf("Night Bus", "Last Stop", "Window Seat")),
        Spec("Orbit Lane", OSTEN, "Electronic", 0, Art.Embedded, VIDEO, listOf("Orbit Lane", "Perigee", "Apogee")),
        Spec("Paper Lanterns", MIKA, "Folk", 1, Art.Embedded, CD, listOf("Paper Lanterns", "Fold Here", "River Release")),
        Spec("Quiet Engines", COBALT, "Jazz", 1, Art.None, CD, listOf("Quiet Engines", "Idle", "Ignition")),
        Spec("Rust and Velvet", TIDES, "Rock", 1, Art.Embedded, CD, listOf("Rust and Velvet", BROKEN, "Velvet Rope")),
        Spec("Salt Lake Postcards", NOA, "Folk", 1, Art.Cover, HI, listOf("Salt Lake", "Postcard Home", "Wish You Were Here Soon")),
        Spec("Tin Roof Weather", MIKA, "Folk", 0, Art.Embedded, CD, listOf("Tin Roof", "Drizzle", "Gutter Song")),
        Spec("Umber", OSTEN, "Ambient", 0, Art.Embedded, CD, listOf("Umber", "Ochre", "Sienna")),
        Spec("Velvet Static", OSTEN, "Electronic", 0, Art.Embedded, CD, listOf("Velvet Static", "Hum", "Noise Floor")),
        Spec("Winter Arcade", TIDES, "Rock", 0, Art.Cover, CD, listOf("Winter Arcade", "Token", "High Score")),
        Spec("Xylem", NOA, "Ambient", 0, Art.Embedded, HI, listOf("Xylem", "Phloem", "Sap Rising")),
        Spec("Yellow Kiosk", COBALT, "Jazz", 0, Art.None, CD, listOf("Yellow Kiosk", "Newspaper Morning", "Small Change")),
        Spec("Zero Gravity Picnic", OSTEN, "Electronic", 0, Art.Embedded, CD, listOf("Zero Gravity", "Picnic Blanket", "Orbital Sandwich")),
        Spec("あおぞら日記", ASAHI, "ポップ", 0, Art.Embedded, VIDEO, listOf("あおぞら", "日記帳", "ひこうき雲")),
        Spec("カセットの夏", KANATA, "ポップ", 0, Art.Embedded, CD, listOf("カセット", "なつやすみ", "テープの終わり")),
        Spec("さよならの駅", ASAHI, "ポップ", 1, Art.Cover, CD, listOf("さよなら", "駅のホーム", "終電")),
        Spec("ターミナル", KANATA, "ポップ", 0, Art.Embedded, CD, listOf("ターミナル", "出発ロビー", "とうちゃく")),
        Spec("なつのおわり", ASAHI, "ポップ", 0, Art.None, CD, listOf("なつのおわり", "花火", "ゆうがた")),
        Spec("ハルカゼ", KANATA, "ポップ", 0, Art.Embedded, CD, listOf("ハルカゼ", "さくら通り", "新学期")),
        Spec("まよなかの雨", TODAI, "民謡", 0, Art.Embedded, CD, listOf("まよなか", "雨音", "かさ")),
        Spec("ヨアケ", ENGINE, "ポップ", 0, Art.Embedded, CD, listOf("ヨアケ", "朝焼け", "ねむれない")),
        Spec("らせん階段", TODAI, "民謡", 0, Art.Cover, CD, listOf("らせん", "階段の上", "ろうそく")),
        Spec("わすれもの", ASAHI, "ポップ", 0, Art.Embedded, CD, listOf("わすれもの", "傘", "改札")),
        Spec("夜行列車", TODAI, "民謡", 0, Art.Embedded, HI, listOf("夜行列車", "寝台", "北へ")),
        Spec("نور الليل", LAILA, "Ambient", 0, Art.Embedded, CD, listOf("نور", "الليل الطويل", "قمر")),
        Spec("שקט ליד הים", SHACHAR, "Ambient", 0, Art.None, CD, listOf("שקט", "גלים", "חוף")),
        Spec("ｶﾞﾗｽの街", ENGINE, "Electronic", 0, Art.Embedded, CD, listOf("ｶﾞﾗｽ", "ﾈｵﾝ", "ﾋﾞﾙの谷")),
    )

    private val MAIN_SPECS = SPECS.mapIndexed { i, s ->
        AlbumSpec(
            s.title, s.artist, s.genre, s.root, s.art, s.format, YEAR_BASE + i * YEAR_STEP % YEAR_SPAN, s.title,
            s.tracks.mapIndexed { n, t -> TrackSpec(t, n + 1, "${(n + 1).toString().padStart(2, '0')} $t.flac", format = SURROUND.takeIf { t == REFUSED }) },
        )
    }

    private val MAIN: Shelf = Shelf.build(ROOTS, MAIN_SPECS, { MTIME_BASE + (it * 7L % SPECS.size) * DAY }, { MS_BASE + it * MS_STEP % MS_SPAN }, BROKEN) { albums, artists ->
        val ids = albums.flatMap { it.tracks }.associate { it.title to it.id }
        Seed(
            playlists = listOf(
                "Late Night" to listOf("Night Bus", "Four in the Morning", "まよなか", "Noise Floor", "寝台", "Paper Lanterns"),
                "Commute" to listOf("Window Seat", "Last Stop", "ターミナル", "Kitchen Radio"),
                "お気に入り" to listOf("Slow Comet", "ひこうき雲", "Hayfever Waltz", "北へ"),
            ).mapIndexed { i, (name, titles) ->
                PlaylistDoc(i + 1L, name, titles.map { ids.getValue(it) }, i < 2, 0, "Music/Playlists/$name.${if (i < 2) "m3u8" else "m3u"}")
            },
            history = listOf("Slow Comet", "ひこうき雲", "Night Bus", "Moth Radio", "夜行列車", "Umbrella").map { ids.getValue(it) },
            pins = listOf(
                Pin(Kind.Album, albums.first { it.title == "夜行列車" }.id),
                Pin(Kind.Artist, artists.first { it.name == MIKA }.id),
                Pin(Kind.Song, ids.getValue("Slow Comet")),
            ),
            favorites = listOf("Hayfever Waltz", "Night Bus", "あおぞら").map { ids.getValue(it) },
            lyrics = mapOf(
                "Slow Comet" to "[00:12.00]the comet takes its time\n[00:18.50]and so do I\n[00:25.00]porch light on, nobody home",
                "ひこうき雲" to "[00:05.00]しろいせんが そらをわける\n[00:11.00]きみのてを ふった\n[00:17.00]夏のおわりに",
                "Umbrella" to "one umbrella\ntwo shoulders\nrain on the left side",
            ).mapKeys { ids.getValue(it.key) },
            shapes = emptyMap(),
        )
    }

    private val DEMO: Shelf by lazy { Demo.shelf(ROOTS.take(1), props = false, playlists = true) }
    private val DEMO_PROPS: Shelf by lazy { Demo.shelf(ROOTS.take(1), props = true, playlists = true) }
    private val DEMO_NO_PLAYLISTS: Shelf by lazy { Demo.shelf(ROOTS.take(1), props = false, playlists = false) }

    val ALBUMS: List<Album> = MAIN.albums
    val PINS: List<Pin> = MAIN.pins
    val HISTORY: List<Long> = MAIN.history

    fun shelf(set: FakeSet): Shelf = when (set) {
        FakeSet.Demo -> DEMO
        FakeSet.DemoProps -> DEMO_PROPS
        FakeSet.DemoNoPlaylists -> DEMO_NO_PLAYLISTS
        else -> MAIN
    }

    fun hugeLetter(i: Int): Int = (i.toLong() * Section.count / HUGE).toInt()

    private fun hugeStart(letter: Int): Int = ((letter.toLong() * HUGE + Section.count - 1) / Section.count).toInt()

    fun hugeIndex(id: Long): Int? = (id - HUGE_BASE).takeIf { it in 0 until HUGE }?.toInt()

    fun hugeTrack(i: Int): Track {
        val letter = hugeLetter(i)
        val n = i - hugeStart(letter) + 1
        val title = "${Section.letter(letter)} ${n.toString().padStart(5, '0')}"
        return Track(HUGE_BASE + i, title, emptyList(), "", 0, null, null, n, MS_BASE + i % 7 * 20_000L, CD, Codec.Flac, null, false, "", "$title.flac")
    }
}
