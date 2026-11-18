package android.os

class ParcelFileDescriptor : AutoCloseable {
    override fun close() {}

    open class AutoCloseOutputStream(val pfd: ParcelFileDescriptor) : java.io.OutputStream()

    companion object {
        const val MODE_READ_ONLY = 268435456
        fun open(file: Any?, mode: Int): ParcelFileDescriptor = ParcelFileDescriptor()
    }
}
