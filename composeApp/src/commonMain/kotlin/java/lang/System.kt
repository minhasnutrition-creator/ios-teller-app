package java.lang

object System {
    fun currentTimeMillis(): Long = com.minhas.teller.currentTimeMillis()
    fun nanoTime(): Long = com.minhas.teller.currentTimeMillis() * 1_000_000L
    fun getProperty(key: String): String? = null
    fun getProperty(key: String, def: String): String = def
    fun getenv(name: String): String? = null
    fun arraycopy(src: Any, srcPos: Int, dest: Any, destPos: Int, length: Int) {
        if (src is ByteArray && dest is ByteArray) {
            src.copyInto(dest, destPos, srcPos, srcPos + length)
        }
    }
}
