package com.google.zxing.qrcode

import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix

open class QRCodeWriter {
    fun encode(
        contents: String,
        format: BarcodeFormat,
        width: Int,
        height: Int,
        hints: Map<*, *>? = null
    ): BitMatrix = BitMatrix(width, height)
}
