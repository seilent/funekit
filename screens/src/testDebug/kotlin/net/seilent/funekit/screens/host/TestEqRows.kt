package net.seilent.funekit.screens.host

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CompletableDeferred
import net.seilent.funekit.screens.eq.EqBand
import net.seilent.funekit.screens.eq.EqBandType
import net.seilent.funekit.screens.eq.EqChoice
import net.seilent.funekit.screens.eq.EqEntry
import net.seilent.funekit.screens.eq.EqField
import net.seilent.funekit.screens.eq.EqGain
import net.seilent.funekit.screens.eq.EqKind
import net.seilent.funekit.screens.eq.EqOutcome
import net.seilent.funekit.screens.eq.EqPivot
import net.seilent.funekit.screens.eq.EqRow
import net.seilent.funekit.screens.eq.EqRows
import net.seilent.funekit.screens.eq.eqGridMilliHz
import net.seilent.funekit.screens.eq.eqGridOf

const val EQ_BANDS = 10
const val KHZ_POINT = 34
const val FRESH_Q = 140

fun testBand(i: Int, gain10: Int = 0): EqBand = (KHZ_POINT + i).let { EqBand(EqBandType.Peak, eqGridMilliHz(it), it, gain10, FRESH_Q) }

fun parametricRows(n: Int): List<EqRow> = listOf(
    EqRow(EqKind.On, "on"),
    EqRow(EqKind.Mode, "parametric"),
    EqRow(EqKind.Profile, "custom"),
    EqRow(EqKind.SavePreset),
    EqRow(EqKind.DeletePreset),
    EqRow(EqKind.RenamePreset),
    EqRow(EqKind.Import),
    EqRow(EqKind.Preamp),
) +
    (0 until n).map { EqRow(EqKind.Band, band = it) } +
    listOfNotNull(EqRow(EqKind.AddBand).takeIf { n < EQ_BANDS }, EqRow(EqKind.RemoveBand).takeIf { n > 1 })

fun parametric(slot: String, name: String, bands: List<EqBand>, preamp10: Int = 0, target: Long? = null): EqPivot =
    EqPivot(slot, name, parametricRows(bands.size), preamp10 = preamp10, bands = bands, target = target)

fun graphic(slot: String, name: String, gains: List<Int>, target: Long? = null): EqPivot = EqPivot(
    slot,
    name,
    listOf(EqRow(EqKind.On, "on"), EqRow(EqKind.Mode, "graphic"), EqRow(EqKind.Preset, "flat"), EqRow(EqKind.AutoPreamp)) + gains.indices.map { EqRow(EqKind.Gain, band = it) },
    gains = gains.mapIndexed { i, g -> EqGain(KHZ_POINT - gains.size + i, g) },
    target = target,
)

fun withBands(p: EqPivot, bands: List<EqBand>): EqPivot = p.copy(bands = bands, rows = parametricRows(bands.size))

class TestEqRows(start: List<EqPivot>) : EqRows {
    private class Laid(val slot: String, val target: Long?, val lay: (EqPivot) -> EqPivot)

    private class Held(val reply: CompletableDeferred<EqOutcome>, var lands: Boolean = true)

    val mirror = mutableStateOf(start)
    private val laid = mutableStateListOf<Laid>()
    override val pivots: State<List<EqPivot>> = derivedStateOf {
        mirror.value.map { p -> laid.filter { it.slot == p.slot && it.target == p.target }.fold(p) { a, l -> l.lay(a) } }
    }
    val sends = mutableListOf<String>()
    val entries = mutableListOf<EqEntry>()
    var opens: (EqEntry) -> Boolean = { true }
    var answer = EqOutcome.Done
    var holds = false
    private val held = mutableListOf<Held>()
    var choiceOf: (String, EqKind) -> List<EqChoice> = { _, _ -> emptyList() }
    var currentOf: (String, EqKind) -> String? = { _, _ -> null }

    fun land(f: (List<EqPivot>) -> List<EqPivot>) {
        mirror.value = f(mirror.value)
    }

    fun reply(outcome: EqOutcome, lands: Boolean = true) {
        val h = held.removeAt(0)
        h.lands = lands
        h.reply.complete(outcome)
    }

    private suspend fun send(slot: String, line: String, lay: (EqPivot) -> EqPivot): EqOutcome {
        sends += line
        val l = Laid(slot, mirror.value.firstOrNull { it.slot == slot }?.target, lay)
        laid += l
        val h = if (holds) Held(CompletableDeferred<EqOutcome>()).also { held += it } else null
        val r = h?.reply?.await() ?: answer
        if (r == EqOutcome.Done && h?.lands != false) mirror.value = mirror.value.map { if (it.slot == slot && it.target == l.target) lay(it) else it }
        laid -= l
        return r
    }

    private fun rowValue(p: EqPivot, kind: EqKind, v: String) = p.copy(rows = p.rows.map { if (it.kind == kind) it.copy(value = v) else it })

    private fun field(b: EqBand, f: EqField, v: Int): EqBand = when (f) {
        EqField.Type -> b.copy(type = EqBandType.entries[v])
        EqField.Freq -> b.copy(milliHz = v, grid = eqGridOf(v))
        EqField.Gain -> b.copy(gain10 = v)
        EqField.Q -> b.copy(q100 = v)
    }

    override fun choices(slot: String, kind: EqKind): List<EqChoice> = choiceOf(slot, kind)

    override fun current(slot: String, kind: EqKind): String? {
        val p = pivots.value.firstOrNull { it.slot == slot } ?: return null
        return if (kind == EqKind.On || kind == EqKind.Mode) p.rows.firstOrNull { it.kind == kind }?.value else currentOf(slot, kind)
    }

    override fun entry(e: EqEntry): Boolean {
        entries += e
        return opens(e)
    }

    override suspend fun on(slot: String, on: Boolean): EqOutcome = send(slot, "on $slot $on") { rowValue(it, EqKind.On, if (on) "on" else "off") }

    override suspend fun mode(slot: String, graphic: Boolean): EqOutcome = send(slot, "mode $slot $graphic") { rowValue(it, EqKind.Mode, if (graphic) "graphic" else "parametric") }

    override suspend fun pick(slot: String, kind: EqKind, key: String): EqOutcome = send(slot, "pick $slot $kind $key") { rowValue(it, kind, key) }

    override suspend fun save(slot: String): EqOutcome = send(slot, "save $slot") { it }

    override suspend fun delete(slot: String, key: String): EqOutcome = send(slot, "delete $slot $key") { it }

    override suspend fun resetCustom(slot: String): EqOutcome = send(slot, "reset $slot") { it }

    override suspend fun rename(slot: String, key: String, name: String): EqOutcome = send(slot, "rename $slot $key $name") { it }

    override suspend fun preamp(slot: String, preamp10: Int): EqOutcome = send(slot, "preamp $slot $preamp10") { it.copy(preamp10 = preamp10) }

    override suspend fun band(slot: String, band: Int, field: EqField, value: Int): EqOutcome =
        send(slot, "band $slot $band $field $value") { p -> p.copy(bands = p.bands.mapIndexed { i, b -> if (i == band) field(b, field, value) else b }) }

    override suspend fun gain(slot: String, band: Int, gain10: Int): EqOutcome =
        send(slot, "gain $slot $band $gain10") { p -> p.copy(gains = p.gains.mapIndexed { i, g -> if (i == band) g.copy(gain10 = gain10) else g }) }

    override suspend fun add(slot: String, band: EqBand): EqOutcome = send(slot, "add $slot") { withBands(it, it.bands + band) }

    override suspend fun remove(slot: String): EqOutcome = send(slot, "remove $slot") { withBands(it, it.bands.dropLast(1)) }
}
