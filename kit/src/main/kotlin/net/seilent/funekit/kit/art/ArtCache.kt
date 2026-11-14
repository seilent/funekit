package net.seilent.funekit.kit.art

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import kotlin.math.min

suspend fun Art.cover(ref: Any, px: Int, bytes: suspend () -> ByteArray?): Bitmap? {
    val key = ArtKey.Cover(ref, px)
    get(key)?.let { return it }
    return decodeArt(bytes() ?: return null, px)?.also { put(key, it) }
}

suspend fun largeArt(px: Int, config: Bitmap.Config, bytes: suspend () -> ByteArray?): Bitmap? {
    val b = decodeArt(bytes() ?: return null, px) ?: return null
    return if (b.config == config) b else b.copy(config, false)
}

fun configFor(sdk: Int, large: Boolean): Bitmap.Config =
    if (large && sdk >= Build.VERSION_CODES.P) Bitmap.Config.HARDWARE else Bitmap.Config.ARGB_8888

fun sampleOf(w: Int, h: Int, px: Int): Int {
    val m = min(w, h)
    var sample = 1
    while (m / (sample shl 1) >= px) sample = sample shl 1
    return sample
}

fun decodeArt(bytes: ByteArray, px: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (min(bounds.outWidth, bounds.outHeight) <= 0 || px <= 0) return null
    val opts = BitmapFactory.Options().apply { inSampleSize = sampleOf(bounds.outWidth, bounds.outHeight, px) }
    val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
    val c = min(src.width, src.height)
    val crop = Bitmap.createBitmap(src, (src.width - c) shr 1, (src.height - c) shr 1, c, c)
    val out = Bitmap.createScaledBitmap(crop, px, px, true)
    if (crop !== src && crop !== out) crop.recycle()
    if (src !== out) src.recycle()
    return out
}
