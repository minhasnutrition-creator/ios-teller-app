package com.minhas.teller.utils

import com.lowagie.text.pdf.RGBColor

/**
 * Compatibility shim for iText's BaseColor → OpenPDF migration.
 * OpenPDF replaced iText's BaseColor with RGBColor; this class
 * provides the same constructor API and static constants.
 */
class BaseColor : RGBColor {

    companion object {
        @JvmField val WHITE = BaseColor(255, 255, 255)
        @JvmField val BLACK = BaseColor(0, 0, 0)
        @JvmField val RED = BaseColor(255, 0, 0)
        @JvmField val GREEN = BaseColor(0, 255, 0)
        @JvmField val BLUE = BaseColor(0, 0, 255)
        @JvmField val GRAY = BaseColor(128, 128, 128)
        @JvmField val LIGHT_GRAY = BaseColor(192, 192, 192)
        @JvmField val DARK_GRAY = BaseColor(64, 64, 64)
        @JvmField val YELLOW = BaseColor(255, 255, 0)
        @JvmField val CYAN = BaseColor(0, 255, 255)
        @JvmField val MAGENTA = BaseColor(255, 0, 255)
        @JvmField val ORANGE = BaseColor(255, 200, 0)
        @JvmField val PINK = BaseColor(255, 175, 175)
    }

    constructor(red: Int, green: Int, blue: Int) : super(red, green, blue)
    constructor(red: Int, green: Int, blue: Int, alpha: Int) : super(red, green, blue, alpha)

    // Single-int ARGB constructor — RGBColor doesn't have this, so unpack manually
    constructor(argb: Int) : super(
        (argb shr 16) and 0xFF,
        (argb shr 8) and 0xFF,
        argb and 0xFF,
        (argb ushr 24) and 0xFF
    )
}
