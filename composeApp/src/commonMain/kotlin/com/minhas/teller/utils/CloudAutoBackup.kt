package com.minhas.teller.utils

import android.util.Log
import com.minhas.teller.SessionManager
import com.minhas.teller.TellerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Automatically backs up local changes to the cloud in the background whenever a Google account
 * is signed in. Debounces rapid updates (e.g. bulk entry edits) so network traffic is minimized.
 */
object CloudAutoBackup {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var backupJob: Job? = null

    fun trigger() {
        // If not signed in to Google / Firebase, do nothing
        if (SessionManager.currentUserId.value.isNullOrBlank()) return

        backupJob?.cancel()
        backupJob = scope.launch {
            delay(1200) // 1.2 second debounce to bundle closely-spaced writes
            try {
                val app = runCatching { TellerApp.instance }.getOrNull() ?: return@launch
                val engine = CloudSyncEngine(app)
                val result = engine.backup()
                Log.i("CloudAutoBackup", "Auto-backup complete: ${result.message}")
            } catch (e: Exception) {
                Log.e("CloudAutoBackup", "Automatic background backup failed: ${e.message}")
            }
        }
    }
}

