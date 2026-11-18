package java.text

import java.util.Calendar
import java.util.Date
import java.util.Locale

class SimpleDateFormat(private val pattern: String, val locale: Locale = Locale.getDefault()) {
    fun format(date: Date): String = format(date.time)

    fun format(timeMs: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
        val year = cal.get(Calendar.YEAR).toString()
        val month = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
        val day = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
        val hour24 = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
        val hour12 = (cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }).toString().padStart(2, '0')
        val min = cal.get(Calendar.MINUTE).toString().padStart(2, '0')
        val sec = cal.get(Calendar.SECOND).toString().padStart(2, '0')
        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"

        val monthNameShort = when (cal.get(Calendar.MONTH)) {
            0 -> "Jan"; 1 -> "Feb"; 2 -> "Mar"; 3 -> "Apr"; 4 -> "May"; 5 -> "Jun"
            6 -> "Jul"; 7 -> "Aug"; 8 -> "Sep"; 9 -> "Oct"; 10 -> "Nov"; else -> "Dec"
        }

        var result = pattern
        result = result.replace("yyyy", year)
        result = result.replace("yy", if (year.length >= 2) year.takeLast(2) else year)
        result = result.replace("MMMM", monthNameShort)
        result = result.replace("MMM", monthNameShort)
        result = result.replace("MM", month)
        result = result.replace("dd", day)
        result = result.replace("d", cal.get(Calendar.DAY_OF_MONTH).toString())
        result = result.replace("HH", hour24)
        result = result.replace("hh", hour12)
        result = result.replace("mm", min)
        result = result.replace("ss", sec)
        result = result.replace("a", amPm)
        return result
    }

    fun parse(source: String): Date? {
        val parts = source.trim().split(Regex("[/\\-.:\\s]+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return try {
            val cal = Calendar.getInstance()
            if (parts.size >= 3) {
                // Check if YYYY-MM-DD or DD-MM-YYYY
                if (parts[0].length == 4) {
                    val y = parts[0].toInt()
                    val m = parts[1].toInt() - 1
                    val d = parts[2].toInt()
                    cal.set(y, m, d, 0, 0, 0)
                } else if (parts[2].length == 4 || parts[2].length == 2) {
                    val d = parts[0].toInt()
                    val m = parts[1].toInt() - 1
                    val rawY = parts[2].toInt()
                    val y = if (rawY < 100) rawY + 2000 else rawY
                    cal.set(y, m, d, 0, 0, 0)
                }
            }
            cal.time
        } catch (e: Exception) {
            Date()
        }
    }
}

class DecimalFormatSymbols(val locale: Locale = Locale.US) {
    var groupingSeparator: Char = ','
    var decimalSeparator: Char = '.'
}

class DecimalFormat(private val pattern: String = "#,##0.00", val symbols: DecimalFormatSymbols = DecimalFormatSymbols()) {
    fun format(number: Double): String {
        val isNegative = number < 0
        val absVal = if (isNegative) -number else number
        val longPart = absVal.toLong()
        val fracPart = ((absVal - longPart) * 100.0 + 0.5).toLong()
        val fracStr = fracPart.toString().padStart(2, '0').take(2)
        val intStr = formatWithCommas(longPart)
        val formatted = if (pattern.contains(".")) "$intStr.${fracStr}" else intStr
        return if (isNegative) "-$formatted" else formatted
    }

    fun format(number: Long): String = format(number.toDouble())

    private fun formatWithCommas(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val sb = StringBuilder()
        var count = 0
        for (i in s.length - 1 downTo 0) {
            sb.append(s[i])
            count++
            if (count == 3 && i > 0) {
                sb.append(',')
            } else if (count > 3 && (count - 3) % 2 == 0 && i > 0) {
                sb.append(',')
            }
        }
        return sb.reverse().toString()
    }
}

object Normalizer {
    enum class Form { NFC, NFD, NFKC, NFKD }
    fun normalize(src: CharSequence, form: Form): String = src.toString()
}
