package net.seilent.funekit.artist

class PushLedger {
    data class Held(val name: String, val mbid: String?, val device: String)

    data class Push(val name: String, val mbid: String?, val done: Set<String> = emptySet(), val origin: ImgOrigin? = null)

    data class Upgrade(val key: String, val name: String, val mbid: String?, val at: Long, val done: Set<String> = emptySet(), val origin: ImgOrigin? = null)

    private val pushes = ArrayList<Push>()
    private val held = ArrayList<Held>()
    private val upgrades = ArrayList<Upgrade>()
    private val upgradeDevices = LinkedHashSet<String>()

    @Synchronized
    internal fun blocked(r: ArtBlocks.Record) {
        pushes.removeAll { Names.fold(it.name) == Names.fold(r.name) }
        held.removeAll { Names.fold(it.name) == Names.fold(r.name) }
        upgrades.removeAll { it.key == r.key || Names.fold(it.name) == Names.fold(r.name) }
    }

    @Synchronized
    internal fun unblocked(r: ArtBlocks.Record) {
        pushes.removeAll { Names.fold(it.name) == Names.fold(r.name) }
        pushes += Push(r.name, if (r is ArtBlocks.Block) Names.mbid(r.key) else null)
    }

    @Synchronized
    internal fun dropped(key: String) {
        upgrades.removeAll { it.key == key }
    }

    @Synchronized
    fun upgraded(key: String, name: String, mbid: String?, at: Long, device: String?, origin: ImgOrigin? = null) {
        upgrades.removeAll { it.key == key }
        device?.let { upgradeDevices += it }
        upgrades += Upgrade(key, name, Names.mbid(mbid), at, setOfNotNull(device), origin)
    }

    @Synchronized
    fun upgrades(device: String): List<Upgrade> {
        upgradeDevices += device
        return upgrades.filter { device !in it.done }
    }

    @Synchronized
    fun upgradeDone(u: Upgrade, device: String) {
        upgradeDevices += device
        upgrades.replaceAll { if (it.key == u.key && it.at == u.at) it.copy(done = it.done + device) else it }
        upgrades.removeAll { it.done.containsAll(upgradeDevices) }
    }

    @Synchronized
    fun dropUpgrade(u: Upgrade) {
        upgrades.removeAll { it.key == u.key && it.at == u.at }
    }

    @Synchronized
    fun hold(name: String, mbid: String?, device: String) {
        held.removeAll { it.device == device && Names.fold(it.name) == Names.fold(name) }
        held += Held(name, Names.mbid(mbid), device)
    }

    @Synchronized
    fun held(device: String): List<Held> = held.filter { it.device == device }

    @Synchronized
    fun released(h: Held) {
        held.remove(h)
    }

    @Synchronized
    fun addPush(name: String, mbid: String?, origin: ImgOrigin? = null) {
        pushes.removeAll { Names.fold(it.name) == Names.fold(name) }
        pushes += Push(name, Names.mbid(mbid), origin = origin)
    }

    @Synchronized
    fun pushes(device: String): List<Push> = pushes.filter { device !in it.done }

    @Synchronized
    fun pushed(p: Push, device: String) {
        pushes.replaceAll { if (it.name == p.name && it.mbid == p.mbid) it.copy(done = it.done + device) else it }
    }

    @Synchronized
    internal fun lines(): List<List<String>> = buildList {
        for (h in held) add(listOf("held", h.name, h.mbid ?: "", h.device))
        for (d in upgradeDevices) add(listOf("upgradedevice", d))
        for (u in upgrades) add(listOf("upgrade", u.key, u.name, u.mbid ?: "", u.at.toString(), u.done.joinToString(","), u.origin?.word ?: ""))
        for (p in pushes) {
            add(listOf("push", p.name, p.mbid ?: "", p.done.joinToString(",")))
            p.origin?.let { add(listOf("origin", p.name, p.mbid ?: "", it.word)) }
        }
    }

    @Synchronized
    internal fun clear() {
        pushes.clear()
        held.clear()
        upgrades.clear()
        upgradeDevices.clear()
    }

    @Synchronized
    internal fun load(p: List<String>): Boolean = when (p[0]) {
        "held" -> p.size == 4 && held.add(Held(p[1], p[2].ifEmpty { null }, p[3]))
        "upgradedevice" -> p.size == 2 && upgradeDevices.add(p[1])
        "upgrade" -> p.size == 7 && p[4].toLongOrNull() != null && upgrades.none { it.key == p[1] } &&
            upgrades.add(Upgrade(p[1], p[2], p[3].ifEmpty { null }, p[4].toLong(), set(p[5]), ImgOrigin.of(p[6])))
        "push" -> p.size == 4 && pushes.add(Push(p[1], p[2].ifEmpty { null }, set(p[3])))
        "origin" -> p.size == 4 && origin(p[1], p[2].ifEmpty { null }, p[3])
        else -> false
    }

    private fun origin(name: String, mbid: String?, word: String): Boolean {
        val o = ImgOrigin.of(word) ?: return false
        val i = pushes.indexOfLast { it.name == name && it.mbid == mbid }
        if (i < 0) return false
        pushes[i] = pushes[i].copy(origin = o)
        return true
    }

    private fun set(s: String) = s.split(',').filter { it.isNotEmpty() }.toSet()

    internal companion object {
        val KINDS = setOf("held", "upgradedevice", "upgrade", "push", "origin")
    }
}
