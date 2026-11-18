package java.nio

enum class ByteOrder {
    BIG_ENDIAN, LITTLE_ENDIAN
}

class ByteBuffer private constructor(private val capacity: Int) {
    private val buffer = ByteArray(capacity)
    private var position = 0
    private var order = ByteOrder.BIG_ENDIAN

    fun order(bo: ByteOrder): ByteBuffer {
        this.order = bo
        return this
    }

    fun get(): Byte = if (position < capacity) buffer[position++] else 0
    fun get(dst: ByteArray): ByteBuffer {
        val count = minOf(dst.size, capacity - position)
        buffer.copyInto(dst, 0, position, position + count)
        position += count
        return this
    }

    fun getInt(): Int = 0
    fun getLong(): Long = 0L
    fun getShort(): Short = 0

    companion object {
        fun allocate(capacity: Int): ByteBuffer = ByteBuffer(capacity)
        fun wrap(array: ByteArray): ByteBuffer = ByteBuffer(array.size)
    }
}
