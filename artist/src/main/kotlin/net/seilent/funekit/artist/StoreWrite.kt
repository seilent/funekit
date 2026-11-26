package net.seilent.funekit.artist

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

open class StoreException(message: String, cause: Throwable? = null) : IOException(message, cause)

internal fun syncedWrite(f: File, text: String, dir: File, dirSync: (File) -> Unit) = syncedWrite(f, text.toByteArray(), dir, dirSync)

internal fun syncedWrite(f: File, bytes: ByteArray, dir: File, dirSync: (File) -> Unit) {
    val tmp = File(f.path + ".tmp")
    val out = step("write", tmp) { FileOutputStream(tmp) }
    try {
        step("write", tmp) {
            out.use { o ->
                o.write(bytes)
                step("sync", tmp) { o.fd.sync() }
            }
        }
        if (!tmp.renameTo(f)) throw StoreException("rename ${tmp.name}")
    } catch (e: StoreException) {
        tmp.delete()
        throw e
    }
    step("dir sync", dir) { dirSync(dir) }
}

private inline fun <T> step(name: String, f: File, block: () -> T): T = try {
    block()
} catch (e: StoreException) {
    throw e
} catch (e: IOException) {
    throw StoreException("$name ${f.name}", e)
}
