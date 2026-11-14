package net.seilent.funekit.core

object SectionRows {
    val titles = listOf("The Beatles", "abba", "Zed", "5 Seconds", "あいみょん", "宇多田ヒカル")

    fun sorted(): List<String> = Section.sorted(titles) { it }.map { it.first }
}
