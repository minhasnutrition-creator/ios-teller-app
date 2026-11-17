package com.google.mlkit.vision.barcode.common

class Barcode(
    val format: Int = FORMAT_QR_CODE,
    val rawValue: String? = null
) {
    companion object {
        const val FORMAT_QR_CODE = 256
        const val FORMAT_EAN_13 = 32
        const val FORMAT_UPC_A = 512
    }
}
