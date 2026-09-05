package edu.unicauca.app.agrochat.rag

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** Métricas de proceso acumuladas durante la vida de la aplicación. */
object QueryTelemetry {
    private const val TAG = "RAG_METRICS"
    private val sequence = AtomicLong(0)
    private val startedAt = mutableMapOf<Long, Long>()
    private val queryStages = mutableMapOf<Long, MutableMap<String, Double>>()
    private val totals = mutableMapOf<String, Double>()
    private val counts = mutableMapOf<String, Long>()

    fun logSystemInfo(context: Context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)
        val runtime = Runtime.getRuntime()
        Log.i(
            TAG,
            "SYSTEM device=${Build.MANUFACTURER}/${Build.MODEL} android=${Build.VERSION.RELEASE} " +
                "api=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()} " +
                "cpuCores=${runtime.availableProcessors()} ramMB=${memory.totalMem / 1_048_576} " +
                "heapMaxMB=${runtime.maxMemory() / 1_048_576}"
        )
    }

    @Synchronized
    fun begin(query: String): Long {
        val id = sequence.incrementAndGet()
        startedAt[id] = SystemClock.elapsedRealtimeNanos()
        queryStages[id] = linkedMapOf()
        Log.i(TAG, "QUERY_START id=$id text=${query.replace(Regex("\\s+"), " ").take(120)}")
        return id
    }

    @Synchronized
    fun record(id: Long, module: String, elapsedMs: Double) {
        queryStages.getOrPut(id) { linkedMapOf() }[module] = elapsedMs
        totals[module] = totals.getOrDefault(module, 0.0) + elapsedMs
        counts[module] = counts.getOrDefault(module, 0L) + 1L
        Log.i(TAG, "MODULE_OK id=$id module=$module timeMs=${fmt(elapsedMs)} avgMs=${fmt(average(module))} runs=${counts[module]}")
    }

    fun skipped(id: Long, module: String, reason: String) {
        Log.w(TAG, "MODULE_SKIPPED id=$id module=$module reason=$reason")
    }

    fun failed(id: Long, module: String, elapsedMs: Double, error: Throwable) {
        Log.e(
            TAG,
            "MODULE_FAIL id=$id module=$module timeMs=${fmt(elapsedMs)} " +
                "error=${error.javaClass.simpleName}:${error.message}"
        )
    }

    @Synchronized
    fun recordLifecycle(module: String, elapsedMs: Double) {
        totals[module] = totals.getOrDefault(module, 0.0) + elapsedMs
        counts[module] = counts.getOrDefault(module, 0L) + 1L
        Log.i(TAG, "LIFECYCLE_OK module=$module timeMs=${fmt(elapsedMs)} avgMs=${fmt(average(module))} runs=${counts[module]}")
    }

    @Synchronized
    fun finish(id: Long, success: Boolean) {
        val start = startedAt.remove(id) ?: return
        val totalMs = (SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0
        record(id, "TOTAL", totalMs)
        val stages = queryStages.remove(id).orEmpty().entries.joinToString(" ") { "${it.key}=${fmt(it.value)}ms" }
        val averages = totals.keys.sorted().joinToString(" ") { "$it=${fmt(average(it))}ms" }
        Log.i(TAG, "QUERY_END id=$id success=$success $stages")
        Log.i(TAG, "AVERAGES completed=${counts["TOTAL"] ?: 0} $averages")
    }

    fun elapsedMs(startNanos: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startNanos) / 1_000_000.0

    private fun average(module: String): Double =
        totals.getOrDefault(module, 0.0) / counts.getOrDefault(module, 1L).coerceAtLeast(1L)

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)
}
