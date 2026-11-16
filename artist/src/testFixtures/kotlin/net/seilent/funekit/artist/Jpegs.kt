package net.seilent.funekit.artist

object Jpegs {
    fun baseline(b: ByteArray, w: Int, h: Int): Boolean {
        if (b.size < 4 || b[0] != 0xFF.toByte() || b[1] != 0xD8.toByte()) return false
        var i = 2
        while (i + 4 <= b.size) {
            if (b[i] != 0xFF.toByte()) return false
            val m = b[i + 1].toInt() and 0xFF
            val len = ((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)
            if (m == 0xC0 || m == 0xC1) {
                if (i + 9 > b.size) return false
                val hh = ((b[i + 5].toInt() and 0xFF) shl 8) or (b[i + 6].toInt() and 0xFF)
                val ww = ((b[i + 7].toInt() and 0xFF) shl 8) or (b[i + 8].toInt() and 0xFF)
                return hh == h && ww == w
            }
            if (m in 0xC2..0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC) return false
            if (m == 0xDA) return false
            i += 2 + len
        }
        return false
    }
}
