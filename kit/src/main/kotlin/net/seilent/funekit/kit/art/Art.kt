package net.seilent.funekit.kit.art

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache

sealed interface ArtKey {
    data class Cover(val ref: Any, val px: Int) : ArtKey
    data class Icon(val key: String) : ArtKey
}

class Art(maxBytes: Int) {
    private val cache = object : LruCache<ArtKey, Bitmap>(maxBytes) {
        override fun sizeOf(key: ArtKey, value: Bitmap) = value.byteCount
    }

    operator fun get(key: ArtKey): Bitmap? = cache.get(key)

    fun put(key: ArtKey, b: Bitmap) {
        cache.put(key, b)
    }

    fun remove(key: ArtKey) {
        cache.remove(key)
    }

    fun keys(): Set<ArtKey> = cache.snapshot().keys

    companion object {
        private const val MB = 1048576
        private const val SHARE = 16
        private const val LOW_RAM_SHARE = 32
        private var art: Art? = null

        fun budget(memoryClassMb: Int, lowRam: Boolean): Int = memoryClassMb * MB / (if (lowRam) LOW_RAM_SHARE else SHARE)

        @Synchronized
        fun get(context: Context): Art = art ?: context.getSystemService(ActivityManager::class.java).let { Art(budget(it.memoryClass, it.isLowRamDevice)) }.also { art = it }
    }
}
