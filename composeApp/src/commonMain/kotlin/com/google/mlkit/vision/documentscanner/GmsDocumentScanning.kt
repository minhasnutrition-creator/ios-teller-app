package com.google.mlkit.vision.documentscanner

import android.net.Uri
import com.google.android.gms.tasks.Task

class GmsDocumentScanningResult(val pages: List<Page> = emptyList()) {
    class Page(val imageUri: Uri = Uri.parse(""))

    companion object {
        fun fromActivityResultIntent(data: Any?): GmsDocumentScanningResult? = null
    }
}

class GmsDocumentScannerOptions {
    class Builder {
        fun setGalleryImportAllowed(allowed: Boolean): Builder = this
        fun setResultFormats(format: Int, vararg formats: Int): Builder = this
        fun setScannerMode(mode: Int): Builder = this
        fun setPageLimit(limit: Int): Builder = this
        fun build(): GmsDocumentScannerOptions = GmsDocumentScannerOptions()
    }

    companion object {
        const val RESULT_FORMAT_JPEG = 101
        const val RESULT_FORMAT_PDF = 102
        const val SCANNER_MODE_FULL = 1
        const val SCANNER_MODE_BASE = 2
    }
}

interface GmsDocumentScanner {
    fun getStartScanIntent(activity: Any?): Task<Any?> = Task()
}

object GmsDocumentScanning {
    fun getClient(options: GmsDocumentScannerOptions? = null): GmsDocumentScanner =
        object : GmsDocumentScanner {}
}
