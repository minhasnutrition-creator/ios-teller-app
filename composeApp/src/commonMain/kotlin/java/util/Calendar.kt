package java.util

class Calendar(var timeInMillis: Long = com.minhas.teller.currentTimeMillis()) {
    fun clone(): Calendar = Calendar(timeInMillis)
    fun clear() { timeInMillis = 0L }
    fun clear(field: Int) {}
    var time: Date
        get() = Date(timeInMillis)
        set(value) {
            timeInMillis = value.time
        }

    val firstDayOfWeek: Int = SUNDAY

    fun get(field: Int): Int {
        // Simple Gregorian calendar math from epoch time
        val totalSeconds = timeInMillis / 1000L
        val totalDays = (totalSeconds / 86400L).toInt()
        val secOfDay = (totalSeconds % 86400L).toInt().let { if (it < 0) it + 86400 else it }

        val hour = secOfDay / 3600
        val minute = (secOfDay % 3600) / 60
        val second = secOfDay % 60
        val millis = (timeInMillis % 1000L).toInt().let { if (it < 0) it + 1000 else it }

        // Day of week: 1970-01-01 was Thursday (5 in 1=Sunday..7=Saturday)
        val dayOfWeek = ((totalDays + 4) % 7 + 7) % 7 + 1

        val (year, month, day, dayOfYear) = daysToDate(totalDays)

        return when (field) {
            YEAR -> year
            MONTH -> month // 0-based
            DAY_OF_MONTH, DATE -> day
            DAY_OF_YEAR -> dayOfYear
            DAY_OF_WEEK -> dayOfWeek
            HOUR_OF_DAY -> hour
            HOUR -> hour % 12
            MINUTE -> minute
            SECOND -> second
            MILLISECOND -> millis
            AM_PM -> if (hour < 12) AM else PM
            else -> 0
        }
    }

    fun set(field: Int, value: Int) {
        val y = if (field == YEAR) value else get(YEAR)
        val m = if (field == MONTH) value else get(MONTH)
        val d = if (field == DAY_OF_MONTH || field == DATE) value else get(DAY_OF_MONTH)
        val h = if (field == HOUR_OF_DAY) value else get(HOUR_OF_DAY)
        val min = if (field == MINUTE) value else get(MINUTE)
        val s = if (field == SECOND) value else get(SECOND)
        val ms = if (field == MILLISECOND) value else get(MILLISECOND)

        val days = dateToDays(y, m, d)
        val msInDay = (h * 3600L + min * 60L + s) * 1000L + ms
        timeInMillis = days * 86400_000L + msInDay
    }

    fun set(year: Int, month: Int, date: Int) {
        set(YEAR, year)
        set(MONTH, month)
        set(DAY_OF_MONTH, date)
    }

    fun set(year: Int, month: Int, date: Int, hourOfDay: Int, minute: Int) {
        set(year, month, date)
        set(HOUR_OF_DAY, hourOfDay)
        set(MINUTE, minute)
    }

    fun set(year: Int, month: Int, date: Int, hourOfDay: Int, minute: Int, second: Int) {
        set(year, month, date, hourOfDay, minute)
        set(SECOND, second)
    }

    fun add(field: Int, amount: Int) {
        when (field) {
            YEAR -> set(YEAR, get(YEAR) + amount)
            MONTH -> {
                val currentYear = get(YEAR)
                val currentMonth = get(MONTH)
                val newTotalMonths = currentYear * 12 + currentMonth + amount
                val newYear = newTotalMonths / 12
                val newMonth = newTotalMonths % 12
                set(YEAR, newYear)
                set(MONTH, if (newMonth < 0) newMonth + 12 else newMonth)
            }
            DAY_OF_MONTH, DATE, DAY_OF_YEAR -> timeInMillis += amount * 86400_000L
            HOUR, HOUR_OF_DAY -> timeInMillis += amount * 3600_000L
            MINUTE -> timeInMillis += amount * 60_000L
            SECOND -> timeInMillis += amount * 1000L
            MILLISECOND -> timeInMillis += amount.toLong()
        }
    }

    fun getActualMaximum(field: Int): Int {
        return when (field) {
            DAY_OF_MONTH, DATE -> daysInMonth(get(YEAR), get(MONTH))
            MONTH -> 11
            YEAR -> 9999
            HOUR_OF_DAY -> 23
            MINUTE, SECOND -> 59
            MILLISECOND -> 999
            else -> 31
        }
    }

    private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private fun daysInMonth(year: Int, month: Int): Int {
        return when (month) {
            0, 2, 4, 6, 7, 9, 11 -> 31
            3, 5, 8, 10 -> 30
            1 -> if (isLeapYear(year)) 29 else 28
            else -> 30
        }
    }

    private fun dateToDays(year: Int, month: Int, day: Int): Long {
        var y = year
        var m = month + 1
        if (m <= 2) {
            y -= 1
            m += 12
        }
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val doy = (153 * (m - 3) + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097L + doe - 719468L
    }

    private fun daysToDate(daysSinceEpoch: Int): IntArray {
        var z = daysSinceEpoch + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = doy - (153 * mp + 2) / 5 + 1
        val m = mp + (if (mp < 10) 3 else -9)
        val finalYear = y + (if (m <= 2) 1 else 0)
        return intArrayOf(finalYear, m - 1, d, doy + 1)
    }

    companion object {
        const val YEAR = 1
        const val MONTH = 2
        const val DATE = 5
        const val DAY_OF_MONTH = 5
        const val DAY_OF_YEAR = 6
        const val DAY_OF_WEEK = 7
        const val AM_PM = 9
        const val HOUR = 10
        const val HOUR_OF_DAY = 11
        const val MINUTE = 12
        const val SECOND = 13
        const val MILLISECOND = 14

        const val SUNDAY = 1
        const val MONDAY = 2
        const val TUESDAY = 3
        const val WEDNESDAY = 4
        const val THURSDAY = 5
        const val FRIDAY = 6
        const val SATURDAY = 7

        const val JANUARY = 0
        const val FEBRUARY = 1
        const val MARCH = 2
        const val APRIL = 3
        const val MAY = 4
        const val JUNE = 5
        const val JULY = 6
        const val AUGUST = 7
        const val SEPTEMBER = 8
        const val OCTOBER = 9
        const val NOVEMBER = 10
        const val DECEMBER = 11

        const val AM = 0
        const val PM = 1

        fun getInstance(): Calendar = Calendar()
        fun getInstance(locale: Locale): Calendar = Calendar()
    }
}
