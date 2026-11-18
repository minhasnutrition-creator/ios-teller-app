package com.google.zxing.common

open class BitMatrix(val width: Int = 100, val height: Int = 100) {
    operator fun get(x: Int, y: Int): Boolean = false
}
