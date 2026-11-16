package net.seilent.funekit.kit.motion

import net.seilent.funekit.gen.Tok

private const val LEGS = 2

object VariantMotion {
    enum class Dir { In, Out }

    data class Frame(val from: Boolean, val alpha: Float, val scale: Float)

    fun total(dir: Dir): Long =
        (if (dir == Dir.In) Tok.motion_nowPlayingFade + Tok.motion_nowPlayingIn else LEGS * Tok.motion_variantFade).toLong()

    fun frame(dir: Dir, e: Long): Frame {
        val at = e.coerceIn(0L, total(dir)).toFloat()
        if (dir == Dir.Out) {
            val d = Tok.motion_variantFade
            return if (at < d) Frame(true, 1 - Curves.decel.at(at / d), 1f) else Frame(false, Curves.decel.at((at - d) / d), 1f)
        }
        val out = Tok.motion_nowPlayingFade
        if (at < out) return Frame(true, 1 - Curves.pop.at(at / out), 1f)
        val t = Curves.easeIn.at((at - out) / Tok.motion_nowPlayingIn)
        return Frame(false, t, lerp(Tok.motion_nowPlayingScale, 1f, t))
    }
}
