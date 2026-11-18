package java.math

enum class RoundingMode {
    UP, DOWN, CEILING, FLOOR, HALF_UP, HALF_DOWN, HALF_EVEN, UNNECESSARY
}

class BigDecimal(val value: Double) : Comparable<BigDecimal> {
    constructor(str: String) : this(str.toDoubleOrNull() ?: 0.0)
    constructor(longVal: Long) : this(longVal.toDouble())
    constructor(intVal: Int) : this(intVal.toDouble())

    fun multiply(other: BigDecimal): BigDecimal = BigDecimal(value * other.value)
    fun divide(other: BigDecimal, scale: Int = 0, roundingMode: RoundingMode = RoundingMode.HALF_UP): BigDecimal {
        if (other.value == 0.0) return BigDecimal(0.0)
        return BigDecimal(value / other.value).setScale(scale, roundingMode)
    }

    fun add(other: BigDecimal): BigDecimal = BigDecimal(value + other.value)
    fun subtract(other: BigDecimal): BigDecimal = BigDecimal(value - other.value)

    fun setScale(scale: Int, roundingMode: RoundingMode = RoundingMode.HALF_UP): BigDecimal {
        var multiplier = 1.0
        for (i in 0 until scale) multiplier *= 10.0
        val scaled = value * multiplier
        val rounded = when (roundingMode) {
            RoundingMode.HALF_UP -> kotlin.math.floor(scaled + 0.5)
            RoundingMode.HALF_DOWN -> kotlin.math.ceil(scaled - 0.5)
            RoundingMode.UP -> if (scaled >= 0) kotlin.math.ceil(scaled) else kotlin.math.floor(scaled)
            RoundingMode.DOWN -> if (scaled >= 0) kotlin.math.floor(scaled) else kotlin.math.ceil(scaled)
            else -> kotlin.math.round(scaled)
        }
        return BigDecimal(rounded / multiplier)
    }

    fun toLong(): Long = value.toLong()
    fun longValue(): Long = value.toLong()
    fun toDouble(): Double = value
    fun doubleValue(): Double = value
    fun toInt(): Int = value.toInt()

    operator fun times(other: BigDecimal): BigDecimal = multiply(other)
    operator fun div(other: BigDecimal): BigDecimal = divide(other)
    operator fun plus(other: BigDecimal): BigDecimal = add(other)
    operator fun minus(other: BigDecimal): BigDecimal = subtract(other)

    override operator fun compareTo(other: BigDecimal): Int = value.compareTo(other.value)
    override fun toString(): String = value.toString()
    override fun equals(other: Any?): Boolean = other is BigDecimal && other.value == value
    override fun hashCode(): Int = value.hashCode()

    companion object {
        val ZERO = BigDecimal(0.0)
        val ONE = BigDecimal(1.0)
        val TEN = BigDecimal(10.0)
        fun valueOf(valLong: Long): BigDecimal = BigDecimal(valLong.toDouble())
        fun valueOf(valInt: Int): BigDecimal = BigDecimal(valInt.toDouble())
        fun valueOf(valDouble: Double): BigDecimal = BigDecimal(valDouble)
    }
}
