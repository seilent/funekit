package net.seilent.funekit.screens.host

import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import net.seilent.funekit.Insets
import net.seilent.funekit.Nav
import net.seilent.funekit.Surface
import net.seilent.funekit.Window
import net.seilent.funekit.api.Busy
import net.seilent.funekit.api.Card
import net.seilent.funekit.api.Disconnected
import net.seilent.funekit.api.Expired
import net.seilent.funekit.api.Kind as ApiKind
import net.seilent.funekit.api.Library
import net.seilent.funekit.api.LibraryView
import net.seilent.funekit.api.Listing
import net.seilent.funekit.api.NoCard
import net.seilent.funekit.api.Refused
import net.seilent.funekit.api.Row
import net.seilent.funekit.fake.FakeSet
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.motion.MoveKind
import net.seilent.funekit.kit.motion.OverKind
import net.seilent.funekit.kit.stack.Phase
import net.seilent.funekit.kit.stack.Turn
import net.seilent.funekit.kit.touch.Arbiter
import net.seilent.funekit.px
import net.seilent.funekit.scale
import net.seilent.funekit.screens.music.ALBUMS
import net.seilent.funekit.screens.music.HUB_ALBUMS
import net.seilent.funekit.screens.music.MusicPage
import net.seilent.funekit.screens.music.MusicSource
import net.seilent.funekit.screens.music.PushFrom
import net.seilent.funekit.screens.music.hub.HubModel
import net.seilent.funekit.screens.music.overlay.ADD_TO_PLAYLIST
import net.seilent.funekit.screens.music.overlay.CARD_GONE
import net.seilent.funekit.screens.music.overlay.DELETE
import net.seilent.funekit.screens.music.overlay.DELETE_ALBUM
import net.seilent.funekit.screens.music.overlay.DELETE_ALBUM_ASK
import net.seilent.funekit.screens.music.overlay.DELETE_SONG
import net.seilent.funekit.screens.music.overlay.DELETE_SONG_ASK
import net.seilent.funekit.screens.music.overlay.DeleteDone
import net.seilent.funekit.screens.music.overlay.ExtraSheet
import net.seilent.funekit.screens.music.overlay.KEEP_ALBUM
import net.seilent.funekit.screens.music.overlay.KEEP_SONG
import net.seilent.funekit.screens.music.overlay.NEW_PLAYLIST
import net.seilent.funekit.screens.music.overlay.SONG_NOT_DELETED
import net.seilent.funekit.screens.music.overlay.SheetDelete
import net.seilent.funekit.screens.music.overlay.SheetExtra
import net.seilent.funekit.screens.music.overlay.SheetFrom
import net.seilent.funekit.screens.music.overlay.SheetKind
import net.seilent.funekit.screens.music.overlay.SheetTarget
import net.seilent.funekit.screens.music.overlay.sheetItems
import net.seilent.funekit.screens.music.testFake
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w720dp-h1280dp-mdpi")
class DeleteHostTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val none = Insets(0, 0, 0, 0)
    private val window = Window(W, H, SDK, none, none, none, none, none)
    private val page = scale(window, Nav.None, Surface.Page, false)
    private val np = scale(window, Nav.None, Surface.NowPlaying, false)
    private val scheduler = TestCoroutineScheduler()
    private val fake = testFake(FakeSet.Demo, scheduler)
    private var unlinked = false
    private val library = object : Library by fake.library {
        override fun view(): LibraryView = linked(fake.library.view())

        override fun view(generation: Long): LibraryView = linked(fake.library.view(generation))
    }
    private val music = source()
    private lateinit var app: TestApp
    private val stack get() = app.stack
    private val asked = mutableListOf<Long>()
    private var outcome: suspend () -> DeleteDone = { DeleteDone.Done }
    private var artist: Long? = null

    private fun source() = MusicSource(library, fake.player, fake.quickplay, fake.favorites, fake.playlists, fake.lyrics)

    private fun linked(v: LibraryView): LibraryView = object : LibraryView by v {
        override suspend fun indexOf(listing: Listing, kind: ApiKind, id: Long): Int? {
            if (unlinked) {
                unlinked = false
                throw Disconnected()
            }
            return v.indexOf(listing, kind, id)
        }
    }

    private fun launch(root: Boolean = false) {
        app = TestApp(page, np, music, StillLevel) { rule.mainClock.currentTime }
        app.deletes = { t ->
            SheetDelete(if (t.kind == SheetKind.Album) songs(t.id) else 0, artist) { _ ->
                asked += t.id
                outcome()
            }
        }
        app.compose(rule, root)
        rule.waitForIdle()
        rule.mainClock.autoAdvance = false
        frames(OPEN_FRAMES)
    }

    private fun apply() = rule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun frames(n: Int) = repeat(n) {
        apply()
        rule.mainClock.advanceTimeByFrame()
    }

    private fun until(what: String, ok: () -> Boolean) {
        var n = 0
        while (!rule.runOnIdle(ok)) {
            frames(1)
            assertTrue(what, ++n < MAX_FRAMES)
        }
    }

    private fun rows(l: Listing): List<Row> = runBlocking {
        val v = fake.library.view()
        v.page(l, 0, v.page(l, 0, 0).total).rows
    }

    private fun songs(album: Long): Int = rows(Listing.AlbumTracks(album)).size

    private fun gen(): Long = fake.library.generation.value

    private fun song(): Row = rows(Listing.Songs).first()

    private fun soleArtists(): List<Pair<Row, Row>> = rows(Listing.Artists).mapNotNull { a -> rows(Listing.ArtistAlbums(a.id)).singleOrNull()?.let { a to it } }

    private fun screen(p: MusicPage, from: PushFrom? = null) = TestScreen.Music(MusicEntry(stack.entry(), p, from))

    private fun on(vararg pages: TestScreen) {
        rule.runOnIdle { stack.reset(pages.toList()) }
        frames(OPEN_FRAMES)
    }

    private fun top(): TestScreen? = rule.runOnIdle { stack.top }

    private fun pages(): List<MusicPage> = rule.runOnIdle { stack.pages.map { (it as TestScreen.Music).music.page } }

    private fun songTarget(r: Row) = SheetTarget(SheetKind.Song, r.title, Listing.Songs, gen(), 0, r.id, ApiKind.Song, r.id, from = SheetFrom.Hub, pinned = false)

    private fun albumTarget(r: Row) = SheetTarget(SheetKind.Album, r.title, Listing.AlbumTracks(r.id), gen(), pin = ApiKind.Album, id = r.id, from = SheetFrom.Hub, pinned = false)

    private fun sheet(t: SheetTarget) {
        assertTrue("premise: the sheet opens", rule.runOnIdle { app.host.openMusic(t) })
        frames(OPEN_FRAMES)
    }

    private fun act(word: String) = rule.runOnIdle {
        val i = app.menu.items.indexOf(word)
        assertTrue("premise: $word is on ${app.menu.items}", i >= 0)
        if (app.menuHost.act(i)) stack.accept()
    }

    private fun pick(word: String) {
        act(word)
        frames(OPEN_FRAMES)
    }

    private fun delete(t: SheetTarget) {
        sheet(t)
        pick(DELETE)
        pick(if (t.kind == SheetKind.Album) DELETE_ALBUM else DELETE_SONG)
    }

    private fun scan(drop: Long? = null) {
        drop?.let { fake.dropAtRescan(it) }
        runBlocking { fake.library.rescan() }
        scheduler.advanceUntilIdle()
    }

    private fun rescan(drop: Long? = null) {
        scan(drop)
        frames(LEAVE_FRAMES)
    }

    private fun rested() = until("the stack rests") { stack.phase == Phase.Rest && stack.turn == null }

    private fun popKind(): MoveKind {
        var n = 0
        while (true) {
            val k = rule.runOnIdle { stack.turn?.takeIf { it.popping }?.kind }
            if (k != null) return k
            frames(1)
            assertTrue("a page pops", ++n < MAX_FRAMES)
        }
    }

    private fun pops(n: Int): Int {
        val seen = mutableListOf<Turn<TestScreen>>()
        repeat(n) {
            frames(1)
            rule.runOnIdle { stack.turn?.takeIf { it.popping }?.let { t -> if (seen.none { it === t }) seen += t } }
        }
        return seen.size
    }

    private fun toasted(line: String, title: String) = until("the toast shows $line, $title") { app.toast.artist == line && app.toast.title == title }

    private fun rowY(i: Int): Float {
        val ppu = if (app.menu.kept) page.stdPpu else page.ppu
        return px(Tok.contentTop, ppu) + (i + HALF) * px(Tok.settingsPitch, ppu)
    }

    private fun tapRow(i: Int) {
        rule.onRoot().performTouchInput {
            down(Offset(page.widthPx / 2f, rowY(i)))
            up()
        }
    }

    @Test
    fun aSongSheetEndsWithDeleteAndItsConfirmLeadsWithTheTitle() {
        launch()
        val s = song()
        on(screen(MusicPage.Hub))
        val t = songTarget(s)
        sheet(t)
        assertEquals(sheetItems(t, false) + DELETE, rule.runOnIdle { app.menu.items })
        pick(DELETE)
        rule.runOnIdle {
            val m = app.menu
            assertEquals(OverKind.Dlg, stack.overlay?.kind)
            assertEquals(MenuForm.Dlg, m.form)
            assertEquals(DELETE_SONG_ASK, m.title)
            assertEquals(listOf(s.title, DELETE_SONG, KEEP_SONG), m.items)
            assertEquals("the title is a lead row", 1, m.lead)
            assertEquals("the title row keeps its case", 0, m.mixedRow)
            assertEquals(KEEP_SONG, m.items[m.focus.row])
            assertTrue("the confirm takes the rise guard", m.confirm)
        }
        assertTrue(asked.isEmpty())
    }

    @Test
    fun anAlbumConfirmCountsItsSongsAndFocusesKeep() {
        launch()
        val a = rows(Listing.Albums).first { songs(it.id) > 1 }
        val n = songs(a.id)
        on(screen(MusicPage.Hub))
        sheet(albumTarget(a))
        pick(DELETE)
        rule.runOnIdle {
            val m = app.menu
            assertEquals(DELETE_ALBUM_ASK, m.title)
            assertEquals(listOf(a.title, "$n songs", DELETE_ALBUM, KEEP_ALBUM), m.items)
            assertEquals(2, m.lead)
            assertEquals(0, m.mixedRow)
            assertEquals(KEEP_ALBUM, m.items[m.focus.row])
        }
    }

    @Test
    fun keepClosesTheConfirmAndRunsNoDelete() {
        launch()
        on(screen(MusicPage.Hub))
        sheet(songTarget(song()))
        pick(DELETE)
        pick(KEEP_SONG)
        assertTrue("the confirm closes", rule.runOnIdle { stack.overlay.let { it == null || it.closing } })
        assertTrue("keep runs no delete", asked.isEmpty())
    }

    @Test
    fun aPressInsideTheConfirmRiseRunsNoDelete() {
        launch()
        val s = song()
        on(screen(MusicPage.Hub))
        sheet(songTarget(s))
        act(DELETE)
        frames(RISE_FRAMES)
        val o = rule.runOnIdle { stack.overlay }
        assertTrue("premise: the confirm rises", rule.runOnIdle { o != null && o.kind == OverKind.Dlg && o.moving(rule.mainClock.currentTime) })
        assertTrue("the confirm takes the rise guard", rule.runOnIdle { app.menu.confirm })
        tapRow(1)
        frames(CUT_FRAMES)
        assertTrue("a press inside the rise runs no delete", asked.isEmpty())
        assertSame(o, rule.runOnIdle { stack.overlay })
        assertFalse(o!!.closing)
        frames(OPEN_FRAMES)
        tapRow(1)
        frames(OPEN_FRAMES)
        assertEquals("a press after the rise runs the delete", listOf(s.id), asked)
    }

    private fun confirm(t: SheetTarget): Int {
        sheet(t)
        pick(DELETE)
        val yes = rule.runOnIdle { app.menu.items.indexOf(DELETE_SONG) }
        rule.runOnIdle { app.menu.focus.set(yes, app.menu.items.size, rule.mainClock.currentTime, false) }
        frames(CUT_FRAMES)
        assertTrue("premise: the confirm rests", rule.runOnIdle { stack.overlay.let { it != null && it.kind == OverKind.Dlg && !it.closing && !it.moving(rule.mainClock.currentTime) } })
        assertEquals("premise: the focus is on the yes", yes, rule.runOnIdle { app.menu.focus.row })
        return yes
    }

    private fun gone() = until("the confirm goes") { stack.overlay == null }

    @Test
    fun aDeleteRunsOnlyOnTheConfirmsYesAndOnce() {
        launch()
        val s = song()
        on(screen(MusicPage.Hub))
        val t = songTarget(s)
        val yes = confirm(t)
        assertTrue("the open runs no delete", asked.isEmpty())
        val slop = Arbiter(page).slop.slop
        rule.onRoot().performTouchInput {
            down(Offset(page.widthPx / 2f, rowY(yes)))
            moveBy(Offset(slop, 0f))
            moveBy(Offset(slop, 0f))
            up()
        }
        frames(OPEN_FRAMES)
        assertTrue("a focus and a sliding press run no delete", asked.isEmpty())
        assertTrue("premise: the confirm stays up", rule.runOnIdle { stack.overlay.let { it != null && it.kind == OverKind.Dlg && !it.closing } })
        rule.runOnIdle { stack.back(app.host.popKind) }
        gone()
        assertTrue("a back runs no delete", asked.isEmpty())
        confirm(t)
        val scrim = page.heightPx - SCRIM_UP
        assertTrue("premise: the tap is below the dialog", rule.runOnIdle { !app.menu.kept && scrim > app.menu.dialogEnd(page) })
        rule.onRoot().performTouchInput {
            down(Offset(page.widthPx / 2f, scrim))
            up()
        }
        gone()
        assertTrue("the scrim runs no delete", asked.isEmpty())
        confirm(t)
        rule.runOnIdle { app.host.closeLibrarySheets() }
        gone()
        assertTrue("the library close runs no delete", asked.isEmpty())
        confirm(t)
        tapRow(yes)
        frames(CUT_FRAMES)
        assertEquals("the yes runs the delete", listOf(s.id), asked)
        assertTrue("premise: the confirm closes", rule.runOnIdle { stack.overlay?.closing == true })
        tapRow(yes)
        gone()
        assertEquals("a second yes in the close fade runs none", listOf(s.id), asked)
    }

    @Test
    fun aYesAfterTheLibraryOrTheSourceMovedRefusesAndRunsNoDelete() {
        launch()
        val s = song()
        on(screen(MusicPage.Hub))
        sheet(songTarget(s))
        pick(DELETE)
        rescan()
        pick(DELETE_SONG)
        toasted(SONG_NOT_DELETED, s.title)
        assertTrue("a moved library runs no delete", asked.isEmpty())
        sheet(songTarget(s))
        pick(DELETE)
        val shows = rule.runOnIdle { app.toast.shows }
        rule.runOnIdle { app.source = source() }
        frames(OPEN_FRAMES)
        pick(DELETE_SONG)
        until("the refusal toasts again") { app.toast.shows > shows }
        toasted(SONG_NOT_DELETED, s.title)
        assertTrue("a moved source runs no delete", asked.isEmpty())
    }

    @Test
    fun theSeamOutcomesOfTheActMapToTheUnitWords() {
        launch()
        val s = song()
        on(screen(MusicPage.Hub))
        val legs = listOf(NoCard() to (CARD_GONE to ""), Refused() to (SONG_NOT_DELETED to s.title), Expired(1L) to (SONG_NOT_DELETED to s.title), Busy() to (SONG_NOT_DELETED to s.title))
        for ((x, words) in legs) {
            outcome = { throw x }
            val shows = rule.runOnIdle { app.toast.shows }
            delete(songTarget(s))
            until("$x toasts") { app.toast.shows > shows }
            toasted(words.first, words.second)
        }
        assertEquals(List(legs.size) { s.id }, asked)
    }

    @Test
    fun anAlbumPageLeavesAtTheRescanThatDropsItNotAtTheAnswer() {
        launch()
        val a = rows(Listing.Albums).first()
        val hub = screen(MusicPage.Hub)
        val album = screen(MusicPage.Album(a.id))
        on(hub, album)
        delete(albumTarget(a))
        assertEquals(listOf(a.id), asked)
        frames(LEAVE_FRAMES)
        assertSame("the answer moves no page", album, top())
        rescan()
        assertSame("a rescan that still lists the album moves no page", album, top())
        scan(a.id)
        assertEquals("the pop takes the back motion", MoveKind.T4, popKind())
        rested()
        assertEquals(listOf(MusicPage.Hub), pages())
    }

    @Test
    fun aBuriedAlbumPageUnderNowPlayingIsErasedWithNoMotion() {
        launch()
        val a = rows(Listing.Albums).first()
        val hub = screen(MusicPage.Hub)
        val album = screen(MusicPage.Album(a.id))
        on(hub, album)
        delete(albumTarget(a))
        rule.runOnIdle { app.host.resetNp(listOf(hub, album)) }
        frames(OPEN_FRAMES)
        assertEquals("premise: now playing over the album page", listOf(MusicPage.Hub, MusicPage.Album(a.id), MusicPage.NowPlaying), pages())
        scan(a.id)
        repeat(LEAVE_FRAMES) {
            frames(1)
            assertNull("the erase runs no motion", rule.runOnIdle { stack.turn })
        }
        assertEquals(listOf(MusicPage.Hub, MusicPage.NowPlaying), pages())
    }

    @Test
    fun theAlbumsArtistPageLeavesWhenItsLastAlbumGoes() {
        launch()
        val sole = soleArtists()
        assertTrue("premise: two artists of one album", sole.size >= 2)
        val hub = screen(MusicPage.Hub)
        val (r, a) = sole[0]
        artist = r.id
        on(hub, screen(MusicPage.Artist(r.id)), screen(MusicPage.Album(a.id)))
        delete(albumTarget(a))
        rescan(a.id)
        rested()
        assertEquals("the artist page of its last album leaves with it", listOf(MusicPage.Hub), pages())
        val (r2, a2) = sole[1]
        artist = null
        on(hub, screen(MusicPage.Artist(r2.id)), screen(MusicPage.Album(a2.id)))
        delete(albumTarget(a2))
        rescan(a2.id)
        rested()
        assertEquals("with no artist given the artist page stays", listOf(MusicPage.Hub, MusicPage.Artist(r2.id)), pages())
        val many = rows(Listing.Artists).first { rows(Listing.ArtistAlbums(it.id)).size > 1 }
        val a3 = rows(Listing.ArtistAlbums(many.id)).first()
        artist = many.id
        on(hub, screen(MusicPage.Artist(many.id)), screen(MusicPage.Album(a3.id)))
        delete(albumTarget(a3))
        rescan(a3.id)
        rested()
        assertEquals("an artist with albums left stays", listOf(MusicPage.Hub, MusicPage.Artist(many.id)), pages())
    }

    @Test
    fun aPopAboveAnErasedArtistPageIsT4NotT4m() {
        launch()
        val (r, a) = soleArtists().first()
        artist = r.id
        val hub = screen(MusicPage.Hub)
        val art = screen(MusicPage.Artist(r.id))
        val album = screen(MusicPage.Album(a.id), PushFrom(art.entry, 0, ALBUMS))
        on(hub, art, album)
        assertEquals("premise: the hub sits on the push source's albums pivot", HUB_ALBUMS, rule.runOnIdle { (app.models[hub.entry] as HubModel).pivot })
        assertEquals("premise: the pop onto the artist page morphs", MoveKind.T4m, rule.runOnIdle { app.host.popKind(Turn(MoveKind.None, album, art, popping = true)) })
        delete(albumTarget(a))
        scan(a.id)
        assertEquals("the pop lands on the hub after the erase", MoveKind.T4, popKind())
        rested()
        assertEquals(listOf(MusicPage.Hub), pages())
    }

    @Test
    fun aBusyReadKeepsTheExitOwed() {
        launch()
        val a = rows(Listing.Albums).first()
        val album = screen(MusicPage.Album(a.id))
        on(screen(MusicPage.Hub), album)
        delete(albumTarget(a))
        fake.busyNext("scan running", Listing.Albums)
        rescan(a.id)
        assertSame("a busy read keeps the page", album, top())
        assertEquals("the busy read logs one line", 1, app.logs.size)
        assertTrue("the line names the album, ${app.logs}", "${a.id}" in app.logs[0])
        rescan()
        rested()
        assertEquals("the next rescan pops it", listOf(MusicPage.Hub), pages())
    }

    @Test
    fun aBusyReadIsReadAgainAtTheScanEnd() {
        launch()
        val a = rows(Listing.Albums).first()
        val album = screen(MusicPage.Album(a.id))
        on(screen(MusicPage.Hub), album)
        delete(albumTarget(a))
        fake.busyNext("scan running", Listing.Albums)
        rescan(a.id)
        assertSame("premise: the busy read keeps the page", album, top())
        val g = gen()
        fake.failNextScan()
        rescan()
        assertEquals("premise: the scan ends with no new generation", g, gen())
        rested()
        assertEquals("the scan end reads the exit again", listOf(MusicPage.Hub), pages())
    }

    @Test
    fun anUnlinkDuringTheExitReadKeepsItOwed() {
        launch()
        val a = rows(Listing.Albums).first()
        val album = screen(MusicPage.Album(a.id))
        on(screen(MusicPage.Hub), album)
        delete(albumTarget(a))
        unlinked = true
        rescan(a.id)
        assertFalse("premise: the read met the unlink", unlinked)
        assertSame("an unlinked read keeps the page", album, top())
        assertEquals(1, app.logs.size)
        rescan()
        rested()
        assertEquals("the next generation pops it", listOf(MusicPage.Hub), pages())
    }

    @Test
    fun aSongDeleteAndAPartlyAlbumOweNoExit() {
        launch()
        val a = rows(Listing.Albums).first { songs(it.id) > 1 }
        val album = screen(MusicPage.Album(a.id))
        on(screen(MusicPage.Hub), album)
        delete(songTarget(rows(Listing.AlbumTracks(a.id)).first()))
        outcome = { DeleteDone.Partly(1) }
        delete(albumTarget(a))
        toasted("1 song not deleted", a.title)
        rescan(a.id)
        assertSame("no exit is owed", album, top())
        assertEquals(2, asked.size)
    }

    @Test
    fun aSourceChangeAHeldCardAndANewCardDropTheOwedExit() {
        launch()
        val albums = rows(Listing.Albums)
        val hub = screen(MusicPage.Hub)
        val first = screen(MusicPage.Album(albums[0].id))
        on(hub, first)
        delete(albumTarget(albums[0]))
        rule.runOnIdle { app.source = source() }
        frames(OPEN_FRAMES)
        rescan(albums[0].id)
        assertSame("a source change drops the owed exit", first, top())
        val second = screen(MusicPage.Album(albums[1].id))
        on(hub, second)
        delete(albumTarget(albums[1]))
        fake.card(Card.Held)
        frames(OPEN_FRAMES)
        fake.card(Card.Present)
        frames(OPEN_FRAMES)
        rescan(albums[1].id)
        assertSame("a held card drops the owed exit", second, top())
        val third = screen(MusicPage.Album(albums[2].id))
        on(hub, third)
        delete(albumTarget(albums[2]))
        rule.runOnIdle { app.host.closeLibrarySheets(newCard = true) }
        frames(OPEN_FRAMES)
        rescan(albums[2].id)
        assertSame("a new card drops the owed exit", third, top())
    }

    @Test
    fun aHeldCardWhileTheLeaveWaitsUnderASheetPopsNothing() {
        launch()
        val a = rows(Listing.Albums).first()
        val album = screen(MusicPage.Album(a.id))
        on(screen(MusicPage.Hub), album)
        delete(albumTarget(a))
        rule.runOnIdle { app.host.openMenu(MenuForm.Sheet, "sheet", listOf("one", "two"), false, -1, 0, false, { true }) }
        frames(OPEN_FRAMES)
        rescan(a.id)
        assertSame("premise: the leave waits under the sheet", album, top())
        fake.card(Card.Held)
        frames(OPEN_FRAMES)
        rule.runOnIdle { stack.close() }
        frames(LEAVE_FRAMES)
        assertNull("premise: the sheet is gone", rule.runOnIdle { stack.overlay })
        assertSame("the dropped exit pops nothing", album, top())
    }

    @Test
    fun twoAlbumsDeletedThenOneRescanPopOnce() {
        launch()
        val (a, b) = rows(Listing.Albums).take(2)
        on(screen(MusicPage.Hub), screen(MusicPage.Album(a.id)), screen(MusicPage.Album(b.id)))
        delete(albumTarget(a))
        delete(albumTarget(b))
        fake.dropAtRescan(a.id)
        scan(b.id)
        assertEquals("one pass pops once", 1, pops(LEAVE_FRAMES))
        rested()
        assertEquals(listOf(MusicPage.Hub), pages())
    }

    @Test
    fun theLibraryCloseCutsTheSheetsTheirSwapsTheConfirmsAndTheJumpGridOnly() {
        launch()
        val s = song()
        val hub = screen(MusicPage.Hub)
        on(hub)
        fun cut(what: String) {
            assertTrue("premise: $what is up", rule.runOnIdle { stack.overlay.let { it != null && !it.closing } })
            rule.runOnIdle { app.host.closeLibrarySheets() }
            frames(CUT_FRAMES)
            assertNull("$what is cut with no close motion", rule.runOnIdle { stack.overlay })
            frames(OPEN_FRAMES)
        }
        sheet(songTarget(s))
        cut("the song sheet")
        sheet(songTarget(s))
        pick(ADD_TO_PLAYLIST)
        assertEquals("premise: the picker swapped in", NEW_PLAYLIST, rule.runOnIdle { app.menu.items.first() })
        cut("the picker")
        sheet(songTarget(s))
        pick(DELETE)
        cut("the confirm")
        rule.runOnIdle { app.host.openJump(hub) }
        frames(OPEN_FRAMES)
        assertEquals("premise: the jump grid is up", OverKind.Jump, rule.runOnIdle { stack.overlay?.kind })
        cut("the jump grid")
        rule.runOnIdle { app.host.openMenu(MenuForm.Sheet, "settings", listOf("one", "two"), false, -1, 0, false, { true }) }
        frames(OPEN_FRAMES)
        val kept = rule.runOnIdle { stack.overlay }
        rule.runOnIdle { app.host.closeLibrarySheets() }
        frames(OPEN_FRAMES)
        assertSame("a host sheet no library sheet opened stays", kept, rule.runOnIdle { stack.overlay })
        assertFalse(kept!!.closing)
    }

    @Test
    fun theLibraryCloseCutsASongSheetOrJumpGridWhoseOpenIsQueued() {
        launch()
        val s = song()
        val hub = screen(MusicPage.Hub)
        on(hub)
        rule.runOnIdle {
            assertTrue("premise: the sheet asks to open", app.host.openMusic(songTarget(s)))
            app.host.closeLibrarySheets()
        }
        frames(OPEN_FRAMES)
        assertNull("the queued song sheet is cut", rule.runOnIdle { stack.overlay })
        rule.runOnIdle {
            app.host.openJump(hub)
            app.host.closeLibrarySheets()
        }
        frames(OPEN_FRAMES)
        assertNull("the queued jump grid is cut", rule.runOnIdle { stack.overlay })
    }

    @Test
    fun theLibraryCloseFiresNoPendingJumpPick() {
        launch(root = true)
        val hub = screen(MusicPage.Hub)
        on(hub)
        val m = rule.runOnIdle { app.models[hub.entry] as HubModel }
        until("premise: the albums pane loads") { m.pane.map != null }
        assertEquals("premise: the hub is on its albums pivot", HUB_ALBUMS, rule.runOnIdle { m.pivot })
        rule.runOnIdle { app.host.openJump(hub) }
        frames(OPEN_FRAMES)
        val map = requireNotNull(rule.runOnIdle { m.pane.map })
        val letter = (LETTERS - 1 downTo 0).first { map.has(it) }
        val q = rule.runOnIdle { m.pane.track.q }
        rule.runOnIdle {
            m.jumpPick = letter
            m.colPick = letter
            app.host.closeLibrarySheets()
        }
        frames(LEAVE_FRAMES)
        rule.runOnIdle {
            assertNull(stack.overlay)
            assertEquals("no column fires", listOf(MusicPage.Hub), pages())
            assertEquals("the hub has not moved", q, m.pane.track.q)
        }
    }

    @Test
    fun theLibraryCloseShutsTheSearchFieldAndLeavesAnEmptySearch() {
        launch()
        val hub = screen(MusicPage.Hub)
        val empty = screen(MusicPage.Search(""))
        on(hub, empty)
        val m = rule.runOnIdle { app.models[empty.entry] as HubModel }
        rule.runOnIdle { m.openField() }
        assertTrue("premise: the field is open", rule.runOnIdle { m.search?.open == true })
        rule.runOnIdle { app.host.closeLibrarySheets() }
        frames(LEAVE_FRAMES)
        rested()
        assertFalse("the field shuts", rule.runOnIdle { m.search?.open == true })
        assertEquals("an empty search leaves", listOf(MusicPage.Hub), pages())
        val query = screen(MusicPage.Search(QUERY))
        on(hub, query)
        val k = rule.runOnIdle { app.models[query.entry] as HubModel }
        rule.runOnIdle { k.openField() }
        rule.runOnIdle { app.host.closeLibrarySheets() }
        frames(LEAVE_FRAMES)
        assertFalse("the field shuts", rule.runOnIdle { k.search?.open == true })
        assertEquals("a search with a query stays", listOf(MusicPage.Hub, MusicPage.Search(QUERY)), pages())
    }

    private fun artistTarget(): SheetTarget {
        val r = rows(Listing.Artists).first()
        return SheetTarget(SheetKind.Artist, r.title, Listing.ArtistTracks(r.id), gen(), pin = ApiKind.Artist, id = r.id, from = SheetFrom.Hub, pinned = false)
    }

    @Test
    fun anExtraRunsAfterTheKitWordsAndItsSheetOpensAsASwap() {
        launch()
        var acts = 0
        val picks = mutableListOf<Int>()
        app.extras = { t ->
            if (t.kind != SheetKind.Artist) {
                emptyList()
            } else {
                listOf(
                    SheetExtra(PICK_IMAGE) {
                        acts++
                        ExtraSheet(PICK_IMAGE, listOf("one", "two")) { i ->
                            picks += i
                            true
                        }
                    },
                )
            }
        }
        on(screen(MusicPage.Hub))
        val t = artistTarget()
        sheet(t)
        assertEquals(sheetItems(t, false) + PICK_IMAGE, rule.runOnIdle { app.menu.items })
        val o = rule.runOnIdle { stack.overlay }
        pick(PICK_IMAGE)
        assertEquals(1, acts)
        rule.runOnIdle {
            assertSame("the extra's sheet swaps into the same overlay", o, stack.overlay)
            assertFalse(o!!.closing)
            assertEquals(PICK_IMAGE, app.menu.title)
            assertEquals(listOf("one", "two"), app.menu.items)
        }
        pick("two")
        assertEquals(listOf(1), picks)
        assertTrue("a pick that answers true closes it", rule.runOnIdle { stack.overlay.let { it == null || it.closing } })
    }

    @Test
    fun anExtraWithNoSheetClosesTheSheet() {
        launch()
        var acts = 0
        app.extras = {
            listOf(
                SheetExtra(PICK_IMAGE) {
                    acts++
                    null
                },
            )
        }
        on(screen(MusicPage.Hub))
        sheet(artistTarget())
        pick(PICK_IMAGE)
        assertEquals(1, acts)
        assertTrue("the sheet closes", rule.runOnIdle { stack.overlay.let { it == null || it.closing } })
    }

    companion object {
        private const val W = 720
        private const val H = 1280
        private const val SDK = 35
        private const val OPEN_FRAMES = 30
        private const val CUT_FRAMES = 2
        private const val RISE_FRAMES = 6
        private const val LEAVE_FRAMES = 80
        private const val MAX_FRAMES = 400
        private const val LETTERS = 40
        private const val HALF = 0.5f
        private const val SCRIM_UP = 40f
        private const val PICK_IMAGE = "pick image"
        private const val QUERY = "rain"
    }
}
