package com.google.zxing

enum class BarcodeFormat {
    QR_CODE,
    CODE_128,
    CODE_39,
    EAN_13,
    EAN_8,
    UPC_A,
    UPC_E,
    ITF,
    PDF_417,
    AZTEC
}

enum class EncodeHintType {
    CHARACTER_SET,
    ERROR_CORRECTION,
    MARGIN
}
