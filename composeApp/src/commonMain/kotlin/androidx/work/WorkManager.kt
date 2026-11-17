package androidx.work

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class Data {
    private val map = mutableMapOf<String, Any?>()

    fun getInt(key: String, defaultValue: Int): Int = (map[key] as? Int) ?: defaultValue
    fun getLong(key: String, defaultValue: Long): Long = (map[key] as? Long) ?: defaultValue
    fun getString(key: String): String? = map[key] as? String
}

class WorkInfo {
    enum class State {
        ENQUEUED, RUNNING, SUCCEEDED, FAILED, BLOCKED, CANCELLED
    }
    val state: State = State.SUCCEEDED
    val progress: Data = Data()
    val outputData: Data = Data()
    val runAttemptCount: Int = 0
}

open class WorkRequest
class OneTimeWorkRequest : WorkRequest() {
    class Builder(workerClass: Any?) {
        fun build(): OneTimeWorkRequest = OneTimeWorkRequest()
    }
}

class PeriodicWorkRequest : WorkRequest() {
    class Builder(workerClass: Any?, interval: Long, timeUnit: Any?) {
        fun build(): PeriodicWorkRequest = PeriodicWorkRequest()
    }
}

inline fun <reified W> PeriodicWorkRequestBuilder(interval: Long, timeUnit: Any?): PeriodicWorkRequest.Builder =
    PeriodicWorkRequest.Builder(W::class, interval, timeUnit)

inline fun <reified W> OneTimeWorkRequestBuilder(): OneTimeWorkRequest.Builder =
    OneTimeWorkRequest.Builder(W::class)

enum class ExistingWorkPolicy {
    REPLACE, KEEP, APPEND, APPEND_OR_REPLACE
}

enum class ExistingPeriodicWorkPolicy {
    REPLACE, KEEP, UPDATE, CANCEL_AND_REENQUEUE
}

class WorkManager private constructor() {
    companion object {
        fun getInstance(context: Context): WorkManager = WorkManager()
    }

    fun enqueue(workRequest: Any?) {}
    fun enqueueUniqueWork(uniqueWorkName: String, existingWorkPolicy: ExistingWorkPolicy, work: Any?) {}
    fun enqueueUniquePeriodicWork(uniqueWorkName: String, existingPeriodicWorkPolicy: Any?, work: Any?) {}
    fun cancelUniqueWork(uniqueWorkName: String) {}
    fun cancelAllWork() {}
    fun getWorkInfosForUniqueWorkLiveData(uniqueWorkName: String): LiveData<List<WorkInfo>> =
        MutableLiveData(emptyList())
    fun getWorkInfosForUniqueWorkFlow(uniqueWorkName: String): Flow<List<WorkInfo>> =
        flowOf(emptyList())
}
