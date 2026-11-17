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
import com.minhas.teller.data.repositories.VoucherRepository
import com.minhas.teller.utils.paiseToRupees
import kotlinx.coroutines.flow.first
import java.util.Calendar

class DailySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val companies = db.companyDao().getAllCompanies().first()
            val company = companies.firstOrNull() ?: return Result.success()
            val companyId = company.id

            // Today's Date Range
            val todayStart = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            val todayEnd = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }.time

            val repo = VoucherRepository(
                db.voucherDao(),
                db.voucherEntryDao(),
                db.inventoryEntryDao(),
                db.billAllocationDao(),
                db.auditLogDao(),
                db.ledgerDao(),
                db = db
            )

            // 1. Sales metrics
            val todaySalesVouchers = repo.getAllVouchersWithAllEntriesBetweenDates(companyId, todayStart, todayEnd)
                .filter { it.voucher.type.equals("Sales", ignoreCase = true) }
            val salesCount = todaySalesVouchers.size
            val salesAmount = todaySalesVouchers.sumOf { vwe ->
                vwe.voucherEntries.filter { it.isDebit }.sumOf { it.amount }
            }.paiseToRupees()

            // 2. Receivables pending
            val receivables = repo.getAllOutstandingVouchers(companyId)
                .filter { it.isDebit && it.pendingAmount > 0 }
                .sumOf { it.pendingAmount }

            // 3. Low stock count
            val stockItems = db.stockItemDao().getAllStockItems(companyId)
            val allVouchers = repo.getAllVouchersWithAllEntries(companyId)
            val stockBalances = stockItems.associate { it.id to Pair(it.openingStock, it.rate) }.toMutableMap()
            allVouchers.forEach { vw ->
                vw.inventoryEntries.forEach { entry ->
                    val current = stockBalances.getOrDefault(entry.stockItemId, Pair(0.0, 0.0))
                    val t = vw.voucher.type
                    stockBalances[entry.stockItemId] = when {
                        t == "Sales" || t == "Purchase Return" || t == "Debit Note" -> Pair(current.first - entry.quantity, current.second)
                        t == "Purchase" || t == "Sales Return" || t == "Credit Note" -> Pair(current.first + entry.quantity, entry.rate)
                        t == "Journal" -> Pair(current.first + entry.quantity, if (entry.quantity > 0) entry.rate else current.second)
                        else -> current
                    }
                }
            }
            val lowStockCount = stockItems.count {
                val qty = stockBalances[it.id]?.first ?: 0.0
                qty <= 0.0
            }

            // Build Notification Text
            val summaryText = "Today: $salesCount sales ₹${String.format("%.0f", salesAmount)} · ₹${String.format("%.0f", receivables)} still to collect · $lowStockCount items low."
            showSummaryNotification(summaryText)

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("DailySummaryWorker", "Daily summary worker failed", e)
            Result.failure()
        }
    }

    private fun showSummaryNotification(text: String) {
        val context = applicationContext
        val channelId = "daily_summary_channel"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Daily Summary", NotificationManager.IMPORTANCE_DEFAULT)
            manager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Daily Business Summary")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build()

        manager.notify(1002, notification)
    }
}
