package net.seilent.funekit.fake

import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.engine.Format

internal object Demo {
    const val ARTISTS = 40
    const val ALBUMS_EACH = 4
    const val TRACKS_EACH = 8

    const val LONG_TITLE = "0 A Song Name That Keeps On Running Past Its Row Edge"
    const val LONG_ARTIST = "A Singer Name Long Enough To Be Cut At The Right End"
    const val LONG_ALBUM = "0 An Album Name Cut Short At The Tile Border"
    const val LONG_PLAYLIST = "A Playlist Title Long Enough To Cut At The Edge"
    const val LIVE_DIR = "flac/live/Evening_Sessions_Recorded_Live_In_Nara/Extra"

    private val CD = Format(44100, 16, 2)
    private val MONO = Format(44100, 16, 1)
    private val MONO48 = Format(48000, 16, 1)
    private val HIGH = Format(96000, 24, 1)
    private val DSD64 = Format(2822400, 1, 2)

    private const val LISTS = "Music/Playlists"
    private const val TRACK_MS = 180_000L
    private const val TONE_MS = 1_000L
    private const val LONG_TONE_MS = 2_000L
    private const val YEAR_BASE = 1980
    private const val YEAR_SPAN = 45
    private const val MP3_EVERY = 5
    private const val MTIME = 1_791_640_260_000L
    private const val SOURCES = 13
    private const val NUMBERED = 10
    private const val NUMBER_SPAN = 90
    private const val MIX_STRIDE = 101
    private const val MIX_PRIME = 7919
    private const val MIX_MOD = 104_729
    private const val ALBUM_SEED = 1000
    private const val TITLE_SEED = 5000
    private const val ROAD = 12
    private const val SHORT_LISTS = 3
    private const val FAVORITE_FROM = 19
    private const val FAVORITES = 5
    private const val PLAYS = 10
    private const val PLAY_STEP = 7
    private const val PLAY_FROM = 3
    private const val PIN_COUNT = 3
    private const val PIN_STEP = 5
    private const val PIN_FROM = 2
    private const val ARTIST_PIN_STEP = 3
    private const val LYRIC_ALBUM = 116

    private val SYLLABLES = listOf("al", "bo", "da", "en", "fu", "ir", "ka", "lo", "mu", "ne", "ri", "su", "ta", "vo", "zi")
    private const val FIXTURE_HEADS = "dz"
    private const val KATAKANA = "イケステニヒムユレヲクセメルヨノ"
    private const val TITLE_HEADS = "アカサタナハマヤラワ"
    private const val ALBUM_HEADS = "アカナハマラワ"
    private const val ARTIST_HEADS = "カラワ"
    private const val KANJI = "雨森川石鳥火木山道音"
    private const val HANGUL = "강물길눈산들"
    private val GENRES = listOf("Pop", "Rock", "Jazz", "Electronic", "Classical", "Ambient", "Folk", "J-Pop")

    private fun mix(seed: Int, j: Int, n: Int): Int = (seed + j * MIX_STRIDE) * MIX_PRIME % MIX_MOD % n

    private fun word(seed: Int, syllables: Int): String {
        val parts = listOf(SYLLABLES[seed % SYLLABLES.size]) + (1 until syllables).map { SYLLABLES[mix(seed, it, SYLLABLES.size)] }
        return parts.joinToString("").replaceFirstChar { it.uppercaseChar() }
    }

    private fun name(kind: Int, seed: Int, heads: String, head: Int): String = when (kind % SOURCES) {
        3 -> "${heads[head % heads.length]}" + (1..3).map { KATAKANA[mix(seed, it, KATAKANA.length)] }.joinToString("")
        6 -> "${KANJI[seed % KANJI.length]}${KANJI[mix(seed, 1, KANJI.length)]}"
        9 -> "${HANGUL[seed % HANGUL.length]}${HANGUL[mix(seed, 1, HANGUL.length)]}"
        11 -> "The ${word(seed + 1, 2)} ${word(seed * 3 + 2, 2)}"
        12 -> "${NUMBERED + seed % NUMBER_SPAN} ${word(seed, 2)}"
        else -> "${word(seed, 2 + seed % 2)} ${word(seed * 3 + 1, 2)}"
    }

    private fun artistName(a: Int): String {
        val seed = if (a % SOURCES == 6 || a % SOURCES == 9) a % (2 * SOURCES) else a
        val n = name(a, seed, ARTIST_HEADS, a / SOURCES)
        return if (n.first().lowercaseChar() in FIXTURE_HEADS) name(a, seed + ARTISTS, ARTIST_HEADS, a / SOURCES) else n
    }

    private fun albumName(k: Int): String = name(k / 3, ALBUM_SEED + k, ALBUM_HEADS, k / (3 * SOURCES) * 3 + k % 3)

    private fun titleName(id: Int): String = name(id / 2, TITLE_SEED + id, TITLE_HEADS, id)

    private fun safe(s: String): String = s.map { if (it in "/\\:*?\"<>|") '_' else it }.joinToString("")

    private fun generated(): List<AlbumSpec> = (0 until ARTISTS).flatMap { a ->
        val artist = artistName(a)
        (0 until ALBUMS_EACH).map { b ->
            val k = a * ALBUMS_EACH + b
            val album = albumName(k)
            val tracks = (0 until TRACKS_EACH).map { t ->
                val title = titleName(k * TRACKS_EACH + t)
                val mp3 = t % MP3_EVERY == MP3_EVERY - 1
                val codec = if (mp3) Codec.Mp3 else Codec.Flac
                TrackSpec(title, t + 1, "${(t + 1).toString().padStart(2, '0')} ${safe(title)}.${codec.word}", codec = codec, ms = TRACK_MS)
            }
            AlbumSpec(album, artist, GENRES[(a + b) % GENRES.size], 0, Art.Cover, CD, YEAR_BASE + k % YEAR_SPAN, "${safe(artist)} $a/${safe(album)} $b", tracks)
        }
    }

    private fun one(
        album: String, artist: String, genre: String, year: Int?, art: Art, format: Format, dir: String,
        title: String, no: Int?, file: String, codec: Codec, ms: Long = TONE_MS, albumArtist: String? = null, credit: String? = null,
    ) = AlbumSpec(album, artist, genre, 0, art, format, year, dir, listOf(TrackSpec(title, no, file, codec = codec, ms = ms, artist = credit)), albumArtist)

    private fun library(props: Boolean): List<AlbumSpec> = listOf(
        one("Field Album", "Aster", "Ambient", 2019, Art.Embedded, MONO, "flac", "Early Frost", 3, "01.flac", Codec.Flac, albumArtist = "Aster", credit = "Aster / Brook"),
        one("テラス盤", "ヨルノオト", "J-Pop", 2020, Art.None, HIGH, "flac", "夜を渡る", 1, "cjk.flac", Codec.Flac, LONG_TONE_MS),
        one("Tracks", "Garnet", "Rock", 2001, Art.Embedded, MONO, "mp3", "Vessel Two", 7, "v23.mp3", Codec.Mp3),
        one("음반", "노래꾼", "", 2022, Art.None, MONO48, "mp3", "반가운 하루", 2, "v24.mp3", Codec.Mp3),
        one("Lineage", "Dorian", "Jazz", 1999, Art.None, MONO, "mp3", "Old Signal", 5, "v1.mp3", Codec.Mp3),
        one("Axioms", "Elara", "Electronic", 2015, Art.Embedded, MONO, "m4a", "Canister", 4, "aac.m4a", Codec.Aac),
        one("Clusters", "Zinnia", "Folk", 2010, Art.None, MONO48, "wav", "Reel", 6, "info.wav", Codec.Wav),
        one("Den", "Edda", "", null, Art.Cover, MONO, "folder", "Folder Cover", null, "a.flac", Codec.Flac),
        one(LONG_ALBUM, LONG_ARTIST, "", null, Art.Cover, MONO, "long", LONG_TITLE, null, "long.flac", Codec.Flac),
        one("Parallels", "Iris, Jun", "", null, Art.None, MONO, LIVE_DIR, "Dialogue", null, "duo.flac", Codec.Flac),
        one("unnamed", "", "", null, Art.None, MONO, "broken", "unnamed", null, "unnamed.flac", Codec.Flac),
    ) + if (props) listOf(one("Bitplane", "Solace", "", 2018, Art.Embedded, DSD64, "dsd", "短いDSD", 2, "tagged.dsf", Codec.Dsf)) else emptyList()

    fun shelf(roots: List<String>, props: Boolean, playlists: Boolean): Shelf = Shelf.build(roots, generated() + library(props), { MTIME }, { TRACK_MS }, null) { albums, artists ->
        val tracks = albums.flatMap { it.tracks }
        val byPath = tracks.sortedBy { "${it.dir}/${it.file}" }
        val flac = byPath.filter { it.codec == Codec.Flac }.map { it.id }
        val mp3 = byPath.filter { it.codec == Codec.Mp3 }.map { it.id }
        val noArt = albums.first { a -> a.tracks.all { it.art == null } }.id
        val pins = (0 until PIN_COUNT).map { i ->
            val album = albums[(i * PIN_STEP + PIN_FROM) % albums.size]
            when (i % 3) {
                0 -> Pin(Kind.Album, album.id)
                1 -> Pin(Kind.Artist, artists[(i * ARTIST_PIN_STEP + 1) % artists.size].id)
                else -> Pin(Kind.Song, album.tracks.first().id)
            }
        } + Pin(Kind.Album, noArt)
        val history = (0 until PLAYS).map { i ->
            val album = albums[(i * PLAY_STEP + PLAY_FROM) % albums.size]
            album.tracks[i % album.tracks.size].id
        }.distinct()
        val shapes = DemoLyrics.SHAPES.keys.mapIndexed { i, shape -> shape to albums[i].tracks.first().id }.toMap()
        Seed(
            playlists = if (!playlists) emptyList() else listOf(
                PlaylistDoc(1, "Night Drive", flac.take(ROAD), true, 0, "$LISTS/Night Drive.m3u8"),
                PlaylistDoc(2, LONG_PLAYLIST, mp3.take(SHORT_LISTS) + Fixture.MISSING, false, 0, "$LISTS/$LONG_PLAYLIST.m3u"),
                PlaylistDoc(3, "雨の歌", mp3.drop(SHORT_LISTS).take(SHORT_LISTS), true, 0, "$LISTS/雨の歌.m3u8"),
            ),
            history = history,
            pins = pins,
            favorites = flac.drop(FAVORITE_FROM).take(FAVORITES),
            lyrics = shapes.entries.associate { (shape, id) -> id to DemoLyrics.SHAPES.getValue(shape) } +
                (albums[LYRIC_ALBUM].tracks.first().id to DemoLyrics.DEMO),
            shapes = shapes,
        )
    }
}
