package java.util.concurrent

enum class TimeUnit {
    NANOSECONDS {
        override fun toNanos(d: Long) = d
        override fun toMicros(d: Long) = d / 1000L
        override fun toMillis(d: Long) = d / 1_000_000L
        override fun toSeconds(d: Long) = d / 1_000_000_000L
        override fun toMinutes(d: Long) = d / 60_000_000_000L
        override fun toHours(d: Long) = d / 3_600_000_000_000L
        override fun toDays(d: Long) = d / 86_400_000_000_000L
    },
    MICROSECONDS {
        override fun toNanos(d: Long) = d * 1000L
        override fun toMicros(d: Long) = d
        override fun toMillis(d: Long) = d / 1000L
        override fun toSeconds(d: Long) = d / 1_000_000L
        override fun toMinutes(d: Long) = d / 60_000_000L
        override fun toHours(d: Long) = d / 3_600_000_000L
        override fun toDays(d: Long) = d / 86_400_000_000L
    },
    MILLISECONDS {
        override fun toNanos(d: Long) = d * 1_000_000L
        override fun toMicros(d: Long) = d * 1000L
        override fun toMillis(d: Long) = d
        override fun toSeconds(d: Long) = d / 1000L
        override fun toMinutes(d: Long) = d / 60_000L
        override fun toHours(d: Long) = d / 3_600_000L
        override fun toDays(d: Long) = d / 86_400_000L
    },
    SECONDS {
        override fun toNanos(d: Long) = d * 1_000_000_000L
        override fun toMicros(d: Long) = d * 1_000_000L
        override fun toMillis(d: Long) = d * 1000L
        override fun toSeconds(d: Long) = d
        override fun toMinutes(d: Long) = d / 60L
        override fun toHours(d: Long) = d / 3600L
        override fun toDays(d: Long) = d / 86400L
    },
    MINUTES {
        override fun toNanos(d: Long) = d * 60_000_000_000L
        override fun toMicros(d: Long) = d * 60_000_000L
        override fun toMillis(d: Long) = d * 60_000L
        override fun toSeconds(d: Long) = d * 60L
        override fun toMinutes(d: Long) = d
        override fun toHours(d: Long) = d / 60L
        override fun toDays(d: Long) = d / 1440L
    },
    HOURS {
        override fun toNanos(d: Long) = d * 3_600_000_000_000L
        override fun toMicros(d: Long) = d * 3_600_000_000L
        override fun toMillis(d: Long) = d * 3_600_000L
        override fun toSeconds(d: Long) = d * 3600L
        override fun toMinutes(d: Long) = d * 60L
        override fun toHours(d: Long) = d
        override fun toDays(d: Long) = d / 24L
    },
    DAYS {
        override fun toNanos(d: Long) = d * 86_400_000_000_000L
        override fun toMicros(d: Long) = d * 86_400_000_000L
        override fun toMillis(d: Long) = d * 86_400_000L
        override fun toSeconds(d: Long) = d * 86400L
        override fun toMinutes(d: Long) = d * 1440L
        override fun toHours(d: Long) = d * 24L
        override fun toDays(d: Long) = d
    };

    abstract fun toNanos(d: Long): Long
    abstract fun toMicros(d: Long): Long
    abstract fun toMillis(d: Long): Long
    abstract fun toSeconds(d: Long): Long
    abstract fun toMinutes(d: Long): Long
    abstract fun toHours(d: Long): Long
    abstract fun toDays(d: Long): Long
}

class ConcurrentHashMap<K, V> : LinkedHashMap<K, V>() {
    fun putIfAbsent(key: K, value: V): V? {
        val current = get(key)
        if (current == null) {
            put(key, value)
            return null
        }
        return current
    }
}
