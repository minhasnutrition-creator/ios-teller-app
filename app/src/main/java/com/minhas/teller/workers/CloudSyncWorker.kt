package com.minhas.teller.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.minhas.teller.SessionManager
import com.minhas.teller.utils.AuthManager
import com.minhas.teller.utils.CloudSyncEngine

/**
 * Background cloud sync. Runs periodically; no-ops immediately when the user is not signed in.
 * On failure it retries a few times (transient network) before giving up until the next period.
 */
class CloudSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            AuthManager.refresh()
            val uid = SessionManager.currentUserId.value
            if (uid == null) return Result.success() // not signed in — nothing to sync
            val result = CloudSyncEngine(applicationContext).sync()
            if (result.success) Result.success() else Result.retry()
        } catch (e: Exception) {
            android.util.Log.e("CloudSyncWorker", "Cloud sync worker failed", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
