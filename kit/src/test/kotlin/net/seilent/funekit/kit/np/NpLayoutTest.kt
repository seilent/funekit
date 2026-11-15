package net.seilent.funekit.kit.np

import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.kit.motion.Part
import net.seilent.funekit.scale
import org.junit.Assert.assertEquals
import org.junit.Test

class NpLayoutTest {
    private val none = Insets(0, 0, 0, 0)
    private val s = scale(Window(720, 1280, 35, none, none, none, none, none, null), Nav.None, Surface.NowPlaying, false)
    private val l = NpLayout(s)

    private fun edges(r: NpRect?) = r?.let { listOf(it.x0, it.y0, it.x1, it.y1) }

    private val plain = mapOf(
        Part.NpStatus to listOf(0, 0, 720, 57),
        Part.NpArtist to listOf(0, 263, 720, 342),
        Part.NpAlbum to listOf(0, 342, 720, 422),
        Part.NpIn to listOf(0, 422, 720, 883),
        Part.NpBar to listOf(0, 883, 720, 892),
        Part.NpTimeL to listOf(0, 892, 271, 941),
        Part.NpTimeR to listOf(271, 892, 720, 941),
        Part.NpTitle to listOf(0, 941, 720, 1013),
        Part.NpNext to listOf(0, 1013, 720, 1143),
        Part.NpShuffle to listOf(0, 1143, 228, 1280),
        Part.NpRings to listOf(228, 1143, 720, 1280),
    )

    @Test
    fun thePartsSplitThePageAtTheFuneOsEdges() {
        assertEquals(3f, s.stdPpu, 0f)
        assertEquals(720, s.widthPx)
        val got = l.partRects(variant = false, lyrics = false)
        assertEquals(plain.keys, got.keys)
        for ((p, e) in plain) assertEquals("$p", e, edges(got[p]))
    }

    @Test
    fun anotherAppsSessionStartsTheArtistPartAtTheFromLine() {
        val got = l.partRects(variant = true, lyrics = false)
        for ((p, e) in plain) assertEquals("$p", if (p == Part.NpArtist) listOf(0, 250, 720, 342) else e, edges(got[p]))
    }

    @Test
    fun lyricsTakeTheTitlePartFromTheArtistLineToTheRings() {
        val got = l.partRects(variant = false, lyrics = true)
        val kept = setOf(Part.NpStatus, Part.NpShuffle, Part.NpRings)
        for ((p, e) in plain) {
            val want = when {
                p == Part.NpTitle -> listOf(0, 263, 720, 1143)
                p in kept -> e
                else -> listOf(0, 0, 0, 0)
            }
            assertEquals("$p", want, edges(got[p]))
        }
    }

    @Test
    fun theRingLayerIsTheBackRingBoxGrownByHalfItsSide() {
        assertEquals(listOf(-12, -43, 194, 163), edges(l.ringRect))
    }
}
