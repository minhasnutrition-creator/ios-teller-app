package java.io

open class File {
    val path: String
    val name: String
    val absolutePath: String get() = path

    constructor(pathname: String) {
        this.path = pathname
        this.name = pathname.substringAfterLast('/').substringAfterLast('\\')
    }

    constructor(parent: String?, child: String) {
        val p = parent ?: ""
        this.path = if (p.isEmpty()) child else if (p.endsWith('/') || p.endsWith('\\')) "$p$child" else "$p/$child"
        this.name = child.substringAfterLast('/').substringAfterLast('\\')
    }

    constructor(parent: File?, child: String) : this(parent?.path, child)

    val parentFile: File?
        get() {
            val idx = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
            return if (idx > 0) File(path.substring(0, idx)) else null
        }

    fun exists(): Boolean = false
    fun isFile(): Boolean = true
    fun isDirectory(): Boolean = false
    fun length(): Long = 0L
    fun createNewFile(): Boolean = true
    fun mkdirs(): Boolean = true
    fun mkdir(): Boolean = true
    fun delete(): Boolean = true
    fun deleteOnExit() {}
    fun listFiles(): Array<File>? = emptyArray()
    fun list(): Array<String>? = emptyArray()

    fun readBytes(): ByteArray = ByteArray(0)
    fun writeBytes(bytes: ByteArray) {}
    fun readText(charset: Any? = null): String = ""
    fun writeText(text: String, charset: Any? = null) {}

    override fun toString(): String = path
    override fun equals(other: Any?): Boolean = other is File && other.path == path
    override fun hashCode(): Int = path.hashCode()
}

abstract class InputStream {
    open fun read(): Int = -1
    open fun read(b: ByteArray): Int = read(b, 0, b.size)
    open fun read(b: ByteArray, off: Int, len: Int): Int = -1
    open fun skip(n: Long): Long = 0
    open fun available(): Int = 0
    open fun close() {}
}

abstract class OutputStream {
    open fun write(b: Int) {}
    open fun write(b: ByteArray) { write(b, 0, b.size) }
    open fun write(b: ByteArray, off: Int, len: Int) {}
    open fun flush() {}
    open fun close() {}
}

class ByteArrayInputStream(private val buf: ByteArray) : InputStream() {
    private var pos = 0

    override fun read(): Int {
        return if (pos < buf.size) buf[pos++].toInt() and 0xFF else -1
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (pos >= buf.size) return -1
        val available = buf.size - pos
        val count = minOf(len, available)
        buf.copyInto(b, off, pos, pos + count)
        pos += count
        return count
    }

    override fun available(): Int = buf.size - pos
}

class ByteArrayOutputStream(initialCapacity: Int = 32) : OutputStream() {
    private var buffer = ByteArray(initialCapacity)
    private var count = 0

    override fun write(b: Int) {
        ensureCapacity(count + 1)
        buffer[count++] = b.toByte()
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        ensureCapacity(count + len)
        b.copyInto(buffer, count, off, off + len)
        count += len
    }

    private fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > buffer.size) {
            val newCap = maxOf(buffer.size * 2, minCapacity)
            buffer = buffer.copyOf(newCap)
        }
    }

    fun toByteArray(): ByteArray = buffer.copyOf(count)
    fun size(): Int = count
    fun reset() { count = 0 }
    override fun toString(): String = buffer.decodeToString(0, count)
}

open class FileInputStream(val file: File) : InputStream()
open class FileOutputStream(val file: File, val append: Boolean = false) : OutputStream() {
    constructor(path: String, append: Boolean = false) : this(File(path), append)
}

class InputStreamReader(val inputStream: InputStream, val charset: Any? = "UTF-8") {
    fun close() { inputStream.close() }
}

class BufferedReader(val reader: Any?) {
    fun readLine(): String? = null
    fun readLines(): List<String> = emptyList()
    fun readText(): String = ""
    fun close() {}
}

class FileReader(val file: File) : InputStream()

class PrintWriter(val out: Any?) {
    fun print(s: Any?) {}
    fun println(s: Any? = "") {}
    fun flush() {}
    fun close() {}
}

class FileWriter(val file: File, val append: Boolean = false) {
    constructor(path: String, append: Boolean = false) : this(File(path), append)
    fun write(text: String) {}
    fun flush() {}
    fun close() {}
}

fun InputStream.readBytes(): ByteArray = ByteArray(0)
fun InputStream.bufferedReader(): BufferedReader = BufferedReader(this)

inline fun <T, R> T.use(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        when (this) {
            is InputStream -> this.close()
            is OutputStream -> this.close()
            is BufferedReader -> this.close()
            is InputStreamReader -> this.close()
            is FileWriter -> this.close()
            is PrintWriter -> this.close()
        }
    }
}
