package java.util

class Date(var time: Long = com.minhas.teller.currentTimeMillis()) : Comparable<Date> {
    fun getTime(): Long = time
    fun setTime(t: Long) {
        time = t
    }

    override operator fun compareTo(other: Date): Int = time.compareTo(other.time)
    fun after(whenDate: Date): Boolean = time > whenDate.time
    fun before(whenDate: Date): Boolean = time < whenDate.time

    override fun equals(other: Any?): Boolean = other is Date && other.time == time
    override fun hashCode(): Int = time.hashCode()
    override fun toString(): String = "Date($time)"
    fun clone(): Date = Date(time)

    companion object {
        fun from(instant: Any?): Date = Date()
    }
}
