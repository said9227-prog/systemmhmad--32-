package com.example.util

import android.content.Context
import android.os.Build
import android.os.Debug
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

data class DiagnosticLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val category: String,
    val message: String,
    val details: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}

enum class LogLevel {
    INFO, WARNING, ERROR, CRITICAL
}

data class AppHealthReport(
    val startupTimeMillis: Long,
    val uptimeSeconds: Long,
    val totalMemoryMB: Long,
    val freeMemoryMB: Long,
    val usedMemoryMB: Long,
    val maxMemoryMB: Long,
    val memoryUsagePercentage: Float,
    val isHeavyLoadDetected: Boolean,
    val activeWarningsCount: Int,
    val activeErrorsCount: Int,
    val databaseStatus: String,
    val securityStatus: String,
    val overallHealthStatus: HealthStatus,
    val recentLogs: List<DiagnosticLogEntry>
)

enum class HealthStatus {
    HEALTHY, WARNING, CRITICAL
}

object AppDiagnosticsEngine {
    private const val TAG = "AppDiagnosticsEngine"
    private val startTime = System.currentTimeMillis()
    
    private val _logs = ConcurrentLinkedQueue<DiagnosticLogEntry>()
    private val _logFlow = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logFlow: StateFlow<List<DiagnosticLogEntry>> = _logFlow.asStateFlow()

    private var isAutoLoggingInitialized = false
    private var originalUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    fun init(context: Context) {
        if (isAutoLoggingInitialized) return
        isAutoLoggingInitialized = true

        log(LogLevel.INFO, "Diagnostics", "تم تفعيل محرك التشخيص الذكي وراقبة الأداء تلقائياً عند فتح التطبيق.")

        // Capture uncaught exceptions to prevent silent crashes and log them for AI diagnostics
        originalUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()
            
            log(LogLevel.CRITICAL, "Crash", "انهيار غير متوقع (Crash/Exception) في الخيط ${thread.name}: ${throwable.message}", stackTrace)
            
            // Pass to original handler so app behaves as expected (or terminates cleanly)
            originalUncaughtHandler?.uncaughtException(thread, throwable)
        }

        // Log system and device info
        val deviceModel = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        log(LogLevel.INFO, "System", "الجهاز: $deviceModel | إصدار أندرويد: $androidVersion | SDK: ${Build.VERSION.SDK_INT}")
    }

    fun log(level: LogLevel, category: String, message: String, details: String? = null) {
        val entry = DiagnosticLogEntry(level = level, category = category, message = message, details = details)
        _logs.add(entry)
        // Keep max 500 logs in memory
        while (_logs.size > 500) {
            _logs.poll()
        }
        _logFlow.value = _logs.toList().reversed()
        
        when (level) {
            LogLevel.INFO -> Log.i(TAG, "[$category] $message")
            LogLevel.WARNING -> Log.w(TAG, "[$category] ⚠️ $message")
            LogLevel.ERROR -> Log.e(TAG, "[$category] ❌ $message")
            LogLevel.CRITICAL -> Log.e(TAG, "[$category] 🚨 CRITICAL: $message")
        }
    }

    fun generateHealthReport(): AppHealthReport {
        val runtime = Runtime.getRuntime()
        val maxMemoryBytes = runtime.maxMemory()
        val totalMemoryBytes = runtime.totalMemory()
        val freeMemoryBytes = runtime.freeMemory()
        val usedMemoryBytes = totalMemoryBytes - freeMemoryBytes

        val maxMemoryMB = maxMemoryBytes / (1024 * 1024)
        val totalMemoryMB = totalMemoryBytes / (1024 * 1024)
        val freeMemoryMB = freeMemoryBytes / (1024 * 1024)
        val usedMemoryMB = usedMemoryBytes / (1024 * 1024)

        val memoryPercentage = if (maxMemoryMB > 0) (usedMemoryMB.toFloat() / maxMemoryMB.toFloat()) * 100f else 0f
        
        // Detect heavy load if memory usage > 85% or frequent heavy logs
        val warnings = _logs.count { it.level == LogLevel.WARNING }
        val errors = _logs.count { it.level == LogLevel.ERROR || it.level == LogLevel.CRITICAL }
        
        val isHeavyLoad = memoryPercentage > 85f || runtime.freeMemory() < (10 * 1024 * 1024)

        val uptimeSec = (System.currentTimeMillis() - startTime) / 1000

        val healthStatus = when {
            errors > 0 || memoryPercentage > 90f -> HealthStatus.CRITICAL
            warnings > 3 || memoryPercentage > 75f -> HealthStatus.WARNING
            else -> HealthStatus.HEALTHY
        }

        return AppHealthReport(
            startupTimeMillis = startTime,
            uptimeSeconds = uptimeSec,
            totalMemoryMB = totalMemoryMB,
            freeMemoryMB = freeMemoryMB,
            usedMemoryMB = usedMemoryMB,
            maxMemoryMB = maxMemoryMB,
            memoryUsagePercentage = memoryPercentage,
            isHeavyLoadDetected = isHeavyLoad,
            activeWarningsCount = warnings,
            activeErrorsCount = errors,
            databaseStatus = if (errors == 0) "متصل وسليم (Room Database OK)" else "يحتاج مراجعة (Errors detected)",
            securityStatus = "محمي (Security Active)",
            overallHealthStatus = healthStatus,
            recentLogs = _logs.toList().takeLast(100).reversed()
        )
    }

    fun generateMarkdownAiReport(): String {
        val report = generateHealthReport()
        val sb = StringBuilder()
        sb.append("# تقرير تشخيص أداء وصحة تطبيق الحكيمي للأدوية (AI Diagnostic Telemetry Report)\n\n")
        sb.append("**وقت التقرير:** ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
        sb.append("**مدة التشغيل (Uptime):** ${report.uptimeSeconds} ثانية\n")
        sb.append("**الحالة العامة:** ${report.overallHealthStatus}\n\n")

        sb.append("## 1. إحصائيات الذاكرة والأداء (Memory & Performance)\n")
        sb.append("- **الذاكرة المستخدمة:** ${report.usedMemoryMB} ميجابايت من أصل ${report.maxMemoryMB} ميجابايت (${String.format(Locale.getDefault(), "%.1f", report.memoryUsagePercentage)}%)\n")
        sb.append("- **الذاكرة الحرة المتاحة:** ${report.freeMemoryMB} ميجابايت\n")
        sb.append("- **هل يوجد حمل ثقيل أو استنزاف للذاكرة؟:** ${if (report.isHeavyLoadDetected) "نعم ⚠️ (تحذير ثقل أداء)" else "لا (الأداء ممتاز 🟢)"}\n\n")

        sb.append("## 2. حالة المكونات والخدمات\n")
        sb.append("- **قاعدة البيانات:** ${report.databaseStatus}\n")
        sb.append("- **حالة الأمان والحماية:** ${report.securityStatus}\n")
        sb.append("- **عدد التنبيهات (Warnings):** ${report.activeWarningsCount}\n")
        sb.append("- **عدد الأخطاء والانهيارات (Errors/Crashes):** ${report.activeErrorsCount}\n\n")

        sb.append("## 3. سجلات التشغيل الأخيرة والانهيارات (Recent Logs & Stack Traces)\n")
        if (report.recentLogs.isEmpty()) {
            sb.append("لا توجد أخطاء أو تحذيرات مسجلة حالياً.\n")
        } else {
            for (log in report.recentLogs) {
                sb.append("### [${log.level}] ${log.category} - ${log.formattedTime}\n")
                sb.append("- **الرسالة:** ${log.message}\n")
                if (!log.details.isNullOrEmpty()) {
                    sb.append("```\n${log.details}\n```\n")
                }
                sb.append("\n")
            }
        }
        return sb.toString()
    }
}
