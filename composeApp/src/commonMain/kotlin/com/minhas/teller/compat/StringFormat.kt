package com.minhas.teller.compat

import kotlin.math.round

fun simpleFormat(format: String, args: Array<out Any?>): String {
    val sb = StringBuilder()
    var argIndex = 0
    var i = 0
    while (i < format.length) {
        val c = format[i]
        if (c == '%' && i + 1 < format.length) {
            val next = format[i + 1]
            if (next == '%') {
                sb.append('%')
                i += 2
                continue
            }
            var j = i + 1
            var padZero = false
            var width = 0
            var precision = -1
            if (j < format.length && format[j] == '0') {
                padZero = true
                j++
            }
            while (j < format.length && format[j].isDigit()) {
                width = width * 10 + (format[j] - '0')
                j++
            }
            if (j < format.length && format[j] == '.') {
                j++
                precision = 0
                while (j < format.length && format[j].isDigit()) {
                    precision = precision * 10 + (format[j] - '0')
                    j++
                }
            }
            if (j < format.length) {
                val type = format[j]
                val arg = if (argIndex < args.size) args[argIndex++] else null
                when (type) {
                    's', 'S' -> {
                        val str = arg?.toString() ?: "null"
                        sb.append(str)
                    }
                    'd' -> {
                        val num = when (arg) {
                            is Number -> arg.toLong()
                            else -> arg?.toString()?.toLongOrNull() ?: 0L
                        }
                        var str = num.toString()
                        if (padZero && width > str.length) {
                            str = "0".repeat(width - str.length) + str
                        }
                        sb.append(str)
                    }
                    'f' -> {
                        val d = when (arg) {
                            is Number -> arg.toDouble()
                            else -> arg?.toString()?.toDoubleOrNull() ?: 0.0
                        }
                        val prec = if (precision >= 0) precision else 6
                        sb.append(formatDouble(d, prec))
                    }
                    'x', 'X' -> {
                        val num = when (arg) {
                            is Number -> arg.toLong()
                            else -> arg?.toString()?.toLongOrNull() ?: 0L
                        }
                        var str = num.toString(16)
                        if (type == 'X') str = str.uppercase()
                        if (padZero && width > str.length) {
                            str = "0".repeat(width - str.length) + str
                        }
                        sb.append(str)
                    }
                    else -> {
                        sb.append(arg?.toString() ?: "")
                    }
                }
                i = j + 1
                continue
            }
        }
        sb.append(c)
        i++
    }
    return sb.toString()
}

fun formatDouble(d: Double, precision: Int): String {
    if (d.isNaN()) return "NaN"
    if (d.isInfinite()) return if (d > 0) "Infinity" else "-Infinity"
    val isNeg = d < 0
    val absD = if (isNeg) -d else d
    val factor = when (precision) {
        0 -> 1L
        1 -> 10L
        2 -> 100L
        3 -> 1000L
        4 -> 10000L
        5 -> 100000L
        6 -> 1000000L
        else -> {
            var f = 1L
            for (k in 0 until precision) f *= 10
            f
        }
    }
    val rounded = round(absD * factor).toLong()
    val intPart = rounded / factor
    val fracPart = rounded % factor
    val sb = StringBuilder()
    if (isNeg && (intPart > 0 || fracPart > 0)) sb.append('-')
    sb.append(intPart)
    if (precision > 0) {
        sb.append('.')
        var fracStr = fracPart.toString()
        while (fracStr.length < precision) {
            fracStr = "0$fracStr"
        }
        sb.append(fracStr)
    }
    return sb.toString()
}

fun String(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset, charset: Any? = null): String {
    val end = minOf(bytes.size, offset + length)
    return bytes.decodeToString(offset, end, throwOnInvalidSequence = false)
}

fun String(bytes: ByteArray, charset: Any?): String {
    return bytes.decodeToString(throwOnInvalidSequence = false)
}
