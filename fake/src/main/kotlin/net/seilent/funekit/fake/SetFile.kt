package net.seilent.funekit.fake

import java.io.File

object SetFile {
    private const val NAME = "fake-set"

    fun read(dir: File): FakeSet {
        val name = File(dir, NAME).takeIf { it.isFile }?.readText()?.trim()
        return FakeSet.entries.firstOrNull { it.name == name } ?: FakeSet.Main
    }

    fun write(dir: File, set: FakeSet) = File(dir, NAME).writeText(set.name)
}
