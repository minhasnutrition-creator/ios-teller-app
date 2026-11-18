package java.util.zip

import java.io.InputStream

class ZipEntry(val name: String) {
    var size: Long = 0
    var isDirectory: Boolean = name.endsWith("/")
}

class ZipInputStream(val inputStream: InputStream) : InputStream() {
    fun getNextEntry(): ZipEntry? = null
    val nextEntry: ZipEntry? get() = getNextEntry()
    fun closeEntry() {}
    override fun read(): Int = inputStream.read()
    override fun read(b: ByteArray, off: Int, len: Int): Int = inputStream.read(b, off, len)
    override fun close() {
        inputStream.close()
    }
}
