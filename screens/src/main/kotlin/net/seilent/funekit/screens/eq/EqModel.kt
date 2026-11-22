package net.seilent.funekit.screens.eq

import net.seilent.funekit.screens.settings.AimKind
import net.seilent.funekit.screens.settings.RowPivot
import net.seilent.funekit.screens.settings.RowsPage
import net.seilent.funekit.screens.settings.ensureVisible

class EqModel(entry: Long, page: EqId) : RowsPage<EqLine>(entry, page) {
    val id: EqId = page
    internal var left: (String) -> Unit = {}
    internal var moving: (Long) -> Boolean = { false }
    private var owed: String? = null
    private var want: String? = null

    override fun keyOf(r: EqLine): String = r.key

    override fun accept(v: List<RowPivot<EqLine>>) {
        val keys = v.flatMap { p -> p.rows.map { it.key } }
        require(keys.size == keys.toSet().size) { "an eq page lists each key once" }
        require(v.size == v.map { it.name }.toSet().size) { "an eq page names each pivot once" }
    }

    override fun moved(from: String, landing: Boolean) {
        stopAim()
        val pn = pane
        pn.px = 0
        pn.key = null
        setFocus(pn, rows(pivot), 0, 0L, false)
        want = null
        if (landing) owed = from else left(from)
    }

    override fun landed() {
        val k = want ?: return
        val i = rows(pivot).indexOfFirst { it.key == k }
        if (i < 0) return
        want = null
        focusAt(i)
    }

    fun focusKey(key: String) {
        val i = rows(pivot).indexOfFirst { it.key == key }
        if (i < 0) {
            want = key
            return
        }
        want = null
        focusAt(i)
    }

    private fun focusAt(i: Int) {
        setFocus(pane, rows(pivot), i, 0L, false)
        aimTo(ensureVisible(tops(pivot)[i], heights(pivot)[i], target(), view, limit(pivot)), AimKind.Into)
    }

    internal fun leave() {
        val from = owed ?: return
        owed = null
        left(from)
    }

    override fun animating(now: Long): Boolean = super.animating(now) || moving(now)

    override fun state(): List<Any> = super.state().take(1)
}
