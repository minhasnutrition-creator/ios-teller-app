package com.minhas.teller.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.minhas.teller.MainActivity
import com.minhas.teller.data.AppDatabase
import com.minhas.teller.data.gst.TaxRegion
import com.minhas.teller.utils.GstReturnPrefs
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Daily reminder for GST return due dates (India, monthly filers):
 *   GSTR-1  → due 11th,  GSTR-3B → due 20th of the following month.
 * Notifies on the due day and the day before, plus an overdue nudge. Suppressed for a period once
 * the user marks that return filed (see [GstReturnPrefs]).
 */
class GstReturnReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val EXTRA_OPEN_GST_RETURNS = "open_gst_returns"
        const val WORK_NAME = "GstReturnReminder"
    }

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val companies = db.companyDao().getAllCompanies().first()

            val cal = Calendar.getInstance()
            val day = cal.get(Calendar.DAY_OF_MONTH)
            val prevCal = (cal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
            val prevFp = String.format(Locale.US, "%02d%04d", prevCal.get(Calendar.MONTH) + 1, prevCal.get(Calendar.YEAR))
            val prevMonth = SimpleDateFormat("MMMM", Locale.getDefault()).format(prevCal.time)

            for (company in companies) {
                if (!TaxRegion.isIndia(company.country)) continue

                val gstr1Due = day in intArrayOf(10, 11, 15) &&
                    GstReturnPrefs.lastFiledGstr1(applicationContext, company.id) != prevFp
                val gstr3bDue = day in intArrayOf(19, 20, 25) &&
                    GstReturnPrefs.lastFiledGstr3b(applicationContext, company.id) != prevFp

                when {
                    gstr1Due -> notify(company.id, "GSTR-1 due", "GSTR-1 for $prevMonth is due — prepare and submit now.")
                    gstr3bDue -> notify(company.id, "GSTR-3B due", "GSTR-3B for $prevMonth is due — prepare and submit now.")
                }
            }
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun notify(companyId: Int, title: String, text: String) {
        val context = applicationContext
        val channelId = "gst_return_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "GST Return Reminders", NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_OPEN_GST_RETURNS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            companyId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()

        manager.notify(1000 + companyId, notification)
    }
}
