package net.seilent.funekit.kit.saver

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.createFontFamilyResolver
import net.seilent.funekit.Fonts
import net.seilent.funekit.Weight
import net.seilent.funekit.gen.Tok
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [28, 34])
class SaverTextTest {
    private val fonts = createFontFamilyResolver(RuntimeEnvironment.getApplication())
    private val text = SaverText(fonts)
    private val st = SaverStyle(Weight.SemiLight, 72, -96, false)

    @Test
    fun eachClusterTakesItsAdvanceTrackingAndUtf8Byte() {
        val r = text.run(st, "HÉ愛")
        assertEquals(3, r.g.size)
        assertEquals(listOf(0, 1, 3), r.g.map { it.byte })
        assertEquals(0, r.g[0].x64)
        assertEquals(text.width64(st, "H"), r.g[1].x64)
        assertEquals(text.run(st, "a").g[0].gid, text.run(st, "A").g[0].gid)
        assertTrue(text.run(st, "é").g[0].gid != text.run(st, "É").g[0].gid)
    }

    @Test
    fun theBoxIsTheFloorAndCeilOfTheClusterPathAndItsExtentsIn64ths() {
        val face = fonts.resolve(Fonts.selawik, Fonts.weight(Weight.SemiLight), FontStyle.Normal, FontSynthesis.None).value as Typeface
        val p = Paint().also {
            it.isAntiAlias = true
            it.isLinearText = true
            it.isSubpixelText = true
            it.typeface = face
            it.textSize = 72f
        }
        val path = Path()
        p.getTextPath("Q", 0, 1, 0f, 0f, path)
        val b = RectF()
        path.computeBounds(b, true)
        val g = text.run(st, "Q").g[0]
        assertEquals(
            SaverRect(floor(b.left).toInt(), floor(b.top).toInt(), ceil(b.right).toInt(), ceil(b.bottom).toInt()),
            text.box(g.face, 72, g.gid),
        )
        assertEquals(listOf((b.left * 64).roundToInt(), (b.right * 64).roundToInt()), text.hbox(g.face, 72, g.gid)!!.toList())
        assertEquals(listOf((b.top * 64).roundToInt() - 1, (b.bottom * 64).roundToInt() + 1), text.vbox(g.face, 72, g.gid)!!.toList())
        val space = text.run(st, " ").g[0]
        assertTrue(text.box(space.face, 72, space.gid)!!.empty)
        assertNull(text.hbox(space.face, 72, space.gid))
        assertNull(text.vbox(space.face, 72, space.gid))
    }

    @Test
    fun cjkOnTheSaverFacesTakesTheSaverEm() {
        val plain = text.run(st, "愛")
        val saver = text.run(st.copy(saver = true), "愛")
        assertEquals(8, plain.g[0].face)
        assertEquals(4, saver.g[0].face)
        assertEquals(5, text.run(st.copy(saver = true), "あ").g[0].face)
        assertEquals(6, text.run(st.copy(saver = true), "ｱ").g[0].face)
        assertEquals(7, text.run(st.copy(saver = true), "한").g[0].face)
        val ratio = saver.width64.toDouble() / plain.width64
        assertEquals(Tok.saverCjkEm.toDouble(), ratio, 0.03)
    }
}
