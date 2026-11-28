package net.seilent.funekit.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.seilent.funekit.api.Eq
import net.seilent.funekit.api.EqResult
import net.seilent.funekit.api.engine.EQ_CUSTOM
import net.seilent.funekit.api.engine.EQ_OFF
import net.seilent.funekit.api.engine.EqAnswer
import net.seilent.funekit.api.engine.EqCurve
import net.seilent.funekit.api.engine.EqFilter
import net.seilent.funekit.api.engine.EqFilterType
import net.seilent.funekit.api.engine.EqPart
import net.seilent.funekit.api.engine.EqView

class FakeEq internal constructor(private val book: Book) : Eq {
    internal class Book(cap: Int?) {
        val cap = cap ?: PROFILES_MAX
        val lock = Mutex()
        val presets = sortedMapOf<String, EqCurve>(ORDER)
        val stages = mutableListOf<FakeEq>()
    }

    private val cap = book.cap
    private val lock = book.lock
    private val presets = book.presets
    private var on = true
    private var key = EQ_OFF
    private var custom = FRESH
    private var origin: String? = null
    private val _view = MutableStateFlow(EqView())

    override val view: StateFlow<EqView> = _view.asStateFlow()

    init {
        book.stages += this
    }

    private fun shown(): EqCurve? = if (key == EQ_CUSTOM) custom else presets[key]

    private fun publish() {
        _view.value = EqView(on, key, presets.keys.toList(), shown(), origin = origin?.takeIf { key == EQ_CUSTOM && it in presets })
    }

    private fun done(name: String? = null): EqResult {
        for (s in book.stages) s.publish()
        return EqResult(EqAnswer.Done, name)
    }

    private fun taken(name: String): Boolean {
        val l = lower(name)
        return l == EQ_OFF || l == FLAT || l == EQ_CUSTOM || presets.keys.any { lower(it) == l }
    }

    private fun free(name: (Int) -> String): String = generateSequence(1) { it + 1 }.map(name).first { !taken(it) }

    private fun unsaved(): Boolean = (custom.preamp10 != 0 || custom.filters.any { it.type != EqFilterType.Off && it.gain10 != 0 }) && custom !in presets.values

    private fun edit(change: (EqCurve) -> EqCurve?): EqResult {
        val was = shown() ?: return REFUSED
        val next = change(was) ?: return REFUSED
        if (next == was) return EqResult(EqAnswer.Done)
        var kept: String? = null
        if (key != EQ_CUSTOM) {
            if (unsaved()) {
                if (presets.size >= cap) return NO_SPACE
                kept = free { "$PRESET $it" }
                presets[kept] = custom
            }
            origin = key
        }
        key = EQ_CUSTOM
        custom = next
        return done(kept)
    }

    private fun clamped(f: EqFilter): EqFilter =
        EqFilter(f.type, f.milliHz.coerceIn(FREQ_MIN, FREQ_MAX), f.gain10.coerceIn(GAIN_MIN, GAIN_MAX), f.q100.coerceIn(Q_MIN, Q_MAX))

    private fun set(f: EqFilter, part: EqPart, value: Int): EqFilter = clamped(
        when (part) {
            EqPart.Type -> f.copy(type = EqFilterType.entries[value.coerceIn(0, EqFilterType.entries.lastIndex)])
            EqPart.Freq -> f.copy(milliHz = value)
            EqPart.Gain -> f.copy(gain10 = value)
            EqPart.Q -> f.copy(q100 = value)
        },
    )

    override suspend fun on(on: Boolean): EqResult = lock.withLock {
        this.on = on
        done()
    }

    override suspend fun pick(key: String): EqResult = lock.withLock {
        if (key != EQ_OFF && key != EQ_CUSTOM && key !in presets) return@withLock REFUSED
        this.key = key
        done()
    }

    override suspend fun preamp(preamp10: Int): EqResult = lock.withLock { edit { it.copy(preamp10 = preamp10.coerceIn(PREAMP_MIN, PREAMP_MAX)) } }

    override suspend fun filter(index: Int, part: EqPart, value: Int): EqResult = lock.withLock {
        edit { c -> c.filters.getOrNull(index)?.let { f -> c.copy(filters = c.filters.mapIndexed { i, g -> if (i == index) set(f, part, value) else g }) } }
    }

    override suspend fun add(filter: EqFilter): EqResult = lock.withLock {
        edit { c -> if (c.filters.size >= BANDS) null else c.copy(filters = c.filters + clamped(filter)) }
    }

    override suspend fun remove(): EqResult = lock.withLock { edit { c -> if (c.filters.size <= 1) null else c.copy(filters = c.filters.dropLast(1)) } }

    override suspend fun save(): EqResult = lock.withLock {
        if (key != EQ_CUSTOM) return@withLock REFUSED
        if (presets.size >= cap) return@withLock NO_SPACE
        val name = free { "$PRESET $it" }
        presets[name] = custom
        key = name
        done(name)
    }

    override suspend fun resetCustom(): EqResult = lock.withLock {
        if (key != EQ_CUSTOM) return@withLock REFUSED
        custom = FRESH
        origin = null
        done()
    }

    override suspend fun delete(key: String): EqResult = lock.withLock {
        if (presets.remove(key) == null) return@withLock REFUSED
        for (s in book.stages) {
            if (s.key == key) s.key = EQ_OFF
            if (s.origin == key) s.origin = null
        }
        done()
    }

    override suspend fun update(name: String): EqResult = lock.withLock {
        if (key != EQ_CUSTOM || origin != name || name !in presets) return@withLock REFUSED
        presets[name] = custom
        key = name
        done(name)
    }

    override suspend fun export(key: String): EqResult = lock.withLock {
        val c = presets[key] ?: return@withLock REFUSED
        EqResult(EqAnswer.Done, key.map { if (it in UNSAFE) '_' else it }.joinToString("") + PARAMETRIC + TXT, text = autoEq(c))
    }

    override suspend fun import(name: String, text: String): EqResult = lock.withLock {
        if (text.lineSequence().none { it.startsWith("Preamp:") || it.startsWith("Filter") }) return@withLock EqResult(EqAnswer.Refused, log = listOf(NO_FILTERS))
        val base = cut(name)
        val clash = presets.keys.firstOrNull { lower(it) == lower(base) }
        if (clash != null && presets[clash] == FRESH) {
            key = clash
            return@withLock done(clash)
        }
        if (presets.size >= cap) return@withLock NO_SPACE
        val n = free { if (it == 1) base else "$base $it" }
        presets[n] = FRESH
        key = n
        done(n)
    }

    private companion object {
        const val NO_FILTERS = "no valid filters"
        const val PRESET = "Preset"
        const val FLAT = "flat"
        const val TXT = ".txt"
        const val PARAMETRIC = " ParametricEQ"
        const val PROFILES_MAX = 200
        const val BANDS = 10
        const val PREAMP_MIN = -240
        const val PREAMP_MAX = 0
        const val GAIN_MIN = -240
        const val GAIN_MAX = 120
        const val Q_MIN = 10
        const val Q_MAX = 1000
        const val FREQ_MIN = 20_000
        const val FREQ_MAX = 20_000_000
        const val KHZ = 1_000_000
        const val FRESH_Q = 140
        val FRESH = EqCurve(0, listOf(EqFilter(EqFilterType.Peak, KHZ, 0, FRESH_Q)))
        const val UNSAFE = "\\:*?\"<>|"
        val REFUSED = EqResult(EqAnswer.Refused)
        val NO_SPACE = EqResult(EqAnswer.NoSpace)

        fun fixed(v: Int, digits: Int): String {
            var div = 1
            repeat(digits) { div *= 10 }
            val a = if (v < 0) -v else v
            return "${if (v < 0) "-" else ""}${a / div}.${(a % div).toString().padStart(digits, '0')}"
        }

        fun autoEq(c: EqCurve): String = buildString {
            append("Preamp: ${fixed(c.preamp10, 1)} dB\n")
            c.filters.forEachIndexed { i, f ->
                append("Filter ${i + 1}: ")
                val kind = when (f.type) {
                    EqFilterType.Off -> null
                    EqFilterType.LowShelf -> "LSC"
                    EqFilterType.HighShelf -> "HSC"
                    EqFilterType.Peak -> "PK"
                }
                if (kind == null) append("OFF\n") else append("ON $kind Fc ${fixed(f.milliHz, 3)} Hz Gain ${fixed(f.gain10, 1)} dB Q ${fixed(f.q100, 2)}\n")
            }
        }

        fun digit(c: Char): Boolean = c in '0'..'9'

        fun fold(c: Char): Char = if (c in 'A'..'Z') c + ('a' - 'A') else c

        fun lower(s: String): String = buildString { for (c in s) append(fold(c)) }

        fun cut(name: String): String {
            val n = if (name.length > TXT.length && lower(name.takeLast(TXT.length)) == TXT) name.dropLast(TXT.length) else name
            return if (n.length > PARAMETRIC.length && n.endsWith(PARAMETRIC)) n.dropLast(PARAMETRIC.length) else n
        }

        val NAMES = Comparator<String> { a, b ->
            var i = 0
            var j = 0
            while (i < a.length && j < b.length) {
                if (digit(a[i]) && digit(b[j])) {
                    var ie = i
                    var je = j
                    while (ie < a.length && digit(a[ie])) ie++
                    while (je < b.length && digit(b[je])) je++
                    while (i + 1 < ie && a[i] == '0') i++
                    while (j + 1 < je && b[j] == '0') j++
                    if (ie - i != je - j) return@Comparator (ie - i).compareTo(je - j)
                    val c = a.substring(i, ie).compareTo(b.substring(j, je))
                    if (c != 0) return@Comparator c
                    i = ie
                    j = je
                    continue
                }
                val x = fold(a[i])
                val y = fold(b[j])
                if (x != y) return@Comparator x.compareTo(y)
                i++
                j++
            }
            if (a.length - i != b.length - j) return@Comparator (a.length - i).compareTo(b.length - j)
            a.compareTo(b)
        }

        val ORDER = Comparator<String> { a, b -> NAMES.compare(a + TXT, b + TXT) }
    }
}
