package android.os

class ParcelFileDescriptor : AutoCloseable {
    override fun close() {}

    companion object {
        const val MODE_READ_ONLY = 268435456
        fun open(file: Any?, mode: Int): ParcelFileDescriptor = ParcelFileDescriptor()
    }
}
