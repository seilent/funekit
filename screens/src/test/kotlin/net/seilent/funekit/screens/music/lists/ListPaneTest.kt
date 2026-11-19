package net.seilent.funekit.screens.music.lists

import kotlinx.coroutines.runBlocking
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Rows
import net.seilent.funekit.kit.touch.RowSpec
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.api.Kind
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.Page
import net.seilent.funekit.api.Row
import net.seilent.funekit.screens.music.Paging
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

class ListPaneTest {
    private val none = Insets(0, 0, 0, 0)

    private fun scaleOf(w: Int, h: Int) = scale(Window(w, h, 35, none, none, none, none, none), Nav.None, Surface.Page, false)

    private fun geo(w: Int, h: Int): ListGeo {
        val s = scaleOf(w, h)
        return ListGeo(s, s.board.queueRowsTop, Rows.pitch(s, false))
    }

    @Test
    fun theRowsCountAndEmptyLinesSitOnTheBoardTokens() {
        val a = geo(720, 1280)
        assertEquals(360, a.top)
        assertEquals(257, a.countBase)
        assertEquals(408, a.emptyBase)
        assertEquals(1280 - 360, a.view)
        val r = geo(480, 800)
        assertEquals(240, r.top)
        assertEquals(171, r.countBase)
        assertEquals(272, r.emptyBase)
        val v = geo(240, 320)
        assertEquals(98, v.top)
        assertEquals(79, v.countBase)
        assertEquals(114, v.emptyBase)
    }

    @Test
    fun aCollectionPitchTakesRowCollectionPerBoard() {
        val s = scaleOf(720, 1280)
        assertEquals(165, ListGeo(s, s.board.queueRowsTop, px(Tok.rowCollection, s.ppu)).pitch)
        val h = ListGeo(s, s.board.page_contentTop, Rows.pitch(s, false))
        assertEquals(360, h.top)
        assertEquals(120, h.pitch)
    }

    @Test
    fun aWholePagePaneReadsItsRowsByIndex() {
        val p = ListPane()
        p.whole = listOf(row(1), row(2))
        assertEquals(2L, p.row(1)?.id)
        assertNull(p.row(2))
        assertNull(p.row(-1))
    }

    @Test
    fun aWindowPaneReadsTheRowsItsPagingHolds() = runBlocking {
        val p = ListPane()
        p.paging = Paging(Listing.Songs)
        assertNull(p.row(0))
        val v = view((0 until TOTAL).map { row(it.toLong()) })
        p.paging!!.show(v, 10, 12)
        assertEquals(10L, p.row(10)?.id)
        assertEquals(12L, p.row(12)?.id)
        assertNull(p.row(TOTAL))
    }

    @Test
    fun aDownFocusesPressesAndArmsTheGlowAndACutDropsThem() {
        val p = ListPane()
        p.geometry(RowSpec(PITCH, PITCH * 4))
        p.down(2, 5, 1000)
        assertEquals(2, p.focus.row)
        assertEquals(2, p.press.on.item)
        assertEquals(2, p.glow.glow.key)
        p.cut()
        assertNull(p.glow.glow.key)
    }

    @Test
    fun aTapIntoViewAimsAtTheRowBottomFromBelow() {
        val p = ListPane()
        p.geometry(RowSpec(PITCH, PITCH * 4))
        p.into(6)
        assertEquals(6 * PITCH + PITCH - PITCH * 4, p.aim)
        assertEquals(1, p.aims)
    }

    private fun row(id: Long) = Row(id, Kind.Song, "t$id", "", null)

    private fun view(rows: List<Row>): LibraryView = Proxy.newProxyInstance(LibraryView::class.java.classLoader, arrayOf(LibraryView::class.java)) { _, method, args ->
        when (method.name) {
            "getGeneration" -> 1L
            "page" -> {
                val off = args[1] as Int
                val n = args[2] as Int
                Page(1, off, rows.size, rows.drop(off).take(n))
            }
            else -> throw UnsupportedOperationException(method.name)
        }
    } as LibraryView

    private companion object {
        const val PITCH = 120
        const val TOTAL = 40
    }
}
