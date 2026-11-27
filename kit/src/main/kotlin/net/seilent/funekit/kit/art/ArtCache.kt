package net.seilent.funekit.kit.art

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.min

private const val PIX_BYTES = 4
private const val CRC_SHIFT = 32
private const val SIDE_SHIFT = 16

suspend fun Art.cover(ref: Any, px: Int, bytes: suspend () -> ByteArray?): Bitmap? {
    val key = ArtKey.Cover(ref, px)
    get(key)?.let { return it }
    return decodeArt(bytes() ?: return null, px)?.also { put(key, it) }
}

suspend fun largeArt(px: Int, config: Bitmap.Config, pic: (Long) -> Unit = {}, bytes: suspend () -> ByteArray?): Bitmap? {
    val b = decodeArt(bytes() ?: return null, px) ?: return null
    pic(picOf(b))
    return if (b.config == config) b else b.copy(config, false)
}

private fun picOf(b: Bitmap): Long {
    val w = b.width
    val h = b.height
    val row = IntArray(w)
    val buf = ByteBuffer.allocate(w * PIX_BYTES).order(ByteOrder.LITTLE_ENDIAN)
    val ints = buf.asIntBuffer()
    val crc = CRC32()
    for (y in 0 until h) {
        b.getPixels(row, 0, w, 0, y, w, 1)
        ints.clear()
        ints.put(row)
        crc.update(buf.array())
    }
    return (crc.value shl CRC_SHIFT) or (w.toLong() shl SIDE_SHIFT) or h.toLong()
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
    val out = squareArt(src, px)
    if (src !== out) src.recycle()
    return out
}

fun squareArt(src: Bitmap, px: Int): Bitmap {
    val c = min(src.width, src.height)
    val crop = Bitmap.createBitmap(src, (src.width - c) shr 1, (src.height - c) shr 1, c, c)
    val out = Bitmap.createScaledBitmap(crop, px, px, true)
    if (crop !== src && crop !== out) crop.recycle()
    return out
}
