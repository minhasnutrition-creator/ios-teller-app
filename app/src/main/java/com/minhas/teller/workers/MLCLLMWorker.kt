package com.minhas.teller.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.minhas.teller.utils.AiModelPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class MLCLLMWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "mlc_download_channel"
    private val notificationId = 101

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "AI Downloads", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelDir = applicationContext.getExternalFilesDir(null)
            ?: applicationContext.filesDir
        val targetFile = File(modelDir, AiModelPrefs.DEFAULT_FILENAME)
        val tmpFile = File(modelDir, AiModelPrefs.getTmpFilename(applicationContext))

        cleanupOldModels(modelDir)

        // ── Already downloaded & verified ──
        if (targetFile.exists() && targetFile.length() >= AiModelPrefs.DEFAULT_MIN_SIZE) {
            Log.d(TAG, "Model already exists at ${targetFile.absolutePath} (${targetFile.length() / 1024 / 1024} MB)")

            // Verify integrity if we have a known hash
            val expectedHash = AiModelPrefs.getExpectedSha256(applicationContext)
            if (expectedHash != null) {
                val actualHash = computeSha256(targetFile)
                if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                    Log.w(TAG, "Existing model SHA-256 mismatch! Expected: $expectedHash, Got: $actualHash")
                    Log.w(TAG, "Deleting corrupted file and re-downloading...")
                    targetFile.delete()
                    tmpFile.delete()
                    File(tmpFile.parentFile, tmpFile.name + ".state").delete()
                    // Fall through to download
                } else {
                    Log.d(TAG, "Model SHA-256 verified OK")
                    setProgress(workDataOf("progress" to 100, "stage" to "Ready"))
                    return@withContext Result.success(workDataOf("progress" to 100))
                }
            } else {
                setProgress(workDataOf("progress" to 100, "stage" to "Ready"))
                return@withContext Result.success(workDataOf("progress" to 100))
            }
        }

        // ── Resume partial download or start fresh ──
        val stateFile = File(tmpFile.parentFile, tmpFile.name + ".state")

        // Running out of space mid-transfer surfaces as a generic IOException and would
        // otherwise retry forever. Check up front on a fresh start so it fails loudly.
        if (!stateFile.exists()) {
            val required = AiModelPrefs.getExpectedSize(applicationContext)
            val usable = modelDir.usableSpace
            if (usable in 1 until required) {
                val neededMB = (required - usable) / 1024 / 1024
                Log.e(TAG, "Not enough storage: ${usable / 1024 / 1024} MB free, need ${required / 1024 / 1024} MB")
                setProgress(workDataOf("progress" to 0, "stage" to "Need $neededMB MB more storage"))
                return@withContext Result.failure(
                    workDataOf("error" to "Not enough storage - free up $neededMB MB and try again")
                )
            }
        }

        try {
            // Only meaningful for the single-thread path. When a state file exists the .tmp
            // was pre-allocated by the parallel downloader, so its length is the reserved
            // size rather than the downloaded size and must not be used as an offset.
            val resumedBytes = if (tmpFile.exists() && !stateFile.exists()) {
                val existing = tmpFile.length()
                if (existing > 0) {
                    Log.d(TAG, "Resuming download from byte $existing (${existing / 1024 / 1024} MB already downloaded)")
                    existing
                } else {
                    0L
                }
            } else {
                0L
            }

            downloadFile(
                targetUrlStr = AiModelPrefs.getModelUrl(applicationContext),
                destination = tmpFile,
                stateFile = stateFile,
                resumeFromByte = resumedBytes
            ) { progress, downloadedBytes, totalBytes ->
                val downloadedMB = downloadedBytes / (1024 * 1024)
                val totalMB = if (totalBytes > 0) totalBytes / (1024 * 1024) else 0
                updateNotification(progress, downloadedMB, totalMB)
                setProgress(workDataOf("progress" to progress, "stage" to "Downloading $downloadedMB MB / $totalMB MB"))
            }

            // ── Verify download ──
            if (!tmpFile.exists() || tmpFile.length() < AiModelPrefs.DEFAULT_MIN_SIZE) {
                Log.e(TAG, "Download incomplete: file size ${if (tmpFile.exists()) tmpFile.length() else 0} < ${AiModelPrefs.DEFAULT_MIN_SIZE}")
                // DON'T delete the tmp file — keep it for resume on next attempt
                return@withContext Result.retry()
            }

            val expectedHash = AiModelPrefs.getExpectedSha256(applicationContext)
            if (expectedHash != null) {
                val actualHash = computeSha256(tmpFile)
                if (!actualHash.equals(expectedHash, ignoreCase = true)) {
                    Log.e(TAG, "Downloaded file SHA-256 mismatch! Expected: $expectedHash, Got: $actualHash")
                    tmpFile.delete() // Corrupted — must re-download from scratch
                    stateFile.delete() // ...so the stale offsets must go too
                    return@withContext Result.retry()
                }
                Log.d(TAG, "SHA-256 verification passed")
            }

            // ── Move .tmp → final file ──
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renameSuccess = tmpFile.renameTo(targetFile)
            if (renameSuccess) {
                stateFile.delete()
                Log.d(TAG, "Model downloaded successfully to ${targetFile.absolutePath} (${targetFile.length() / 1024 / 1024} MB)")
                setProgress(workDataOf("progress" to 100, "stage" to "Complete"))
                Result.success(workDataOf("progress" to 100))
            } else {
                Log.e(TAG, "Failed to rename temp file to target file")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            // DON'T delete tmp file — resume on next retry
            // Only delete if we know the file is corrupted
            if (e is java.io.FileNotFoundException || e is java.net.UnknownHostException) {
                // Keep tmp for resume, just retry
                Log.d(TAG, "Transient error, keeping tmp file for resume")
            }
            Result.retry()
        }
    }

    private data class UrlInfo(val finalUrl: String, val contentLength: Long, val supportsRange: Boolean)

    private fun resolveUrl(targetUrlStr: String): UrlInfo {
        var currentUrl = targetUrlStr
        var redirectCount = 0
        val maxRedirects = 6

        while (redirectCount < maxRedirects) {
            val url = URL(currentUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
            conn.setRequestProperty("Range", "bytes=0-0")
            conn.connect()

            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                currentUrl = loc
                redirectCount++
                continue
            }

            val rangeSupported = code == 206 || conn.getHeaderField("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
            val contentRange = conn.getHeaderField("Content-Range")
            val totalLength = if (!contentRange.isNullOrBlank() && contentRange.contains("/")) {
                contentRange.substringAfter("/").trim().toLongOrNull() ?: conn.contentLengthLong
            } else {
                conn.contentLengthLong
            }
            conn.disconnect()
            return UrlInfo(currentUrl, totalLength, rangeSupported)
        }
        return UrlInfo(targetUrlStr, -1L, false)
    }

    /**
     * High-speed parallel chunk downloader. Uses 6 parallel worker threads to download
     * different byte segments simultaneously over HTTP Range requests.
     */
    private suspend fun downloadFile(
        targetUrlStr: String,
        destination: File,
        stateFile: File,
        resumeFromByte: Long = 0L,
        onProgress: suspend (Int, Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val info = resolveUrl(targetUrlStr)
        Log.d(TAG, "Resolved download URL: finalUrl=${info.finalUrl.take(60)}..., size=${info.contentLength / 1024 / 1024}MB, supportsRange=${info.supportsRange}")

        if (info.supportsRange && info.contentLength > 50_000_000L) {
            // Resume offsets live in the sidecar, not the caller - the pre-allocated file
            // length can't distinguish "reserved" from "downloaded".
            downloadParallel(info.finalUrl, destination, info.contentLength, stateFile, onProgress)
        } else {
            downloadSingleThread(info.finalUrl, destination, resumeFromByte, onProgress)
        }
    }

    /**
     * Per-chunk resume state, persisted next to the .tmp file.
     *
     * The parallel downloader writes into a sparse pre-allocated file, so the file's own
     * length says nothing about how much has actually arrived - it reads as the full size
     * from the first byte written. Without this sidecar, every retry restarted all
     * [totalBytes] from zero, which is why the download could never outrun a transient
     * network stall. Format is deliberately trivial:
     *
     *     <totalBytes>:<numThreads>
     *     <done0>,<done1>,...
     *
     * The header is a fingerprint: if the model size or the thread count changes, the
     * chunk boundaries change too, so the saved offsets are meaningless and we start over.
     */
    private fun readChunkState(stateFile: File, totalBytes: Long, numThreads: Int): LongArray {
        val fresh = LongArray(numThreads)
        if (!stateFile.exists()) return fresh
        return try {
            val lines = stateFile.readLines()
            if (lines.size < 2) return fresh
            val header = lines[0].split(":")
            if (header.size != 2) return fresh
            if (header[0].trim().toLongOrNull() != totalBytes) {
                Log.d(TAG, "Resume state is for a different model size - starting fresh")
                return fresh
            }
            if (header[1].trim().toIntOrNull() != numThreads) return fresh
            val values = lines[1].split(",").mapNotNull { it.trim().toLongOrNull() }
            if (values.size != numThreads) return fresh
            LongArray(numThreads) { values[it].coerceAtLeast(0L) }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read resume state (${e.message}) - starting fresh")
            fresh
        }
    }

    private fun writeChunkState(stateFile: File, totalBytes: Long, numThreads: Int, done: LongArray) {
        try {
            stateFile.writeText("$totalBytes:$numThreads\n" + done.joinToString(","))
        } catch (e: Exception) {
            // Losing the sidecar only costs us the resume, never correctness.
            Log.w(TAG, "Could not persist resume state: ${e.message}")
        }
    }

    private suspend fun downloadParallel(
        urlStr: String,
        destination: File,
        totalBytes: Long,
        stateFile: File,
        onProgress: suspend (Int, Long, Long) -> Unit
    ): Unit = coroutineScope {
        val numThreads = 8
        val chunkSize = (totalBytes + numThreads - 1) / numThreads

        fun chunkStart(idx: Int) = idx * chunkSize
        fun chunkEnd(idx: Int) = minOf((idx + 1) * chunkSize - 1, totalBytes - 1)
        fun chunkMax(idx: Int) = chunkEnd(idx) - chunkStart(idx) + 1

        // Bytes already on disk per chunk, clamped in case the sidecar outran the file.
        val chunkDone = readChunkState(stateFile, totalBytes, numThreads)
        for (i in 0 until numThreads) {
            chunkDone[i] = chunkDone[i].coerceIn(0L, chunkMax(i))
        }
        val resumedTotal = chunkDone.sum()
        if (resumedTotal > 0) {
            Log.d(TAG, "Resuming parallel download at ${resumedTotal / 1024 / 1024} MB / ${totalBytes / 1024 / 1024} MB")
        }

        // Two counters, deliberately: progressByChunk is the durable (fsync'd) resume offset,
        // liveByChunk is what has been written this run. Progress is reported from the live
        // one; a retry rewinds that chunk's live value to its durable offset, so bytes
        // re-fetched after a stall are never counted twice.
        val progressByChunk = java.util.concurrent.atomic.AtomicLongArray(numThreads)
        val liveByChunk = java.util.concurrent.atomic.AtomicLongArray(numThreads)
        for (i in 0 until numThreads) {
            progressByChunk.set(i, chunkDone[i])
            liveByChunk.set(i, chunkDone[i])
        }
        fun liveTotal(): Long {
            var sum = 0L
            for (i in 0 until numThreads) sum += liveByChunk.get(i)
            return sum
        }

        // Pre-allocate file size for random access
        RandomAccessFile(destination, "rw").use { raf ->
            if (raf.length() != totalBytes) {
                raf.setLength(totalBytes)
            }
        }

        fun snapshot() = LongArray(numThreads) { progressByChunk.get(it) }

        val progressJob = launch {
            var lastReported = -1
            var sincePersist = 0
            while (isActive) {
                val current = liveTotal()
                val progress = if (totalBytes > 0) ((current * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                if (progress != lastReported) {
                    lastReported = progress
                    onProgress(progress, current, totalBytes)
                }
                // Persist roughly every 2s so a kill/timeout costs seconds, not gigabytes.
                if (++sincePersist >= 5) {
                    sincePersist = 0
                    writeChunkState(stateFile, totalBytes, numThreads, snapshot())
                }
                delay(400)
            }
        }

        try {
            val chunkJobs = (0 until numThreads).map { threadIdx ->
                val start = chunkStart(threadIdx)
                val end = chunkEnd(threadIdx)
                val threadMax = chunkMax(threadIdx)

                async(Dispatchers.IO) {
                    val maxAttempts = 3
                    var attempt = 0
                    var lastOffset = -1L
                    while (true) {
                        val already = progressByChunk.get(threadIdx)
                        if (already >= threadMax) return@async
                        if (isStopped) return@async

                        // A retry re-fetches whatever wasn't fsync'd, so rewind the live
                        // counter to the durable offset to keep the total honest.
                        liveByChunk.set(threadIdx, already)

                        // Only give up after maxAttempts of making *no* headway - a slow link
                        // that keeps inching forward should be allowed to finish.
                        if (already > lastOffset) {
                            lastOffset = already
                            attempt = 0
                        }

                        var conn: HttpURLConnection? = null
                        try {
                            val url = URL(urlStr)
                            conn = (url.openConnection() as HttpURLConnection).apply {
                                connectTimeout = 30_000
                                // A large model over a slow mobile link can legitimately stall
                                // between reads; 2 minutes was tight enough to abort healthy
                                // downloads mid-stream.
                                readTimeout = 300_000
                                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                                setRequestProperty("Range", "bytes=${start + already}-$end")
                            }
                            conn.connect()

                            val code = conn.responseCode
                            if (code != 206 && code != 200) {
                                throw java.io.IOException("Thread $threadIdx failed with HTTP $code")
                            }
                            // A 200 means the server ignored our Range and is replaying the whole
                            // file - writing that at an offset would corrupt the chunk.
                            if (code == 200 && already > 0) {
                                throw java.io.IOException("Thread $threadIdx: server ignored Range on resume")
                            }

                            RandomAccessFile(destination, "rw").use { raf ->
                                raf.seek(start + already)
                                conn.inputStream.use { input ->
                                    val buffer = ByteArray(131072) // 128KB buffer per thread
                                    var bytesRead: Int
                                    var threadWritten = already
                                    var unsynced = 0L

                                    // progressByChunk is the *resume* offset, so it may only ever
                                    // advance past bytes that are durable on disk. Writes land in
                                    // the page cache, so we fsync every 16 MB and publish the
                                    // offset only then - otherwise a crash could leave a hole that
                                    // resume would skip over and never fill.
                                    fun publish() {
                                        if (unsynced == 0L) return
                                        raf.fd.sync()
                                        unsynced = 0L
                                        progressByChunk.set(threadIdx, threadWritten)
                                    }

                                    try {
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            if (isStopped) {
                                                publish()
                                                return@async
                                            }
                                            val toWrite = minOf(bytesRead.toLong(), threadMax - threadWritten).toInt()
                                            if (toWrite <= 0) break
                                            raf.write(buffer, 0, toWrite)
                                            threadWritten += toWrite
                                            unsynced += toWrite
                                            liveByChunk.set(threadIdx, threadWritten)
                                            if (unsynced >= 16L * 1024 * 1024) publish()
                                            if (threadWritten >= threadMax) break
                                        }
                                        publish()
                                    } catch (e: Exception) {
                                        // Bank whatever is durable before surfacing the failure,
                                        // so the retry resumes from there instead of from `already`.
                                        runCatching { publish() }
                                        throw e
                                    }
                                }
                            }

                            if (progressByChunk.get(threadIdx) >= threadMax) return@async
                            // Stream ended early (dropped connection) - fall through and retry
                            // from the new offset rather than failing the whole download.
                            throw java.io.IOException("Thread $threadIdx: stream ended at ${progressByChunk.get(threadIdx)}/$threadMax")
                        } catch (e: Exception) {
                            attempt++
                            writeChunkState(stateFile, totalBytes, numThreads, snapshot())
                            if (attempt >= maxAttempts || isStopped) throw e
                            Log.w(TAG, "Thread $threadIdx attempt $attempt failed (${e.message}) - retrying from ${progressByChunk.get(threadIdx)}")
                            delay(2_000L * attempt)
                        } finally {
                            conn?.disconnect()
                        }
                    }
                }
            }

            chunkJobs.awaitAll()
        } finally {
            progressJob.cancel()
            writeChunkState(stateFile, totalBytes, numThreads, snapshot())
        }

        val finalBytes = liveTotal()
        // The file is pre-allocated to full size, so its length can't tell us whether the
        // transfer finished. This is the only real completeness gate before the rename.
        if (finalBytes < totalBytes) {
            throw java.io.IOException("Incomplete download: $finalBytes / $totalBytes bytes")
        }
        onProgress(100, finalBytes, totalBytes)
    }

    private suspend fun downloadSingleThread(
        targetUrlStr: String,
        destination: File,
        resumeFromByte: Long = 0L,
        onProgress: suspend (Int, Long, Long) -> Unit
    ) {
        val url = URL(targetUrlStr)
        var connection = url.openConnection() as HttpURLConnection
        var redirectCount = 0
        val maxRedirects = 5

        while (redirectCount < maxRedirects) {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 30_000
            connection.readTimeout = 120_000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")

            if (resumeFromByte > 0) {
                connection.setRequestProperty("Range", "bytes=$resumeFromByte-")
            }

            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode in 300..399) {
                val location = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                connection = URL(location).openConnection() as HttpURLConnection
                redirectCount++
                continue
            }
            break
        }

        val responseCode = connection.responseCode
        val isPartial = responseCode == 206

        if (responseCode != HttpURLConnection.HTTP_OK && responseCode != 206) {
            throw java.io.IOException("Server returned HTTP $responseCode: ${connection.responseMessage}")
        }

        val contentLength = connection.contentLengthLong
        val alreadyDownloaded = if (isPartial) resumeFromByte else 0L
        val remainingBytes = contentLength
        val totalBytes = alreadyDownloaded + remainingBytes

        var downloadedBytes = alreadyDownloaded

        val rawFos = if (resumeFromByte > 0 && destination.exists()) {
            FileOutputStream(destination, true)
        } else {
            FileOutputStream(destination, false)
        }

        java.io.BufferedOutputStream(rawFos, 262144).use { output ->
            java.io.BufferedInputStream(connection.inputStream, 262144).use { input ->
                val buffer = ByteArray(262144)
                var bytesRead: Int
                var lastProgressUpdate = System.currentTimeMillis()
                var lastProgressPercent = -1

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (isStopped) {
                        output.flush()
                        connection.disconnect()
                        return
                    }
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val now = System.currentTimeMillis()
                    val progress = if (totalBytes > 0)
                        ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                    else 0

                    if (progress != lastProgressPercent && (now - lastProgressUpdate > 500 || progress == 100)) {
                        lastProgressPercent = progress
                        lastProgressUpdate = now
                        onProgress(progress, downloadedBytes, totalBytes)
                    }
                }
                output.flush()
            }
        }
        connection.disconnect()
    }

    /**
     * Computes SHA-256 hex digest of a file.
     * Uses a buffered read to handle large files efficiently.
     */
    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32768)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun cleanupOldModels(modelDir: File) {
        val oldFiles = listOf(
            "qwen2.5-0.5b.bin", "qwen2.5-0.5b.bin.tmp",
            "gemma2-2b-it-gpu-int8.bin", "gemma2-2b-it-gpu-int8.bin.tmp",
            "gemma-2b-it-cpu-int4.bin", "gemma-2b-it-cpu-int4.bin.tmp",
            "gemma3-4b-it-int4-web.task", "gemma3-4b-it-int4-web.task.tmp", "gemma3-4b-it-int4-web.task.state",
            "gemma3-1b-it-int4.task.tmp"
        )
        oldFiles.forEach { name ->
            val file = File(modelDir, name)
            if (file.exists()) {
                Log.d(TAG, "Removing old model: ${file.name}")
                file.delete()
            }
        }
    }

    private fun updateNotification(progress: Int, downloadedMB: Long, totalMB: Long) {
        val text = if (totalMB > 0) "$progress% ($downloadedMB MB / $totalMB MB)" else "$progress%"
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Downloading AI Model")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = createForegroundInfo(0)

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Downloading AI Model")
            .setContentText(if (progress > 0) "$progress% complete" else "Starting download...")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ForegroundInfo(notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else
            ForegroundInfo(notificationId, notification)
    }

    companion object {
        const val TAG = "MLCLLMWorker"
        const val WORK_NAME = "gemma_model_download"
    }
}
