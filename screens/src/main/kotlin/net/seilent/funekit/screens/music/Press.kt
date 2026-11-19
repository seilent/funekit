package net.seilent.funekit.screens.music

import net.seilent.funekit.kit.PressSlots

fun PressSlots.staleIn(lo: Int, hi: Int, cols: Int, now: Long): Boolean =
    (on.item >= 0 && on.item / cols in lo..hi && on.p.stale(now.toFloat())) || (back.item >= 0 && back.item / cols in lo..hi && back.p.stale(now.toFloat()))
