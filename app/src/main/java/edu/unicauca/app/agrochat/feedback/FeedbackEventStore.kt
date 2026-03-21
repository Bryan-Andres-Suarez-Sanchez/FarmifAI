package edu.unicauca.app.agrochat.feedback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import edu.unicauca.app.agrochat.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Almacena eventos de retroalimentacion en formato JSONL para analisis de campo.
 * Estructura:
 * files/feedback/feedback_events_YYYY-MM-DD.jsonl
 */
class FeedbackEventStore(context: Context) {
    companion object {
        private const val TAG = "FeedbackEventStore"
        private const val CATBOX_UPLOAD_URL = "https://catbox.moe/user/api.php"
        private const val MIN_UPLOAD_INTERVAL_MS = 15_000L
        private const val MAX_PENDING_LIVE_EVENTS = 2000
    }

    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "feedback").apply { mkdirs() }
    private val fileLock = Any()
    private val syncLock = Any()
    private val liveEndpoint = BuildConfig.FEEDBACK_LIVE_ENDPOINT.trim()
    private val dayFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val localFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    private val syncManifestFile = File(rootDir, "feedback_sync_manifest.json")
    private val pendingLiveEventsFile = File(rootDir, "feedback_live_pending.jsonl")

    fun storagePath(): String = rootDir.absolutePath
    fun syncManifestPath(): String = syncManifestFile.absolutePath
    fun pendingLivePath(): String = pendingLiveEventsFile.absolutePath

    suspend fun recordAssistantResponse(
        sessionId: String,
        messageId: String,
        turnIndex: Int,
        userQuery: String,
        assistantResponse: String,
        responseLabel: String,
        usedLlm: Boolean,
        kbSupported: Boolean,
        kbSupportScore: Float,
        kbCoverage: Float,
        kbUnknownRatio: Float
    ) {
        val now = System.currentTimeMillis()
        val event = baseEvent("assistant_response", now, sessionId, messageId)
            .put("turn_index", turnIndex)
            .put("response_label", responseLabel)
            .put("used_llm", usedLlm)
            .put("kb_supported", kbSupported)
            .put("kb_support_score", kbSupportScore.toDouble())
            .put("kb_coverage", kbCoverage.toDouble())
            .put("kb_unknown_ratio", kbUnknownRatio.toDouble())
            .put("user_query", userQuery.take(1200))
            .put("assistant_response", assistantResponse.take(2400))

        // Se guarda localmente para trazabilidad, pero no se encola envio remoto
        // hasta que el usuario marque al menos una respuesta del feedback.
        appendEvent(now, event, enqueueRemote = false)
    }

    suspend fun recordFeedbackUpdate(
        sessionId: String,
        messageId: String,
        responseLabel: String,
        userQuery: String,
        assistantResponse: String,
        helpful: Boolean?,
        clear: Boolean?,
        wouldApplyToday: Boolean?
    ) {
        val now = System.currentTimeMillis()
        val event = baseEvent("feedback_update", now, sessionId, messageId)
            .put("response_label", responseLabel)
            .put("helpful", helpful)
            .put("clear", clear)
            .put("would_apply_today", wouldApplyToday)
            .put("user_query", userQuery.take(1200))
            .put("assistant_response", assistantResponse.take(2400))

        // Cualquier respuesta del feedback dispara la cola de envio remoto.
        appendEvent(now, event, enqueueRemote = true)
    }

    private fun baseEvent(
        type: String,
        nowMs: Long,
        sessionId: String,
        messageId: String
    ): JSONObject {
        return JSONObject()
            .put("event_id", UUID.randomUUID().toString())
            .put("event_type", type)
            .put("timestamp_ms", nowMs)
            .put("timestamp_local", localFormatter.format(Date(nowMs)))
            .put("timestamp_iso", isoFormatter.format(Date(nowMs)))
            .put("timezone_id", TimeZone.getDefault().id)
            .put("session_id", sessionId)
            .put("message_id", messageId)
            .put("app_version", BuildConfig.VERSION_NAME)
            .put("app_package", BuildConfig.APPLICATION_ID)
    }

    private suspend fun appendEvent(
        nowMs: Long,
        event: JSONObject,
        enqueueRemote: Boolean
    ) = withContext(Dispatchers.IO) {
        val outFile = File(rootDir, "feedback_events_${dayFormatter.format(Date(nowMs))}.jsonl")
        synchronized(fileLock) {
            outFile.parentFile?.mkdirs()
            outFile.appendText(event.toString() + "\n")
        }
        if (enqueueRemote) {
            try {
                enqueueLiveEvent(event)
                flushPendingLiveEvents(nowMs)
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo enviar evento a endpoint live: ${error.message}")
                updateLiveSyncStatus(
                    nowMs = nowMs,
                    success = false,
                    responseCode = null,
                    errorMessage = error.message ?: "unknown_error",
                    pendingCount = countPendingLiveEvents()
                )
            }
            try {
                uploadDailySnapshotIfNeeded(outFile, nowMs, event.optString("event_type", "unknown"))
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo sincronizar feedback a internet: ${error.message}")
            }
        }
    }

    private fun uploadDailySnapshotIfNeeded(outFile: File, nowMs: Long, triggerType: String) {
        if (!isInternetAvailable()) return

        val currentHash = sha256(outFile)
        val previousState = getFileSyncState(outFile.name)
        val lastUploadMs = previousState?.optLong("last_uploaded_at_ms", 0L) ?: 0L
        val lastHash = previousState?.optString("last_hash", "") ?: ""

        // Evitar subir demasiado seguido durante una misma interacción.
        if (lastUploadMs > 0L && nowMs - lastUploadMs < MIN_UPLOAD_INTERVAL_MS) return
        // Si no cambió el contenido, no subir de nuevo.
        if (currentHash == lastHash) return

        val remoteUrl = uploadFileToCatbox(outFile) ?: return
        updateSyncState(
            fileName = outFile.name,
            nowMs = nowMs,
            fileHash = currentHash,
            remoteUrl = remoteUrl,
            fileSizeBytes = outFile.length(),
            lineCount = countJsonlLines(outFile),
            triggerType = triggerType
        )
        Log.i(TAG, "Feedback sincronizado: $remoteUrl")
    }

    private data class LivePostResult(
        val success: Boolean,
        val responseCode: Int?,
        val errorMessage: String?
    )

    private fun enqueueLiveEvent(event: JSONObject) {
        synchronized(fileLock) {
            pendingLiveEventsFile.parentFile?.mkdirs()
            pendingLiveEventsFile.appendText(event.toString() + "\n")

            // Evita crecimiento infinito si hay mala conectividad prolongada.
            val lines = pendingLiveEventsFile.readLines()
            if (lines.size > MAX_PENDING_LIVE_EVENTS) {
                val trimmed = lines.takeLast(MAX_PENDING_LIVE_EVENTS)
                pendingLiveEventsFile.writeText(trimmed.joinToString("\n", postfix = "\n"))
            }
        }
    }

    private fun flushPendingLiveEvents(nowMs: Long) {
        if (liveEndpoint.isBlank()) {
            updateLiveSyncStatus(
                nowMs = nowMs,
                success = false,
                responseCode = null,
                errorMessage = "live_endpoint_empty",
                pendingCount = countPendingLiveEvents()
            )
            return
        }
        if (!isInternetAvailable()) {
            updateLiveSyncStatus(
                nowMs = nowMs,
                success = false,
                responseCode = null,
                errorMessage = "no_internet",
                pendingCount = countPendingLiveEvents()
            )
            return
        }

        val pendingLines = synchronized(fileLock) {
            if (!pendingLiveEventsFile.exists()) emptyList() else pendingLiveEventsFile.readLines()
        }
        if (pendingLines.isEmpty()) return

        val remaining = mutableListOf<String>()
        var lastResult: LivePostResult? = null

        for ((index, line) in pendingLines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            val event = runCatching { JSONObject(trimmed) }.getOrNull()
            if (event == null) {
                // Línea dañada: se descarta para no bloquear la cola.
                continue
            }

            val result = postEventToLiveEndpoint(event)
            lastResult = result
            if (!result.success) {
                remaining.add(trimmed)
                for (j in index + 1 until pendingLines.size) {
                    val tail = pendingLines[j].trim()
                    if (tail.isNotBlank()) remaining.add(tail)
                }
                break
            }
        }

        synchronized(fileLock) {
            if (remaining.isEmpty()) {
                pendingLiveEventsFile.delete()
            } else {
                pendingLiveEventsFile.writeText(remaining.joinToString("\n", postfix = "\n"))
            }
        }

        val pendingCount = remaining.size
        if (lastResult != null) {
            updateLiveSyncStatus(
                nowMs = nowMs,
                success = lastResult.success,
                responseCode = lastResult.responseCode,
                errorMessage = lastResult.errorMessage,
                pendingCount = pendingCount
            )
        } else {
            updateLiveSyncStatus(
                nowMs = nowMs,
                success = true,
                responseCode = 200,
                errorMessage = null,
                pendingCount = pendingCount
            )
        }
    }

    private fun postEventToLiveEndpoint(event: JSONObject): LivePostResult {
        if (liveEndpoint.isBlank()) {
            return LivePostResult(success = false, responseCode = null, errorMessage = "live_endpoint_empty")
        }
        if (!isInternetAvailable()) {
            return LivePostResult(success = false, responseCode = null, errorMessage = "no_internet")
        }

        val connection = (URL(liveEndpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            useCaches = false
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "FarmifAI-FeedbackSync/1.0")
        }

        return try {
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(event.toString())
                writer.flush()
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "Live endpoint rechazó evento code=$code body='${errorBody.take(180)}'")
                LivePostResult(
                    success = false,
                    responseCode = code,
                    errorMessage = errorBody.take(180).ifBlank { "http_$code" }
                )
            } else {
                Log.i(TAG, "Live endpoint OK code=$code")
                LivePostResult(success = true, responseCode = code, errorMessage = null)
            }
        } catch (error: Exception) {
            LivePostResult(
                success = false,
                responseCode = null,
                errorMessage = error.message ?: "network_error"
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun uploadFileToCatbox(file: File): String? {
        val boundary = "----AgroChatBoundary${System.currentTimeMillis()}"
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        val connection = (URL(CATBOX_UPLOAD_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doInput = true
            doOutput = true
            useCaches = false
            connectTimeout = 15000
            readTimeout = 45000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        return try {
            DataOutputStream(connection.outputStream).use { output ->
                output.writeBytes(twoHyphens + boundary + lineEnd)
                output.writeBytes("Content-Disposition: form-data; name=\"reqtype\"$lineEnd$lineEnd")
                output.writeBytes("fileupload$lineEnd")

                output.writeBytes(twoHyphens + boundary + lineEnd)
                output.writeBytes(
                    "Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"${file.name}\"$lineEnd"
                )
                output.writeBytes("Content-Type: application/octet-stream$lineEnd$lineEnd")

                FileInputStream(file).use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                }

                output.writeBytes(lineEnd)
                output.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd)
                output.flush()
            }

            val responseCode = connection.responseCode
            val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?.trim()
                .orEmpty()

            if (responseCode in 200..299 && responseText.startsWith("https://")) {
                responseText
            } else {
                Log.w(TAG, "Upload Catbox fallido code=$responseCode body='${responseText.take(180)}'")
                null
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager =
            appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        // En visitas de campo puede haber validación inestable; basta con capacidad de Internet.
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun countJsonlLines(file: File): Int =
        file.useLines { lines -> lines.count() }

    private fun getFileSyncState(fileName: String): JSONObject? = synchronized(syncLock) {
        val manifest = loadSyncManifestLocked()
        manifest.optJSONObject("files")?.optJSONObject(fileName)
    }

    private fun countPendingLiveEvents(): Int = synchronized(fileLock) {
        if (!pendingLiveEventsFile.exists()) return@synchronized 0
        pendingLiveEventsFile.useLines { lines -> lines.count { it.isNotBlank() } }
    }

    private fun updateSyncState(
        fileName: String,
        nowMs: Long,
        fileHash: String,
        remoteUrl: String,
        fileSizeBytes: Long,
        lineCount: Int,
        triggerType: String
    ) = synchronized(syncLock) {
        val manifest = loadSyncManifestLocked()
        val files = manifest.optJSONObject("files") ?: JSONObject().also { manifest.put("files", it) }
        val state = files.optJSONObject(fileName) ?: JSONObject()
        val uploadCount = state.optInt("upload_count", 0) + 1

        state.put("last_hash", fileHash)
            .put("last_uploaded_at_ms", nowMs)
            .put("last_uploaded_at_local", localFormatter.format(Date(nowMs)))
            .put("last_uploaded_at_iso", isoFormatter.format(Date(nowMs)))
            .put("last_remote_url", remoteUrl)
            .put("last_file_size_bytes", fileSizeBytes)
            .put("last_line_count", lineCount)
            .put("last_trigger_event", triggerType)
            .put("upload_count", uploadCount)

        files.put(fileName, state)
        manifest.put("updated_at_ms", nowMs)
            .put("updated_at_local", localFormatter.format(Date(nowMs)))
            .put("updated_at_iso", isoFormatter.format(Date(nowMs)))
            .put("timezone_id", TimeZone.getDefault().id)

        syncManifestFile.parentFile?.mkdirs()
        syncManifestFile.writeText(manifest.toString(2))
    }

    private fun updateLiveSyncStatus(
        nowMs: Long,
        success: Boolean,
        responseCode: Int?,
        errorMessage: String?,
        pendingCount: Int
    ) = synchronized(syncLock) {
        val manifest = loadSyncManifestLocked()
        val live = manifest.optJSONObject("live_endpoint_status") ?: JSONObject()
        val deliveredCount = live.optLong("delivered_count", 0L)
        val failedCount = live.optLong("failed_count", 0L)

        live.put("endpoint", liveEndpoint)
            .put("last_attempt_at_ms", nowMs)
            .put("last_attempt_at_local", localFormatter.format(Date(nowMs)))
            .put("last_attempt_at_iso", isoFormatter.format(Date(nowMs)))
            .put("last_response_code", responseCode)
            .put("last_error", errorMessage)
            .put("pending_events", pendingCount)

        if (success) {
            live.put("last_success_at_ms", nowMs)
                .put("last_success_at_local", localFormatter.format(Date(nowMs)))
                .put("last_success_at_iso", isoFormatter.format(Date(nowMs)))
                .put("delivered_count", deliveredCount + 1L)
        } else {
            live.put("failed_count", failedCount + 1L)
        }

        manifest.put("live_endpoint_status", live)
            .put("updated_at_ms", nowMs)
            .put("updated_at_local", localFormatter.format(Date(nowMs)))
            .put("updated_at_iso", isoFormatter.format(Date(nowMs)))
            .put("timezone_id", TimeZone.getDefault().id)

        syncManifestFile.parentFile?.mkdirs()
        syncManifestFile.writeText(manifest.toString(2))
    }

    private fun loadSyncManifestLocked(): JSONObject {
        if (!syncManifestFile.exists()) {
            return JSONObject()
                .put("format", "feedback_sync_manifest_v1")
                .put("files", JSONObject())
        }
        return try {
            JSONObject(syncManifestFile.readText())
        } catch (_: Exception) {
            JSONObject()
                .put("format", "feedback_sync_manifest_v1")
                .put("files", JSONObject())
        }
    }
}
