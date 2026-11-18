package android.text.format

import java.util.Calendar

object DateUtils {
    fun isToday(timeMs: Long): Boolean {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timeMs }
        return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
               now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

    fun formatDateTime(context: Any?, timeMs: Long, flags: Int): String {
        return java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a").format(timeMs)
    }
}
