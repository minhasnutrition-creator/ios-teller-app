package android.print

open class PageRange(val start: Int = 0, val end: Int = 0) {
    companion object {
        val ALL_PAGES = PageRange(0, Int.MAX_VALUE)
    }
}

open class PrintAttributes {
    class MediaSize {
        companion object {
            val ISO_A4 = MediaSize()
        }
    }
}

class PrintDocumentInfo {
    companion object {
        const val CONTENT_TYPE_DOCUMENT = 0
        const val PAGE_COUNT_UNKNOWN = -1
    }
    class Builder(name: String) {
        fun setContentType(type: Int): Builder = this
        fun setPageCount(count: Int): Builder = this
        fun build(): PrintDocumentInfo = PrintDocumentInfo()
    }
}

abstract class PrintDocumentAdapter {
    open fun onLayout(
        oldAttributes: PrintAttributes,
        newAttributes: PrintAttributes,
        cancellationSignal: android.os.CancellationSignal,
        callback: LayoutResultCallback,
        extras: android.os.Bundle
    ) {}

    open fun onWrite(
        pages: Array<PageRange>,
        destination: android.os.ParcelFileDescriptor,
        cancellationSignal: android.os.CancellationSignal,
        callback: WriteResultCallback
    ) {}

    open class LayoutResultCallback {
        open fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {}
        open fun onLayoutFailed(error: CharSequence?) {}
        open fun onLayoutCancelled() {}
    }

    open class WriteResultCallback {
        open fun onWriteFinished(pages: Array<PageRange>) {}
        open fun onWriteFailed(error: CharSequence?) {}
        open fun onWriteCancelled() {}
    }
}

class PrintManager {
    fun print(printJobName: String, documentAdapter: PrintDocumentAdapter, attributes: PrintAttributes? = null): Any? = null
}
