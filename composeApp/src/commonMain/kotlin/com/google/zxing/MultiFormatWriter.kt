package com.google.zxing

import com.google.zxing.common.BitMatrix

open class MultiFormatWriter {
    fun encode(
        contents: String,
        format: BarcodeFormat,
        width: Int,
        height: Int,
        hints: Map<*, *>? = null
    ): BitMatrix = BitMatrix(width, height)
}
