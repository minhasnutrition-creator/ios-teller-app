package java.util

class UUID(val mostSigBits: Long, val leastSigBits: Long) : Comparable<UUID> {
    override fun compareTo(other: UUID): Int {
        val c = mostSigBits.compareTo(other.mostSigBits)
        return if (c != 0) c else leastSigBits.compareTo(other.leastSigBits)
    }

    override fun toString(): String {
        fun toHex(v: Long, len: Int): String {
            val hex = v.toULong().toString(16)
            return hex.padStart(len, '0').takeLast(len)
        }
        val msb = toHex(mostSigBits, 16)
        val lsb = toHex(leastSigBits, 16)
        return "${msb.substring(0, 8)}-${msb.substring(8, 12)}-${msb.substring(12, 16)}-${lsb.substring(0, 4)}-${lsb.substring(4, 16)}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UUID) return false
        return mostSigBits == other.mostSigBits && leastSigBits == other.leastSigBits
    }

    override fun hashCode(): Int = (mostSigBits xor (mostSigBits ushr 32) xor leastSigBits xor (leastSigBits ushr 32)).toInt()

    companion object {
        private var counter = 1000L

        fun randomUUID(): UUID {
            val now = com.minhas.teller.currentTimeMillis()
            counter++
            val msb = (now shl 16) or (counter and 0xFFFF)
            val lsb = (counter shl 32) xor now
            return UUID(msb, lsb)
        }

        fun fromString(name: String): UUID {
            val parts = name.split("-")
            if (parts.size == 5) {
                val msb = (parts[0].toLong(16) shl 32) or (parts[1].toLong(16) shl 16) or parts[2].toLong(16)
                val lsb = (parts[3].toLong(16) shl 48) or parts[4].toLong(16)
                return UUID(msb, lsb)
            }
            return UUID(name.hashCode().toLong(), 0L)
        }
    }
}
