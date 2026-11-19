package net.seilent.funekit.fake

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream

internal object Placeholder {
    const val SIDE = 8
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun png(seed: Long, side: Int = SIDE): ByteArray {
        val gray = (seed * 37 % 200 + 28).toInt()
        val raw = ByteArrayOutputStream()
        repeat(side) {
            raw.write(0)
            repeat(side) { raw.write(gray) }
        }
        val pixels = ByteArrayOutputStream().also { DeflaterOutputStream(it).use { z -> z.write(raw.toByteArray()) } }.toByteArray()
        val header = ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeInt(side)
                writeInt(side)
                writeByte(8)
                writeByte(0)
                writeByte(0)
                writeByte(0)
                writeByte(0)
            }
        }.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(SIGNATURE)
        chunk(out, "IHDR", header)
        chunk(out, "IDAT", pixels)
        chunk(out, "IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val name = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(name)
            update(data)
        }
        DataOutputStream(out).apply {
            writeInt(data.size)
            write(name)
            write(data)
            writeInt(crc.value.toInt())
        }
    }
}
