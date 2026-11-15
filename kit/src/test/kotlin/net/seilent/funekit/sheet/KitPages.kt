package net.seilent.funekit.sheet

import net.seilent.funekit.core.Section
import net.seilent.funekit.gen.Glyphs
import net.seilent.funekit.kit.RowKind
import net.seilent.funekit.kit.RowModel

class Page(
    val id: String,
    val note: String,
    val header: String,
    val pivots: List<String>,
    val active: Int,
    val rows: List<RowModel>,
    val focused: Int,
    val pressed: Int,
    val kind: String? = null,
    val listRows: Int = rows.size,
) {
    fun at(focused: Int, pressed: Int) = Page(id, note, header, pivots, active, rows, focused, pressed, listRows = listRows)

    fun playing(i: Int) = Page(id, note, header, pivots, active, rows.mapIndexed { k, r -> if (k == i) r.copy(accent = true) else r }, i, -1, listRows = listRows)
}

object KitPages {
    const val PICKED = 3

    val hubPivots = listOf("albums", "artists", "playlists", "songs", "genres", "folders")
    val btPivots = listOf("phone", "pairing", "headphones", "eq", "settings")

    const val SONG_ROWS = 1281
    const val ARTIST_ROWS = 75

    val songs = Page(
        "hub-songs", "as funeOS rowpress-0-hubsongs-held: songs pivot, # divider, numbered two line rows with the artist and album second line (hub.cpp SongModel), the first focused and pressed at 0.96 with the press glow at 50 ms",
        "music", hubPivots, 3,
        listOf(
            RowModel(kind = RowKind.Divider, primary = "#", song = true),
            RowModel(primary = "0 A Long Song Title That Runs Past The Right Edge", secondary = "An Artist Name Long Enough To Clip At The Edge", album = "Long Album", song = true),
            RowModel(primary = "10 Tide", secondary = "Lantern Hours", album = "Tide Pools", song = true),
            RowModel(primary = "10 Wren", secondary = "Brass Weather", album = "Copper Rain", song = true),
            RowModel(primary = "11 Ash", secondary = "Coastline", album = "Salt Roads", song = true),
            RowModel(primary = "11 Reed", secondary = "Afterglow", album = "Late Embers", song = true),
            RowModel(primary = "12 Moss", secondary = "Field Notes", album = "Paper Moons", song = true),
        ),
        1, 1, "rowpress-0-hubsongs-held", SONG_ROWS,
    )

    val songsLong = Page(
        "hub-songs-long", "the songs hub list of funeOS thumbfade and fling: the # divider and song rows 1 to 16 (7 to 16 past the kit page) of a $SONG_ROWS row list",
        "music", hubPivots, 3,
        songs.rows + listOf(
            RowModel(primary = "13 Fern", secondary = "Lantern Hours", album = "Tide Pools", song = true),
            RowModel(primary = "13 Ivy", secondary = "Brass Weather", album = "Copper Rain", song = true),
            RowModel(primary = "14 Rook", secondary = "Coastline", album = "Salt Roads", song = true),
            RowModel(primary = "14 Sage", secondary = "Afterglow", album = "Late Embers", song = true),
            RowModel(primary = "15 Alder", secondary = "Field Notes", album = "Paper Moons", song = true),
            RowModel(primary = "15 Fen", secondary = "Lantern Hours", album = "Tide Pools", song = true),
            RowModel(primary = "16 Heron", secondary = "Brass Weather", album = "Copper Rain", song = true),
            RowModel(primary = "16 Lark", secondary = "Coastline", album = "Salt Roads", song = true),
            RowModel(primary = "17 Marsh", secondary = "Afterglow", album = "Late Embers", song = true),
            RowModel(primary = "17 Pine", secondary = "Field Notes", album = "Paper Moons", song = true),
        ),
        0, -1, listRows = SONG_ROWS,
    )

    val artists = Page(
        "hub-artists", "as funeOS hub: artists pivot, collection # divider focused as enter_hub leaves it, collection rows with the play ring (the thumb strip under the title is not a row part, see P12d); $ARTIST_ROWS list rows, read off the funeOS t5-1500 list thumb (A105 y 360 to 498) through scrollbar.cpp thumb_of",
        "music", hubPivots, 1,
        listOf(
            RowModel(kind = RowKind.Divider, primary = "#", collection = true),
            RowModel(primary = "81 Tide", ring = true, collection = true),
            RowModel(primary = "81 Wren", ring = true, collection = true),
            RowModel(primary = "81 Reed", ring = true, collection = true),
        ),
        0, -1, "hub", ARTIST_ROWS,
    )

    val bt = Page(
        "bt-headphones", "as funeOS rowpress-0-btarc-held: headphones pivot, bold count line rows, the first pressed with the press glow",
        "bluetooth", btPivots, 2,
        listOf(
            RowModel(primary = "search", secondary = "searching 84", bold = true, countLine = true),
            RowModel(primary = "Field Buds", bold = true),
            RowModel(primary = "Lantern Cans", secondary = "connected", bold = true, countLine = true),
        ),
        0, 0, "rowpress-0-btarc-held",
    )

    val forms = Page(
        "forms", "the other row forms: ring row, trailing, accent, wifi toggle ring, arc 120, library second line",
        "music", hubPivots, 3,
        listOf(
            RowModel(primary = "Afterglow", secondary = "Lantern Hours", ring = true),
            RowModel(primary = "Brass Weather", number = "12", trailing = "4:05"),
            RowModel(primary = "Coastline", accent = true),
            RowModel(primary = "Home network", ring = true, ringToggle = true, ringOn = true, wifi = true, level = 2),
            RowModel(primary = "Pairing", ring = true, arc = 120, glyph = Glyphs.bluetooth),
            RowModel(primary = "Field Notes", secondary = "12 songs", libSub = true),
        ),
        -1, -1,
    )

    val pages = listOf(bt, forms)

    private val songLetters = "#abcdefiklmnorstuvzあかさたなはまやらわ漢"

    private fun avail(letters: String) = BooleanArray(Section.count) { Section.letter(it) in letters }

    val jumpAvail = avail(songLetters)
}
