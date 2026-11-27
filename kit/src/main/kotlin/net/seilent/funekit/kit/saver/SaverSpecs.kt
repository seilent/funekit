package net.seilent.funekit.kit.saver

import net.seilent.funekit.Weight
import net.seilent.funekit.fx
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.gen.TokBoard

enum class SaverScene { A, B, D, E, F, H, S1, K, P, R, J, L, M, N, Q }

enum class SaverStage { Scene, Whoosh, Gap, Cut, In, Hold, Out, Black }

internal enum class Src { None, Title, Artist, Album, Time, First, Art }

internal enum class Look { Plain, Eased, Slide }

private const val A3_PIECES = 5
private const val B3_PIECES = 4
private const val E3_PIECES = 2
private const val H8_PIECES = 5
private const val S5_PIECES = 4
private const val K4_PIECES = 5
private const val P2_PIECES = 3
private const val R2_PIECES = 6
private const val R3_PIECES = 3

internal class Spec(
    val scene: SaverScene,
    val angle: Int,
    val src: Src,
    val src2: Src,
    val w: Weight,
    val w2: Weight,
    val size: Long,
    val speed: Long,
    val inAt: Long,
    val across: Long,
    val whoosh: Long,
    val repeat: Boolean,
    val look: Look,
    val on: Long = 0,
    val off: Long = 0,
    val tall: Long = 0,
    val pieces: Int = 0,
    val join: Long = 0,
    val period: Long = 0,
    val gap: Long = 0,
    val shift: Long = 0,
    val shiftT: Long = 0,
    val fadeT: Long = 0,
    val trail: Long = 0,
    val top: Boolean = false,
    val tail: Boolean = false,
    val even: Boolean = false,
    val split: Long = 0,
    val roll: Long = 0,
    val touch: Long = 0,
    val nominal: Boolean = false,
)

internal fun weightOf(style: String): Weight = when (style) {
    "Light" -> Weight.Light
    "Bold" -> Weight.Bold
    "SemiLight" -> Weight.SemiLight
    else -> Weight.Regular
}

internal fun specsOf(b: TokBoard.Board): List<Spec> = listOf(
        Spec(SaverScene.A, -DIAG, Src.Artist, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverGiant), fx(b.motion_saverA1Speed), fx(Tok.motion_saverA1In), fx(b.saverA1Across), fx(b.motion_saverA1Whoosh), false, Look.Eased, shift = fx(b.saverA1Shift), shiftT = fx(Tok.motion_saverA1Entry)),
        Spec(SaverScene.A, -DIAG, Src.Album, Src.Artist, Weight.Regular, Weight.Regular, fx(b.saverRow), fx(b.motion_saverA2Speed), fx(Tok.motion_saverA2In), fx(b.saverA2Across), fx(b.motion_saverA2Whoosh), true, Look.Plain, gap = fx(b.saverA2Gap)),
        Spec(SaverScene.A, -DIAG, Src.Album, Src.Time, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverA3Speed), fx(Tok.motion_saverA3In), fx(b.saverA3Across), fx(b.motion_saverA3Whoosh), true, Look.Plain, pieces = A3_PIECES, gap = fx(b.saverA3Gap), split = fx(b.saverA3Split)),
        Spec(SaverScene.A, -DIAG, Src.Title, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverA4Speed), fx(Tok.motion_saverA4In), fx(b.saverA4Across), fx(b.motion_saverA4Whoosh), false, Look.Plain),
        Spec(SaverScene.A, -DIAG, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileA5), fx(b.motion_saverA5Speed), fx(Tok.motion_saverA5In), fx(b.saverA5Across), fx(b.motion_saverA5Whoosh), false, Look.Plain),
        Spec(SaverScene.A, DIAG, Src.Time, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverDigits), fx(b.motion_saverA6Speed), fx(Tok.motion_saverA6In), fx(b.saverA6Across), fx(b.motion_saverA6Whoosh), false, Look.Plain),
        Spec(SaverScene.A, DIAG, Src.Title, Src.None, Weight.Bold, Weight.Bold, fx(b.saverRow), fx(b.motion_saverA7Speed), fx(Tok.motion_saverA7In), fx(b.saverA7Across), fx(b.motion_saverA7Whoosh), false, Look.Plain),
        Spec(SaverScene.A, DIAG, Src.Time, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverDigits), fx(b.motion_saverA8Speed), fx(Tok.motion_saverA8In), fx(b.saverA8Across), fx(b.motion_saverA8Whoosh), false, Look.Plain),
        Spec(SaverScene.B, DIAG, Src.Title, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverB1Size), fx(b.motion_saverB1Speed), fx(Tok.motion_saverB1In), fx(b.saverB1Across), fx(b.motion_saverB1Whoosh), false, Look.Plain),
        Spec(SaverScene.B, DIAG, Src.Artist, Src.Album, Weight.Regular, Weight.Regular, fx(b.saverRow), fx(b.motion_saverB2Speed), fx(Tok.motion_saverB2In), fx(b.saverB2Across), fx(b.motion_saverB2Whoosh), true, Look.Plain),
        Spec(SaverScene.B, DIAG, Src.Title, Src.Time, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverB3Speed), fx(b.motion_saverB3In), fx(b.saverB3Across), fx(b.motion_saverB3Whoosh), true, Look.Plain, pieces = B3_PIECES, gap = fx(b.saverB3Gap), split = fx(b.saverB3Split)),
        Spec(SaverScene.B, DIAG, Src.Album, Src.Time, Weight.Regular, Weight.SemiLight, fx(b.saverLargeCjk), fx(b.motion_saverB4Speed), fx(Tok.motion_saverB4In), fx(b.saverB4Across), fx(b.motion_saverB4Whoosh), true, Look.Plain, period = fx(b.saverB4Period)),
        Spec(SaverScene.B, DIAG, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileB5), fx(b.motion_saverB5Speed), fx(Tok.motion_saverB5In), fx(b.saverB5Across), fx(b.motion_saverB5Whoosh), false, Look.Plain),
        Spec(SaverScene.B, -DIAG, Src.Time, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverDigits), fx(b.motion_saverB6Speed), fx(Tok.motion_saverB6In), fx(b.saverB6Across), fx(b.motion_saverB6Whoosh), false, Look.Plain),
        Spec(SaverScene.B, -DIAG, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileB7), fx(b.motion_saverB7Speed), fx(Tok.motion_saverB7In), fx(b.saverB7Across), fx(b.motion_saverB7Whoosh), false, Look.Plain),
        Spec(SaverScene.D, DIAG, Src.Time, Src.Album, Weight.SemiLight, Weight.Bold, fx(b.saverGiantDigits), fx(b.motion_saverD1Speed), fx(Tok.motion_saverD1In), fx(b.saverD1Across), fx(b.motion_saverD1Whoosh), false, Look.Plain, join = fx(b.saverD1Join)),
        Spec(SaverScene.D, DIAG, Src.Artist, Src.Album, Weight.Regular, Weight.Regular, fx(b.saverRow), fx(b.motion_saverD2Speed), fx(Tok.motion_saverD2In), fx(b.saverD2Across), fx(b.motion_saverD2Whoosh), true, Look.Plain),
        Spec(SaverScene.D, DIAG, Src.Album, Src.Title, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverD3Speed), fx(b.motion_saverD3In), fx(b.saverD3Across), fx(b.motion_saverD3Whoosh), true, Look.Plain, gap = fx(b.saverD3Gap), split = fx(b.saverD3Split)),
        Spec(SaverScene.D, DIAG, Src.Title, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverD4Size), fx(b.motion_saverD4Speed), fx(Tok.motion_saverD4In), fx(b.saverD4Across), fx(b.motion_saverD4Whoosh), false, Look.Plain),
        Spec(SaverScene.D, DIAG, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileD5), fx(b.motion_saverD5Speed), fx(Tok.motion_saverD5In), fx(b.saverD5Across), fx(b.motion_saverD5Whoosh), false, Look.Plain),
        Spec(SaverScene.D, -DIAG, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileD6), fx(b.motion_saverD6Speed), fx(b.motion_saverD6In), fx(b.saverD6Across), fx(b.motion_saverD6Whoosh), false, Look.Plain),
        Spec(SaverScene.E, 0, Src.Artist, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverGiant), fx(b.motion_saverE1Speed), fx(Tok.motion_saverE1In), fx(b.saverE1Across), fx(b.motion_saverE1Whoosh), true, Look.Eased, shift = fx(b.saverE1Shift), shiftT = fx(Tok.motion_saverE1Entry), fadeT = fx(Tok.motion_saverE1Fade)),
        Spec(SaverScene.E, 0, Src.Album, Src.Title, Weight.Regular, Weight.Bold, fx(b.saverRowE), fx(b.motion_saverE2Speed), fx(Tok.motion_saverE2In), fx(b.saverE2Across), fx(b.motion_saverE2Whoosh), true, Look.Plain, period = fx(b.saverE2Period), even = true, nominal = true),
        Spec(SaverScene.E, 0, Src.Artist, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmallE), fx(b.motion_saverE3Speed), fx(Tok.motion_saverE3In), fx(b.saverE3Across), fx(b.motion_saverE3Whoosh), true, Look.Plain, pieces = E3_PIECES, gap = fx(b.saverE3Gap)),
        Spec(SaverScene.E, 0, Src.Title, Src.Time, weightOf(Tok.type_saverFourth_style), Weight.Bold, fx(b.type_saverFourth_size), fx(b.motion_saverE4Speed), fx(Tok.motion_saverE4In), fx(b.saverFourthBaseline), fx(b.motion_saverE4Whoosh), true, Look.Plain, touch = fx(b.saverE4Touch)),
        Spec(SaverScene.E, 0, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileE5), fx(b.motion_saverE5Speed), fx(Tok.motion_saverE5In), fx(b.saverE5Across), fx(b.motion_saverE5Whoosh), false, Look.Plain, on = fx(Tok.motion_saverE5On), off = fx(Tok.motion_saverE5Off), tall = fx(b.saverTileE5Tall)),
        Spec(SaverScene.F, DIAG, Src.Artist, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverLarge), fx(b.motion_saverF1Speed), fx(Tok.motion_saverF1In), fx(b.saverF1Across), fx(b.motion_saverF1Whoosh), true, Look.Slide, gap = fx(b.saverF1Gap), shift = fx(b.saverF1Shift), shiftT = fx(Tok.motion_saverF1Entry), fadeT = fx(Tok.motion_saverFFade), trail = fx(b.saverF1Trail), tail = true),
        Spec(SaverScene.F, DIAG, Src.Album, Src.None, Weight.Bold, Weight.Bold, fx(b.saverF2Size), fx(b.motion_saverF2Speed), fx(Tok.motion_saverF2In), fx(b.saverF2Across), fx(b.motion_saverF2Whoosh), true, Look.Slide, gap = fx(b.saverF2Gap), shift = fx(b.saverF2Shift), shiftT = fx(Tok.motion_saverF2Entry), fadeT = fx(Tok.motion_saverFFade), trail = fx(b.saverF2Trail), tail = true),
        Spec(SaverScene.F, DIAG, Src.First, Src.None, Weight.Bold, Weight.Bold, fx(b.saverGiantCjk), fx(b.motion_saverF3Speed), fx(b.motion_saverF3In), fx(b.saverF3Across), fx(b.motion_saverF3Whoosh), false, Look.Plain),
        Spec(SaverScene.F, -DIAG, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileF4), fx(b.motion_saverF4Speed), fx(Tok.motion_saverF4In), fx(b.saverF4Across), fx(b.motion_saverF4Whoosh), false, Look.Plain),
        Spec(SaverScene.F, DIAG, Src.Album, Src.Title, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverF5Speed), fx(b.motion_saverF5In), fx(b.saverF5Across), fx(b.motion_saverF5Whoosh), true, Look.Plain, gap = fx(b.saverF5Gap), split = fx(b.saverF5Split)),
        Spec(SaverScene.H, 0, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileH1), fx(b.motion_saverH1Speed), fx(Tok.motion_saverH1In), fx(b.saverH1Across), fx(b.motion_saverH1Whoosh), false, Look.Slide, shift = fx(b.saverH1Shift), shiftT = fx(Tok.motion_saverH1Entry), fadeT = fx(Tok.motion_saverH1Fade), roll = fx(b.saverH1Roll)),
        Spec(SaverScene.H, 0, Src.First, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverGiant), fx(b.motion_saverH2Speed), fx(Tok.motion_saverH2In), fx(b.saverH2Across), fx(b.motion_saverH2Whoosh), false, Look.Plain, top = true),
        Spec(SaverScene.H, 0, Src.Album, Src.Title, Weight.Regular, Weight.Regular, fx(b.saverRowE), fx(b.motion_saverH3Speed), fx(Tok.motion_saverH3In), fx(b.saverH3Across), fx(b.motion_saverH3Whoosh), true, Look.Plain),
        Spec(SaverScene.H, 0, Src.Title, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverGiant), fx(b.motion_saverH4Speed), fx(Tok.motion_saverH4In), fx(b.saverH4Across), fx(b.motion_saverH4Whoosh), true, Look.Plain, top = true),
        Spec(SaverScene.H, 0, Src.Artist, Src.None, Weight.Bold, Weight.Bold, fx(b.saverLargeBlack), fx(b.motion_saverH5Speed), fx(Tok.motion_saverH5In), fx(b.saverH5Across), fx(b.motion_saverH5Whoosh), true, Look.Plain),
        Spec(SaverScene.H, 0, Src.Album, Src.Title, Weight.Regular, Weight.Regular, fx(b.saverRowE), fx(b.motion_saverH6Speed), fx(Tok.motion_saverH6In), fx(b.saverH6Across), fx(b.motion_saverH6Whoosh), true, Look.Plain, gap = fx(b.saverH6Gap), split = fx(b.saverH6Split)),
        Spec(SaverScene.H, 0, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileH7), fx(b.motion_saverH7Speed), fx(Tok.motion_saverH7In), fx(b.saverH7Across), fx(b.motion_saverH7Whoosh), false, Look.Plain),
        Spec(SaverScene.H, 0, Src.Title, Src.Time, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverH8Speed), fx(Tok.motion_saverH8In), fx(b.saverH8Across), fx(b.motion_saverH8Whoosh), true, Look.Plain, pieces = H8_PIECES),
        Spec(SaverScene.S1, UP, Src.Title, Src.None, Weight.Bold, Weight.Bold, fx(b.saverS1Size), fx(b.motion_saverS1Speed), fx(Tok.motion_saverS1In), fx(b.saverS1Across), fx(b.motion_saverS1Whoosh), false, Look.Plain),
        Spec(SaverScene.S1, UP, Src.Artist, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverS2Speed), fx(Tok.motion_saverS2In), fx(b.saverS2Across), fx(b.motion_saverS2Whoosh), false, Look.Plain),
        Spec(SaverScene.S1, 0, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileS3), fx(b.motion_saverS3Speed), fx(Tok.motion_saverS3In), fx(b.saverS3Across), fx(b.motion_saverS3Whoosh), false, Look.Plain),
        Spec(SaverScene.S1, 0, Src.Title, Src.Album, Weight.SemiLight, Weight.Bold, fx(b.saverS4Size), fx(b.motion_saverS4Speed), fx(Tok.motion_saverS4In), fx(b.saverS4Across), fx(b.motion_saverS4Whoosh), true, Look.Plain, period = fx(b.saverS4Period)),
        Spec(SaverScene.S1, 0, Src.Title, Src.Time, Weight.Bold, Weight.Bold, fx(b.saverS5Size), fx(b.motion_saverS5Speed), fx(Tok.motion_saverS5In), fx(b.saverS5Across), fx(b.motion_saverS5Whoosh), true, Look.Plain, pieces = S5_PIECES),
        Spec(SaverScene.S1, 0, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileS6), fx(b.motion_saverS6Speed), fx(Tok.motion_saverS6In), fx(b.saverS6Across), fx(b.motion_saverS6Whoosh), false, Look.Plain),
        Spec(SaverScene.S1, 0, Src.Artist, Src.None, Weight.Regular, Weight.Regular, fx(b.saverRowE), fx(b.motion_saverS7Speed), fx(Tok.motion_saverS7In), fx(b.saverS7Across), fx(b.motion_saverS7Whoosh), false, Look.Plain),
        Spec(SaverScene.K, 0, Src.Album, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverGiant), fx(b.motion_saverK1Speed), fx(Tok.motion_saverK1In), fx(b.saverK1Across), fx(b.motion_saverK1Whoosh), true, Look.Eased, shift = fx(b.saverK1Shift), shiftT = fx(Tok.motion_saverK1Entry), fadeT = fx(Tok.motion_saverK1Fade), top = true),
        Spec(SaverScene.K, 0, Src.Album, Src.Artist, Weight.Bold, Weight.SemiLight, fx(b.saverRowE), fx(b.motion_saverK2Speed), fx(Tok.motion_saverK2In), fx(b.saverK2Across), fx(b.motion_saverK2Whoosh), true, Look.Plain, gap = fx(b.saverK2Gap), split = fx(b.saverK2Split), roll = fx(b.saverK2Roll)),
        Spec(SaverScene.K, 0, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileK3), fx(b.motion_saverK3Speed), fx(Tok.motion_saverK3In), fx(b.saverK3Across), fx(b.motion_saverK3Whoosh), false, Look.Plain),
        Spec(SaverScene.K, 0, Src.Title, Src.Time, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverK4Speed), fx(Tok.motion_saverK4In), fx(b.saverK4Across), fx(b.motion_saverK4Whoosh), true, Look.Plain, pieces = K4_PIECES),
        Spec(SaverScene.K, 0, Src.Album, Src.Title, Weight.SemiLight, Weight.SemiLight, fx(b.saverLarge), fx(b.motion_saverK5Speed), fx(Tok.motion_saverK5In), fx(b.saverK5Across), fx(b.motion_saverK5Whoosh), true, Look.Plain),
        Spec(SaverScene.K, 0, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileK6), fx(b.motion_saverK6Speed), fx(Tok.motion_saverK6In), fx(b.saverK6Across), fx(b.motion_saverK6Whoosh), false, Look.Plain),
        Spec(SaverScene.P, 0, Src.Artist, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverGiant), fx(b.motion_saverP1Speed), fx(Tok.motion_saverP1In), fx(b.saverP1Across), fx(b.motion_saverP1Whoosh), true, Look.Slide, shift = fx(b.saverP1Shift), shiftT = fx(Tok.motion_saverP1Entry), fadeT = fx(Tok.motion_saverP1Fade), top = true),
        Spec(SaverScene.P, 0, Src.Album, Src.Title, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverP2Speed), fx(Tok.motion_saverP2In), fx(b.saverP2Across), fx(b.motion_saverP2Whoosh), true, Look.Plain, pieces = P2_PIECES, gap = fx(b.saverP2Gap), split = fx(b.saverP2Split)),
        Spec(SaverScene.P, 0, Src.Title, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverLargeCjk), fx(b.motion_saverP3Speed), fx(Tok.motion_saverP3In), fx(b.saverP3Across), fx(b.motion_saverP3Whoosh), true, Look.Plain),
        Spec(SaverScene.P, 0, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileP4), fx(b.motion_saverP4Speed), fx(Tok.motion_saverP4In), fx(b.saverP4Across), fx(b.motion_saverP4Whoosh), false, Look.Plain),
        Spec(SaverScene.P, 0, Src.Artist, Src.None, Weight.Bold, Weight.Bold, fx(b.saverS5Size), fx(b.motion_saverP5Speed), fx(Tok.motion_saverP5In), fx(b.saverP5Across), fx(b.motion_saverP5Whoosh), false, Look.Plain),
        Spec(SaverScene.R, 0, Src.Album, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverR1Size), fx(b.motion_saverR1Speed), fx(Tok.motion_saverR1In), fx(b.saverR1Across), fx(b.motion_saverR1Whoosh), true, Look.Slide, shift = fx(b.saverR1Shift), shiftT = fx(Tok.motion_saverR1Entry)),
        Spec(SaverScene.R, 0, Src.Title, Src.Time, Weight.SemiLight, Weight.SemiLight, fx(b.saverLargeCjk), fx(b.motion_saverR2Speed), fx(Tok.motion_saverR2In), fx(b.saverR2Across), fx(b.motion_saverR2Whoosh), true, Look.Plain, pieces = R2_PIECES),
        Spec(SaverScene.R, 0, Src.Artist, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverR3Size), fx(b.motion_saverR3Speed), fx(Tok.motion_saverR3In), fx(b.saverR3Across), fx(b.motion_saverR3Whoosh), true, Look.Plain, pieces = R3_PIECES),
        Spec(SaverScene.R, 0, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileR4), fx(b.motion_saverR4Speed), fx(Tok.motion_saverR4In), fx(b.saverR4Across), fx(b.motion_saverR4Whoosh), false, Look.Plain),
        Spec(SaverScene.J, -DIAG, Src.Title, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverB1Size), fx(b.motion_saverJ1Speed), fx(Tok.motion_saverJ1In), fx(b.saverJ1Across), fx(b.motion_saverJ1Whoosh), true, Look.Eased, gap = fx(b.saverF1Gap), shift = fx(b.saverA1Shift), shiftT = fx(Tok.motion_saverA1Entry)),
        Spec(SaverScene.J, -DIAG, Src.Artist, Src.Album, Weight.Regular, Weight.Regular, fx(b.saverRow), fx(b.motion_saverJ2Speed), fx(Tok.motion_saverJ2In), fx(b.saverJ2Across), fx(b.motion_saverJ2Whoosh), true, Look.Plain, gap = fx(b.saverF2Gap)),
        Spec(SaverScene.J, -DIAG, Src.Artist, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverLarge), fx(b.motion_saverJ3Speed), fx(Tok.motion_saverJ3In), fx(b.saverJ3Across), fx(b.motion_saverJ3Whoosh), true, Look.Plain, gap = fx(b.saverF1Gap)),
        Spec(SaverScene.J, -DIAG, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileM), fx(b.motion_saverJ4Speed), fx(Tok.motion_saverJ4In), fx(b.saverJ4Across), fx(b.motion_saverJ4Whoosh), false, Look.Plain),
        Spec(SaverScene.J, -DIAG, Src.Title, Src.Time, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverJ5Speed), fx(Tok.motion_saverJ5In), fx(b.saverJ5Across), fx(b.motion_saverJ5Whoosh), false, Look.Plain),
        Spec(SaverScene.J, DIAG, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileM), fx(b.motion_saverJ6Speed), fx(Tok.motion_saverJ6In), fx(b.saverJ6Across), fx(b.motion_saverJ6Whoosh), false, Look.Plain),
        Spec(SaverScene.L, -DIAG, Src.Album, Src.Time, Weight.Bold, Weight.SemiLight, fx(b.saverGiantDigits), fx(b.motion_saverL1Speed), fx(Tok.motion_saverL1In), fx(b.saverL1Across), fx(b.motion_saverL1Whoosh), true, Look.Eased, gap = fx(b.saverF1Gap), shift = fx(b.saverA1Shift), shiftT = fx(Tok.motion_saverA1Entry)),
        Spec(SaverScene.L, -DIAG, Src.Artist, Src.Title, Weight.Regular, Weight.Regular, fx(b.saverRow), fx(b.motion_saverL2Speed), fx(Tok.motion_saverL2In), fx(b.saverL2Across), fx(b.motion_saverL2Whoosh), true, Look.Plain),
        Spec(SaverScene.L, -DIAG, Src.Album, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverL3Speed), fx(Tok.motion_saverL3In), fx(b.saverL3Across), fx(b.motion_saverL3Whoosh), false, Look.Plain),
        Spec(SaverScene.L, -DIAG, Src.Title, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverLarge), fx(b.motion_saverL4Speed), fx(Tok.motion_saverL4In), fx(b.saverL4Across), fx(b.motion_saverL4Whoosh), true, Look.Plain),
        Spec(SaverScene.L, DIAG, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileS), fx(b.motion_saverL5Speed), fx(Tok.motion_saverL5In), fx(b.saverL5Across), fx(b.motion_saverL5Whoosh), false, Look.Plain),
        Spec(SaverScene.M, 0, Src.Time, Src.Album, Weight.SemiLight, Weight.Bold, fx(b.saverGiantDigits), fx(b.motion_saverM1Speed), fx(Tok.motion_saverM1In), fx(b.saverM1Across), fx(b.motion_saverM1Whoosh), true, Look.Eased, shift = fx(b.saverE1Shift), shiftT = fx(Tok.motion_saverE1Entry)),
        Spec(SaverScene.M, 0, Src.Title, Src.None, Weight.Bold, Weight.Bold, fx(b.saverRowE), fx(b.motion_saverM2Speed), fx(Tok.motion_saverM2In), fx(b.saverM2Across), fx(b.motion_saverM2Whoosh), true, Look.Plain),
        Spec(SaverScene.M, 0, Src.Artist, Src.None, Weight.Bold, Weight.Bold, fx(b.saverLargeBlack), fx(b.motion_saverM3Speed), fx(Tok.motion_saverM3In), fx(b.saverM3Across), fx(b.motion_saverM3Whoosh), true, Look.Plain),
        Spec(SaverScene.M, 0, Src.Album, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverM4Speed), fx(Tok.motion_saverM4In), fx(b.saverM4Across), fx(b.motion_saverM4Whoosh), false, Look.Plain),
        Spec(SaverScene.M, 0, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileM), fx(b.motion_saverM5Speed), fx(Tok.motion_saverM5In), fx(b.saverM5Across), fx(b.motion_saverM5Whoosh), false, Look.Plain),
        Spec(SaverScene.M, 0, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileM), fx(b.motion_saverM6Speed), fx(Tok.motion_saverM6In), fx(b.saverM6Across), fx(b.motion_saverM6Whoosh), false, Look.Plain),
        Spec(SaverScene.N, 0, Src.Artist, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverGiant), fx(b.motion_saverN1Speed), fx(Tok.motion_saverN1In), fx(b.saverN1Across), fx(b.motion_saverN1Whoosh), true, Look.Eased, shift = fx(b.saverE1Shift), shiftT = fx(Tok.motion_saverE1Entry)),
        Spec(SaverScene.N, 0, Src.Album, Src.None, Weight.Regular, Weight.Regular, fx(b.saverRowE), fx(b.motion_saverN2Speed), fx(Tok.motion_saverN2In), fx(b.saverN2Across), fx(b.motion_saverN2Whoosh), true, Look.Plain),
        Spec(SaverScene.N, 0, Src.Title, Src.Time, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverN3Speed), fx(Tok.motion_saverN3In), fx(b.saverN3Across), fx(b.motion_saverN3Whoosh), false, Look.Plain),
        Spec(SaverScene.N, 0, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileM), fx(b.motion_saverN4Speed), fx(Tok.motion_saverN4In), fx(b.saverN4Across), fx(b.motion_saverN4Whoosh), false, Look.Plain),
        Spec(SaverScene.N, UP, Src.Title, Src.None, Weight.Bold, Weight.Bold, fx(b.saverRowE), fx(b.motion_saverN5Speed), fx(Tok.motion_saverN5In), fx(b.saverN5Across), fx(b.motion_saverN5Whoosh), false, Look.Plain),
        Spec(SaverScene.N, UP, Src.Artist, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverN6Speed), fx(Tok.motion_saverN6In), fx(b.saverN6Across), fx(b.motion_saverN6Whoosh), false, Look.Plain),
        Spec(SaverScene.Q, 0, Src.Time, Src.Album, Weight.SemiLight, Weight.Bold, fx(b.saverGiantDigits), fx(b.motion_saverQ1Speed), fx(Tok.motion_saverQ1In), fx(b.saverQ1Across), fx(b.motion_saverQ1Whoosh), true, Look.Slide, shift = fx(b.saverP1Shift), shiftT = fx(Tok.motion_saverP1Entry)),
        Spec(SaverScene.Q, 0, Src.Artist, Src.None, Weight.Regular, Weight.Regular, fx(b.saverRowE), fx(b.motion_saverQ2Speed), fx(Tok.motion_saverQ2In), fx(b.saverQ2Across), fx(b.motion_saverQ2Whoosh), true, Look.Plain),
        Spec(SaverScene.Q, 0, Src.Title, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverQ3Speed), fx(Tok.motion_saverQ3In), fx(b.saverQ3Across), fx(b.motion_saverQ3Whoosh), false, Look.Plain),
        Spec(SaverScene.Q, 0, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileL), fx(b.motion_saverQ4Speed), fx(Tok.motion_saverQ4In), fx(b.saverQ4Across), fx(b.motion_saverQ4Whoosh), false, Look.Plain),
        Spec(SaverScene.Q, UP, Src.Title, Src.None, Weight.Regular, Weight.Regular, fx(b.saverRowE), fx(b.motion_saverQ5Speed), fx(Tok.motion_saverQ5In), fx(b.saverQ5Across), fx(b.motion_saverQ5Whoosh), false, Look.Plain),
        Spec(SaverScene.Q, UP, Src.Artist, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverSmall), fx(b.motion_saverQ6Speed), fx(Tok.motion_saverQ6In), fx(b.saverQ6Across), fx(b.motion_saverQ6Whoosh), false, Look.Plain),
        Spec(SaverScene.Q, 0, Src.Art, Src.None, Weight.SemiLight, Weight.SemiLight, fx(b.saverTileL), fx(b.motion_saverQ7Speed), fx(Tok.motion_saverQ7In), fx(b.saverQ7Across), fx(b.motion_saverQ7Whoosh), false, Look.Plain),
)

internal fun length(scene: SaverScene): Long = when (scene) {
    SaverScene.A -> fx(Tok.motion_saverALength)
    SaverScene.B -> fx(Tok.motion_saverBLength)
    SaverScene.D -> fx(Tok.motion_saverDLength)
    SaverScene.E -> fx(Tok.motion_saverELength)
    SaverScene.F -> fx(Tok.motion_saverFLength)
    SaverScene.H -> fx(Tok.motion_saverHLength)
    SaverScene.S1 -> fx(Tok.motion_saverSLength)
    SaverScene.K -> fx(Tok.motion_saverKLength)
    SaverScene.P -> fx(Tok.motion_saverPLength)
    SaverScene.R -> fx(Tok.motion_saverRLength)
    SaverScene.J -> fx(Tok.motion_saverJLength)
    SaverScene.L -> fx(Tok.motion_saverLLength)
    SaverScene.M -> fx(Tok.motion_saverMLength)
    SaverScene.N -> fx(Tok.motion_saverNLength)
    SaverScene.Q -> fx(Tok.motion_saverQLength)
}
