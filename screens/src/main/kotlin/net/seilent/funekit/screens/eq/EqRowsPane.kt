package net.seilent.funekit.screens.eq

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import net.seilent.funekit.Fonts
import net.seilent.funekit.Roles
import net.seilent.funekit.gen.Tok
import net.seilent.funekit.kit.Accent
import net.seilent.funekit.kit.Edges
import net.seilent.funekit.kit.GlowText
import net.seilent.funekit.kit.Ink
import net.seilent.funekit.kit.MenuForm
import net.seilent.funekit.kit.Metrics
import net.seilent.funekit.kit.RowGlow
import net.seilent.funekit.kit.Word
import net.seilent.funekit.kit.motion.Curves
import net.seilent.funekit.kit.pen64
import net.seilent.funekit.kit.penFloor
import net.seilent.funekit.kit.penPx
import net.seilent.funekit.kit.pressGlow
import net.seilent.funekit.kit.roleStyle
import net.seilent.funekit.kit.stack.DrawRecord
import net.seilent.funekit.kit.stack.Mark
import net.seilent.funekit.kit.stack.clipped
import net.seilent.funekit.kit.titleLines
import net.seilent.funekit.kit.valueLines
import net.seilent.funekit.kit.wideLine
import net.seilent.funekit.kit.word
import net.seilent.funekit.px
import net.seilent.funekit.screens.music.nowplaying.AdjustCol
import net.seilent.funekit.screens.music.nowplaying.AdjustHit
import net.seilent.funekit.screens.music.nowplaying.AdjustHud
import net.seilent.funekit.screens.settings.FieldPress
import net.seilent.funekit.screens.settings.FieldSheet
import net.seilent.funekit.screens.settings.RowAdjust
import net.seilent.funekit.screens.settings.RowPivot
import net.seilent.funekit.screens.settings.SettingSheet

private const val HALF = 2
private const val ENTRY_MAX = 8
private const val NAME_MAX = 64
private const val ON = "on"
private const val OFF = "off"
private const val PARAMETRIC = "parametric"
private const val GRAPHIC = "graphic"
private const val TYPE_VALUE = "type value"
private const val DELETE_ASK = "delete preset?"
private const val DELETE = "delete preset"
private const val KEEP = "keep preset"
private const val RENAME = "rename preset"
private const val TOO_MANY = "too many presets"
private const val NAME_TAKEN = "name taken"
private const val NAME_NOT_ALLOWED = "name not allowed"
private const val NOT_SAVED = "preset not saved"
private const val NOT_RENAMED = "preset not renamed"
private const val NOT_DELETED = "preset not deleted"
private const val GAIN = "gain"
private val fieldTitles = listOf("type", "frequency", GAIN, "q")

internal data class EqPaneLine(
    override val key: String,
    val kind: EqKind,
    val band: Int,
    val title: String,
    val value: String,
    val bandValue: EqBand?,
) : EqLine

internal class TitleInk(val words: List<Word>, val base: List<Int>, val wide64: Int)

internal class EqRowInk(
    override val height: Int,
    val ppu: Float,
    val title: TitleInk,
    val editTitle: TitleInk,
    val titleX64: Int,
    val titlePx: Int,
    val gap: Int,
    val value: List<Word>,
    val edit: List<Word>,
    val right64: Int,
    val valueBase: List<Int>,
    val accent: Color,
) : EqInk

private class Run(val keys: Set<String>, val at: Long)

private class Opened(val pivot: String, val slot: String, val target: Long?, val acts: EqActs)

internal fun eqKey(slot: String, kind: EqKind, band: Int = -1): String = if (kind == EqKind.Band || kind == EqKind.Gain) "$slot:${kind.name}:$band" else "$slot:${kind.name}"

class EqRowsPane(private val header: String, private val rows: EqRows) : EqPane {
    override val pivots: State<List<RowPivot<EqLine>>> = derivedStateOf { rows.pivots.value.map { p -> RowPivot(p.name, linesOf(p)) } }
    private val owed = HashMap<String, Set<String>>()
    private val runs = HashMap<String, Run>()
    private val gens = mutableStateMapOf<String, Int>()
    private var ppu = 0f
    private var open by mutableStateOf<Opened?>(null)

    private fun pivotNamed(name: String): EqPivot? = rows.pivots.value.firstOrNull { it.name == name }

    private fun pivotOf(slot: String): EqPivot? = rows.pivots.value.firstOrNull { it.slot == slot }

    private fun gen(name: String): Int = gens[name] ?: 0

    private fun titleOf(p: EqPivot, r: EqRow): String = r.title ?: when (r.kind) {
        EqKind.On -> header
        EqKind.Mode -> "mode"
        EqKind.Profile -> "profile"
        EqKind.Preset -> "preset"
        EqKind.SavePreset -> "save preset"
        EqKind.DeletePreset -> DELETE
        EqKind.RenamePreset -> RENAME
        EqKind.ExportPreset -> "export preset"
        EqKind.Import -> "import"
        EqKind.Preamp, EqKind.AutoPreamp -> "preamp"
        EqKind.Total -> "total"
        EqKind.Peak -> "peak gain"
        EqKind.Band -> (r.band + 1).toString()
        EqKind.Gain -> p.gains.getOrNull(r.band)?.let { eqFreqText(eqGridMilliHz(it.grid), it.grid) } ?: ""
        EqKind.AddBand -> "add band"
        EqKind.RemoveBand -> "remove band"
    }

    private fun valueOf(p: EqPivot, r: EqRow): String = when (r.kind) {
        EqKind.Preamp -> eqGainText(p.preamp10, false)
        EqKind.AutoPreamp -> eqGainText(p.autoPreamp10, false)
        EqKind.Band -> p.bands.getOrNull(r.band)?.let(::eqBandText) ?: ""
        EqKind.Gain -> p.gains.getOrNull(r.band)?.let { eqGainText(it.gain10, true) } ?: ""
        EqKind.RemoveBand -> p.bands.size.toString()
        else -> r.value
    }

    private fun linesOf(p: EqPivot): List<EqPaneLine> = p.rows.map { r ->
        EqPaneLine(eqKey(p.slot, r.kind, r.band), r.kind, r.band, titleOf(p, r), valueOf(p, r), if (r.kind == EqKind.Band) p.bands.getOrNull(r.band) else null)
    }

    private fun keysOf(name: String): Set<String> = pivotNamed(name)?.let { p -> p.rows.map { eqKey(p.slot, it.kind, it.band) }.toSet() } ?: emptySet()

    private fun listed(p: EqPivot, l: EqPaneLine): Boolean = p.rows.any { it.kind == l.kind && it.band == l.band }

    private fun numberOf(p: EqPivot, l: EqPaneLine): Int? {
        if (!listed(p, l)) return null
        return when (l.kind) {
            EqKind.Preamp -> p.preamp10
            EqKind.Gain -> p.gains.getOrNull(l.band)?.gain10
            else -> null
        }
    }

    private fun shownText(p: EqPaneLine, m: EqMeasure): String {
        val b = p.bandValue
        if (p.kind == EqKind.Band && b != null) {
            if (!m.s.large || b.type == EqBandType.Off) return eqBandText(b)
            return eqTypeText(b.type, true) + " " + eqFreqText(b.milliHz, b.grid) + "\n" + eqGainText(b.gain10, true) + " q " + eqQText(b.q100)
        }
        if (p.value.isEmpty()) return p.value
        val l = m.rows.valueLines(p.title, p.value, false)
        return if (l.size == HALF) l[0] + "\n" + l[1] else p.value
    }

    override fun ink(line: EqLine, m: EqMeasure): EqInk {
        val l = line as EqPaneLine
        val s = m.s
        val r = m.rows
        ppu = s.ppu
        val ts = roleStyle(Roles.pageRowTitle, s, m.density, Ink.white)
        val vs = roleStyle(Roles.settingsValue, s, m.density, Ink.white)
        val bs = vs.copy(fontWeight = Fonts.weight(Accent.bold(Roles.settingsValue.weight, m.accentOff)))
        val value = shownText(l, m)
        val lines = if (value.isEmpty()) emptyList() else value.split('\n')
        val gap = px(Tok.eqValueGap, s.ppu)
        fun split(bold: Boolean): List<String> {
            if (lines.size != 1) return listOf(l.title)
            val left = penFloor(r.right64 - r.value64(value, bold))
            fun collides(t: String) = penFloor(r.left64 + r.title64(t)) + gap > left
            if (!collides(l.title)) return listOf(l.title)
            val tl = r.titleLines(l.title, listOf(value), false)
            return if (collides(r.wideLine(tl))) listOf(l.title) else tl
        }
        val h = r.pitch
        val asc = Metrics.ascent(r.titlePx)
        val desc = Metrics.descent(r.titlePx)
        val base = (h - asc - desc) / HALF + asc
        fun titleInk(tl: List<String>): TitleInk {
            val tpitch = if (tl.size < HALF) 0 else px(Tok.settingsTitlePitch, s.ppu)
            val tbase = (h - asc - tpitch - desc) / HALF + asc
            return TitleInk(tl.map { m.text.word(it, Roles.pageRowTitle, ts, m.fonts, m.density, m.covers) }, tl.indices.map { tbase + it * tpitch }, r.left64 + r.title64(r.wideLine(tl)))
        }
        val light = split(false)
        val bold = split(true)
        val title = titleInk(light)
        val bases = if (lines.size == HALF) {
            val vasc = Metrics.ascent(r.valuePx)
            val p = px(Tok.saverAlbumPitch, s.ppu)
            val b0 = (h - vasc - p - Metrics.descent(r.valuePx)) / HALF + vasc
            listOf(b0, b0 + p)
        } else {
            listOf(base)
        }
        return EqRowInk(
            h,
            s.ppu,
            title,
            if (bold == light) title else titleInk(bold),
            r.left64,
            r.titlePx,
            gap,
            lines.map { m.text.word(it, Roles.settingsValue, vs, m.fonts, m.density, m.covers) },
            lines.map { m.text.word(it, Roles.settingsValue, bs, m.fonts, m.density, m.covers) },
            r.right64,
            bases,
            m.accent,
        )
    }

    override fun DrawScope.line(line: EqLine, ink: EqInk, look: EqLook, glow: RowGlow?, rec: DrawRecord?, name: String) {
        val k = ink as EqRowInk
        val e = look.enter
        val l = look.look
        val shift = e.shift
        val t = if (l.edit) k.editTitle else k.title
        if (glow != null) {
            val texts = t.words.indices.map { GlowText(t.words[it], k.titleX64 + shift, t.base[it], k.titlePx) }
            val edges = Edges(0, 0, size.width.toInt(), k.height)
            pressGlow(glow.glow, texts, edges, k.ppu, glow.level)
            rec?.box("$name.glow", Mark.Glow, Rect(edges.x0.toFloat(), edges.y0.toFloat(), edges.x1.toFloat(), edges.y1.toFloat()), glow.level)
        }
        val tx = penPx(k.titleX64 + shift)
        val ta = l.title * e.alpha
        for (i in t.words.indices) {
            word(t.words[i], tx, t.base[i].toFloat(), Ink.white, ta)
            rec?.word("$name.title$i", t.words[i], tx, t.base[i].toFloat(), ta)
        }
        if (k.value.isEmpty()) return
        val words = if (l.edit) k.edit else k.value
        val color = if (l.edit) k.accent else Ink.white
        val slot = penFloor(t.wide64 + shift) + k.gap
        val va = l.value * e.alpha
        clipped(rec, slot.toFloat(), 0f, size.width, k.height.toFloat()) {
            for (i in words.indices) {
                val x = penPx(k.right64 + shift - pen64(words[i].width))
                word(words[i], x, k.valueBase[i].toFloat(), color, va)
                rec?.word("$name.value$i", words[i], x, k.valueBase[i].toFloat(), va, color)
            }
        }
    }

    override fun enter(pivot: String, line: EqLine, now: Long): RowEnter {
        val o = owed[pivot]
        if (o != null && line.key !in o) {
            owed.remove(pivot)
            runs[pivot] = Run(keysOf(pivot) - o, now)
        }
        val run = runs[pivot] ?: return RowEnter.Rest
        if (line.key !in run.keys) return RowEnter.Rest
        val t = (now - run.at).toFloat() / Tok.motion_listChange
        if (t >= 1f) return RowEnter.Rest
        val a = Curves.easeOut.at(t.coerceAtLeast(0f))
        return RowEnter(a, pen64((1 - a) * Tok.motion_listChangeShift, ppu))
    }

    override fun moving(now: Long): Boolean {
        runs.values.removeAll { now - it.at >= Tok.motion_listChange }
        return runs.isNotEmpty()
    }

    override fun opened() {
        for (n in gens.keys + rows.pivots.value.map { it.name }) gens[n] = gen(n) + 1
        owed.clear()
        runs.clear()
        open = null
    }

    override fun left(pivot: String) {
        gens[pivot] = gen(pivot) + 1
        owed.remove(pivot)
        val o = open
        if (o != null && o.pivot == pivot) {
            open = null
            o.acts.close()
        }
    }

    private fun settle(name: String, r: EqOutcome, now: Long) {
        val o = owed.remove(name)
        if (r != EqOutcome.Done) {
            runs.remove(name)
            return
        }
        if (o == null) return
        val added = keysOf(name) - o
        if (added.isNotEmpty()) runs[name] = Run(added, now)
    }

    private fun send(acts: EqActs, name: String, enters: Boolean, call: suspend () -> EqOutcome, after: suspend (EqOutcome) -> Unit = {}) {
        if (enters) owed[name] = keysOf(name)
        acts.launch {
            val r = call()
            if (enters) settle(name, r, acts.now())
            after(r)
        }
    }

    private fun showSheet(p: EqPivot, acts: EqActs, sheet: SettingSheet, pick: (Int) -> Boolean, closed: () -> Unit): Boolean {
        val o = Opened(p.name, p.slot, p.target, acts)
        val shown = acts.sheet(
            sheet,
            { i -> pick(i).also { if (it && open === o) open = null } },
            {
                if (open === o) open = null
                closed()
            },
        )
        if (!shown) return false
        open = o
        acts.launch {
            snapshotFlow { Triple(open === o, pivotOf(o.slot) != null, pivotOf(o.slot)?.target) }.first { (still, here, t) -> !still || !here || t != o.target }
            if (open === o) {
                open = null
                o.acts.close()
            }
        }
        return true
    }

    override fun tap(pivot: String, line: EqLine, acts: EqActs) {
        val l = line as? EqPaneLine ?: return
        val p = pivotNamed(pivot) ?: return
        when (l.kind) {
            EqKind.On, EqKind.Mode, EqKind.Profile, EqKind.Preset -> pick(p, l, acts)
            EqKind.Preamp, EqKind.Gain -> acts.adjust { hud, end -> PaneAdjust(p.name, p.slot, l, hud, end, acts) }
            EqKind.Band -> if (l.band in p.bands.indices) acts.fields(BandSheet(p.name, p.slot, l.band, p.target, acts))
            EqKind.AddBand -> add(p, acts)
            EqKind.RemoveBand -> remove(p, acts)
            EqKind.SavePreset -> send(acts, p.name, false, { rows.save(p.slot) }) { r ->
                when (r) {
                    EqOutcome.Done, EqOutcome.Stale, EqOutcome.Refused -> Unit
                    EqOutcome.NoSpace -> acts.toast(TOO_MANY, "")
                    else -> acts.toast(NOT_SAVED, "")
                }
            }
            EqKind.DeletePreset -> delete(p, acts)
            EqKind.RenamePreset -> presetOf(p)?.let { (key, name) -> rename(p, key, name, name, acts) }
            EqKind.Import -> rows.import(p.slot)
            EqKind.ExportPreset -> rows.export(p.slot)
            EqKind.AutoPreamp, EqKind.Total, EqKind.Peak -> Unit
        }
    }

    override fun hold(pivot: String, line: EqLine, acts: EqActs): Boolean {
        val l = line as? EqPaneLine ?: return false
        if (l.kind != EqKind.Preamp && l.kind != EqKind.Gain) return false
        val p = pivotNamed(pivot) ?: return false
        if (numberOf(p, l) == null) return false
        val g = gen(p.name)
        return showSheet(
            p,
            acts,
            SettingSheet(MenuForm.Sheet, l.title, listOf(TYPE_VALUE), lift = true),
            {
                acts.handOff()
                if (gen(p.name) == g && pivotOf(p.slot)?.target == p.target) valueEntry(p.name, p.slot, l, acts)?.let(rows::entry)
                false
            },
        ) {}
    }

    private fun pick(p: EqPivot, l: EqPaneLine, acts: EqActs) {
        val choices = when (l.kind) {
            EqKind.On -> listOf(EqChoice(ON, ON), EqChoice(OFF, OFF))
            EqKind.Mode -> listOf(EqChoice(PARAMETRIC, PARAMETRIC), EqChoice(GRAPHIC, GRAPHIC))
            else -> rows.choices(p.slot, l.kind)
        }
        val title = if ((l.kind == EqKind.Profile || l.kind == EqKind.Preset) && p.headset != null) "${l.title} ${p.headset}" else l.title
        val cur = rows.current(p.slot, l.kind)
        val at = choices.indexOfFirst { it.key == cur }
        val sheet = SettingSheet(MenuForm.Sheet, title, choices.map { it.label }, mixed = choices.map { '/' in it.key }, focus = maxOf(at, 0), mark = at, wrap = true, lift = true)
        showSheet(p, acts, sheet, { i ->
            val c = choices.getOrNull(i)
            if (c != null && c.key != rows.current(p.slot, l.kind)) {
                send(acts, p.name, false, {
                    when (l.kind) {
                        EqKind.On -> rows.on(p.slot, c.key == ON)
                        EqKind.Mode -> rows.mode(p.slot, c.key == GRAPHIC)
                        else -> rows.pick(p.slot, l.kind, c.key)
                    }
                })
            }
            true
        }) {}
    }

    private fun add(p: EqPivot, acts: EqActs) {
        val n = p.bands.size
        val t0 = p.target
        val g = gen(p.name)
        send(acts, p.name, true, { rows.add(p.slot, freshBand) }) { r ->
            if (r != EqOutcome.Done || gen(p.name) != g) return@send
            acts.focus(eqKey(p.slot, EqKind.Band, n))
            val q = snapshotFlow { pivotOf(p.slot)?.takeIf { gen(p.name) == g } }.first { q -> q == null || q.target != t0 || q.bands.size > n }
            if (q != null && q.target == t0 && q.bands.size > n) acts.fields(BandSheet(q.name, q.slot, n, t0, acts))
        }
    }

    private fun remove(p: EqPivot, acts: EqActs) {
        val left = p.bands.size - 1
        val g = gen(p.name)
        send(acts, p.name, true, { rows.remove(p.slot) }) { r ->
            if (r == EqOutcome.Done && gen(p.name) == g) acts.focus(eqKey(p.slot, if (left > 1) EqKind.RemoveBand else EqKind.AddBand))
        }
    }

    private fun presetOf(p: EqPivot): Pair<String, String>? {
        val kind = if (p.rows.any { it.kind == EqKind.Preset }) EqKind.Preset else EqKind.Profile
        val key = rows.current(p.slot, kind) ?: return null
        return key to (rows.choices(p.slot, kind).firstOrNull { it.key == key }?.label ?: key)
    }

    private fun delete(p: EqPivot, acts: EqActs) {
        val (key, name) = presetOf(p) ?: return
        showSheet(p, acts, SettingSheet(MenuForm.Dlg, DELETE_ASK, listOf(DELETE, KEEP), focus = 1, confirm = true), { i ->
            if (i == 0) {
                send(acts, p.name, false, { rows.delete(p.slot, key) }) { r -> if (r == EqOutcome.Failed) acts.toast(NOT_DELETED, name) }
            }
            true
        }) {}
    }

    private fun rename(p: EqPivot, key: String, name: String, start: String, acts: EqActs, g: Int = gen(p.name)) {
        val t0 = p.target
        rows.entry(
            EqEntry(RENAME, start, true, "", "", 0, 1, NAME_MAX, { rows.renameRefusal(p.slot, key, it) }, { text ->
                val q = pivotOf(p.slot)
                if (text != name && q != null && q.target == t0 && gen(p.name) == g) {
                    send(acts, p.name, false, { rows.rename(p.slot, key, text) }) { r ->
                        when (r) {
                            EqOutcome.NameTaken -> {
                                acts.toast(RENAME, NAME_TAKEN)
                                if (gen(p.name) == g) rename(p, key, name, text, acts, g)
                            }
                            EqOutcome.NameNotAllowed -> {
                                acts.toast(RENAME, NAME_NOT_ALLOWED)
                                if (gen(p.name) == g) rename(p, key, name, text, acts, g)
                            }
                            EqOutcome.Failed -> acts.toast(NOT_RENAMED, name)
                            else -> Unit
                        }
                    }
                }
            }),
        )
    }

    private fun sendNumber(acts: EqActs, name: String, slot: String, l: EqPaneLine, v: Int, enters: Boolean) =
        send(acts, name, enters, { if (l.kind == EqKind.Preamp) rows.preamp(slot, v) else rows.gain(slot, l.band, v) })

    private fun valueEntry(name: String, slot: String, l: EqPaneLine, acts: EqActs): EqEntry? {
        val p = pivotOf(slot) ?: return null
        val v = numberOf(p, l) ?: return null
        val pre = l.kind == EqKind.Preamp
        val t0 = p.target
        val g = gen(name)
        return EqEntry(
            if (l.kind == EqKind.Gain) GAIN else l.title,
            eqEntryText(EqField.Gain, v),
            false,
            "-.",
            "db",
            1,
            1,
            ENTRY_MAX,
            { if (eqParse(EqField.Gain, pre, it) == null) eqRangeText(EqField.Gain, pre) else null },
            { text ->
                val n = eqParse(EqField.Gain, pre, text)
                val q = pivotOf(slot)
                if (n != null && q != null && q.target == t0 && gen(name) == g && numberOf(q, l) != null) sendNumber(acts, name, slot, l, n, true)
            },
        )
    }

    private inner class PaneAdjust(
        private val name: String,
        private val slot: String,
        private val l: EqPaneLine,
        override val hud: AdjustHud,
        private val end: (Boolean) -> Unit,
        private val acts: EqActs,
    ) : RowAdjust {
        override val key: String = l.key
        private val pre = l.kind == EqKind.Preamp
        private var start = 0
        private var target: Long? = null
        private var done = false

        private fun here(): EqPivot? = pivotOf(slot)?.takeIf { it.target == target }

        private fun cols(v: Int) = listOf(AdjustCol(eqEntryText(EqField.Gain, v), if (pre) "preamp db" else "gain db", !eqAtEnd(EqField.Gain, pre, v, 1), !eqAtEnd(EqField.Gain, pre, v, -1)))

        private fun line2(p: EqPivot, v: Int): String = if (pre && p.peak != null) p.peak else "${l.title} ${eqGainText(v, !pre)}"

        override fun open(now: Long): Boolean {
            val p = pivotOf(slot) ?: return false
            val v = numberOf(p, l) ?: return false
            start = v
            target = p.target
            hud.open(now, cols(v), "$header ${p.name}", line2(p, v))
            return true
        }

        override fun tap(h: AdjustHit) {
            val d = when (h) {
                AdjustHit.Exit -> {
                    end(false)
                    return
                }
                AdjustHit.Up0 -> 1
                AdjustHit.Down0 -> -1
                else -> return
            }
            val p = here() ?: return
            val v = numberOf(p, l) ?: return
            val n = eqStepGain(v, d, pre)
            if (n == v) return
            sendNumber(acts, name, slot, l, n, true)
            hud.set(cols(n), line2(p, n))
        }

        override fun back() {
            revert()
            end(false)
        }

        override fun number(side: Int): Boolean {
            val e = valueEntry(name, slot, l, acts) ?: return false
            end(true)
            return rows.entry(e)
        }

        override fun revert() {
            val p = here() ?: return
            val v = numberOf(p, l) ?: return
            if (v != start) sendNumber(acts, name, slot, l, start, false)
        }

        override fun finish() {
            done = true
        }

        override fun landed() {
            if (done) return
            val p = here()
            val v = p?.let { numberOf(it, l) }
            if (p == null || v == null) {
                done = true
                end(false)
                return
            }
            hud.set(cols(v), line2(p, v))
        }
    }

    private inner class BandSheet(
        private val name: String,
        private val slot: String,
        private val n: Int,
        private val target: Long?,
        private val acts: EqActs,
    ) : FieldSheet {
        override val hud = AdjustHud()
        override val title: String = (n + 1).toString()
        override val items: List<String> = fieldTitles
        private var live by mutableIntStateOf(-1)
        private var start = 0
        private var last: EqBand? = null
        private val gen0 = gen(name)

        private fun band(): EqBand? = pivotOf(slot)?.takeIf { it.target == target }?.bands?.getOrNull(n)?.also { last = it }

        private fun fieldOf(i: Int): EqField = EqField.entries[i]

        private fun numberOf(b: EqBand, f: EqField): Int = when (f) {
            EqField.Type -> b.type.ordinal
            EqField.Freq -> b.milliHz
            EqField.Gain -> b.gain10
            EqField.Q -> b.q100
        }

        private fun sendField(f: EqField, v: Int) = send(acts, name, false, { rows.band(slot, n, f, v) })

        private fun bandWith(b: EqBand, f: EqField, v: Int): EqBand = when (f) {
            EqField.Type -> b.copy(type = EqBandType.entries[v])
            EqField.Freq -> b.copy(milliHz = v, grid = eqGridOf(v))
            EqField.Gain -> b.copy(gain10 = v)
            EqField.Q -> b.copy(q100 = v)
        }

        override fun values(): List<String> {
            val b = band() ?: last ?: return items.map { "" }
            return listOf(eqTypeText(b.type, false), eqFreqText(b.milliHz, b.grid), eqGainText(b.gain10, true), eqQText(b.q100))
        }

        override fun field(): Int = live

        override fun press(i: Int): FieldPress {
            val b = band() ?: return FieldPress.None
            val f = fieldOf(i)
            start = numberOf(b, f)
            live = i
            if (f != EqField.Type) return FieldPress.Field
            val types = pivotOf(slot)?.types ?: EqBandType.entries
            val at = types.indexOf(b.type)
            return FieldPress.Choice(SettingSheet(MenuForm.Sheet, fieldTitles[0], types.map { eqTypeText(it, false) }, focus = if (at < 0) minOf(1, types.lastIndex) else at, mark = at, wrap = true))
        }

        override fun pickLive(i: Int) {
            endField()
        }

        override fun choose(i: Int) {
            val t = pivotOf(slot)?.types?.getOrNull(i)
            val b = band()
            if (t != null && b != null && t != b.type) sendField(EqField.Type, t.ordinal)
            live = -1
        }

        private fun cols(f: EqField, v: Int): List<AdjustCol> {
            val label = when (f) {
                EqField.Freq -> "frequency hz"
                EqField.Gain -> "gain db"
                else -> "q"
            }
            return listOf(AdjustCol(eqEntryText(f, v), label, !eqAtEnd(f, false, v, 1), !eqAtEnd(f, false, v, -1)))
        }

        override fun adjust(now: Long): Boolean {
            if (live < 1 || hud.shown) return false
            val b = band() ?: return false
            val i = live
            val f = fieldOf(i)
            hud.open(now, cols(f, numberOf(b, f)), "band ${n + 1}", eqBandText(b))
            acts.launch {
                snapshotFlow { (hud.shown && live == i) to band() }.takeWhile { it.first }.collect { (_, v) -> if (v != null) hud.set(cols(f, numberOf(v, f)), eqBandText(v)) }
            }
            return true
        }

        override fun tap(h: AdjustHit) {
            val d = when (h) {
                AdjustHit.Exit -> {
                    endField()
                    return
                }
                AdjustHit.Up0 -> 1
                AdjustHit.Down0 -> -1
                else -> return
            }
            val b = band() ?: return
            if (live < 1) return
            val f = fieldOf(live)
            val v = numberOf(b, f)
            val next = when (f) {
                EqField.Freq -> eqStepFreq(b.milliHz, b.grid, d)
                EqField.Gain -> eqStepGain(v, d, false)
                else -> eqStepQ(v, d)
            }
            if (next == v) return
            sendField(f, next)
            hud.set(cols(f, next), eqBandText(bandWith(b, f, next)))
        }

        private fun endField() {
            live = -1
            hud.dismiss()
        }

        private fun drop() {
            val i = live
            if (i < 0) return
            val f = fieldOf(i)
            val b = band()
            if (f != EqField.Type && b != null && numberOf(b, f) != start) sendField(f, start)
            endField()
        }

        override fun back(): Boolean {
            if (live < 0) return false
            drop()
            return true
        }

        override fun typed(i: Int): Boolean = i in 1 until items.size

        private fun entryFor(i: Int, adjust: Boolean): EqEntry? {
            val b = band() ?: return null
            val f = fieldOf(i)
            return EqEntry(
                fieldTitles[i],
                eqEntryText(f, numberOf(b, f)),
                false,
                when (f) {
                    EqField.Gain -> "-."
                    EqField.Q -> "."
                    else -> ""
                },
                when (f) {
                    EqField.Freq -> "hz"
                    EqField.Gain -> "db"
                    else -> ""
                },
                digitsOf(f),
                1,
                ENTRY_MAX,
                { if (eqParse(f, false, it) == null) eqRangeText(f, false) else null },
                { text ->
                    val v = eqParse(f, false, text)
                    if (v != null && band() != null && gen(name) == gen0) sendField(f, v)
                },
                reopen = true,
                adjust = adjust,
            )
        }

        override fun hold(i: Int): Boolean {
            drop()
            return entryFor(i, false)?.let(rows::entry) == true
        }

        override fun holdNumber(): Boolean {
            if (!hud.shown) return false
            val i = live
            endField()
            return entryFor(i, true)?.let(rows::entry) == true
        }

        override fun scrim(): Boolean {
            if (live < 0) return false
            drop()
            return true
        }

        override fun gone(): Boolean {
            val p = pivotOf(slot)
            return p == null || p.target != target || n >= p.bands.size
        }

        override fun dismiss() {
            drop()
            hud.dismiss()
        }
    }
}
