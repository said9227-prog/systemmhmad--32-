package com.example.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.data.database.AppDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

enum class TestRunStatus {
    IDLE, RUNNING, PAUSED, RECOVERING, PASSED, FAILED, INTERRUPTED, CANCELLED, RESOURCE_LIMIT_REACHED
}

data class TestCheckpointState(
    val runId: String = "",
    val workloadTotal: Int = 100000,
    val completedCount: Int = 0,
    val currentPhase: String = "جاهز للاختبار",
    val status: TestRunStatus = TestRunStatus.IDLE,
    val startTime: Long = 0L,
    val lastCheckpointTime: Long = 0L,
    val lastCommittedId: String = "",
    val errorStack: String? = null,
    val memoryUsageMB: Long = 0L,
    val databaseSizeBytes: Long = 0L,
    val recoveryAttempts: Int = 0,
    val isInterruptedFromPreviousRun: Boolean = false
) {
    val progressPercentage: Float
        get() = if (workloadTotal > 0) (completedCount.toFloat() / workloadTotal.toFloat()).coerceIn(0f, 1f) else 0f
    
    val formattedDuration: String
        get() {
            if (startTime == 0L) return "0 ثانية"
            val durationSec = (System.currentTimeMillis() - startTime) / 1000
            val mins = durationSec / 60
            val secs = durationSec % 60
            return if (mins > 0) "$mins دقيقة و $secs ثانية" else "$secs ثانية"
        }
}

object TestRunSupervisor {
    private const val TAG = "TestRunSupervisor"
    private const val CHECKPOINT_FILE_NAME = "test_run_checkpoint_v2.json"
    private const val CRASH_FILE_NAME = "test_crash_evidence_v2.json"

    private val _state = MutableStateFlow(TestCheckpointState())
    val state: StateFlow<TestCheckpointState> = _state.asStateFlow()

    private var testJob: Job? = null
    private val supervisorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        loadCheckpoint(context)
        checkPreviousRunTermination(context)
    }

    private fun getCheckpointFile(context: Context): File = File(context.filesDir, CHECKPOINT_FILE_NAME)
    private fun getCrashFile(context: Context): File = File(context.filesDir, CRASH_FILE_NAME)

    @Synchronized
    fun saveCheckpoint(context: Context, currentState: TestCheckpointState) {
        try {
            val json = JSONObject().apply {
                put("runId", currentState.runId)
                put("workloadTotal", currentState.workloadTotal)
                put("completedCount", currentState.completedCount)
                put("currentPhase", currentState.currentPhase)
                put("status", currentState.status.name)
                put("startTime", currentState.startTime)
                put("lastCheckpointTime", System.currentTimeMillis())
                put("lastCommittedId", currentState.lastCommittedId)
                put("errorStack", currentState.errorStack ?: "")
                put("memoryUsageMB", currentState.memoryUsageMB)
                put("databaseSizeBytes", currentState.databaseSizeBytes)
                put("recoveryAttempts", currentState.recoveryAttempts)
                put("isInterruptedFromPreviousRun", currentState.isInterruptedFromPreviousRun)
            }
            val file = getCheckpointFile(context)
            val tempFile = File(context.filesDir, "$CHECKPOINT_FILE_NAME.tmp")
            tempFile.writeText(json.toString())
            if (file.exists()) file.delete()
            tempFile.renameTo(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save checkpoint: ${e.message}", e)
        }
    }

    fun loadCheckpoint(context: Context) {
        try {
            val file = getCheckpointFile(context)
            if (!file.exists()) return
            val json = JSONObject(file.readText())
            val savedStatus = try { TestRunStatus.valueOf(json.optString("status", "IDLE")) } catch (_: Exception) { TestRunStatus.IDLE }
            
            val loadedState = TestCheckpointState(
                runId = json.optString("runId", UUID.randomUUID().toString().uppercase().take(8)),
                workloadTotal = json.optInt("workloadTotal", 118200),
                completedCount = json.optInt("completedCount", 0),
                currentPhase = json.optString("currentPhase", "متوقف مؤقتاً عند نقطة حفظ"),
                status = if (savedStatus == TestRunStatus.RUNNING) TestRunStatus.INTERRUPTED else savedStatus,
                startTime = json.optLong("startTime", System.currentTimeMillis()),
                lastCheckpointTime = json.optLong("lastCheckpointTime", System.currentTimeMillis()),
                lastCommittedId = json.optString("lastCommittedId", ""),
                errorStack = json.optString("errorStack", "").takeIf { it.isNotEmpty() },
                memoryUsageMB = json.optLong("memoryUsageMB", 0L),
                databaseSizeBytes = json.optLong("databaseSizeBytes", 0L),
                recoveryAttempts = json.optInt("recoveryAttempts", 0),
                isInterruptedFromPreviousRun = savedStatus == TestRunStatus.RUNNING
            )
            _state.value = loadedState
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load checkpoint: ${e.message}", e)
        }
    }

    private fun checkPreviousRunTermination(context: Context) {
        val curr = _state.value
        if (curr.status == TestRunStatus.INTERRUPTED || curr.isInterruptedFromPreviousRun) {
            AppDiagnosticsEngine.log(
                LogLevel.WARNING,
                "TestSupervisor",
                "⚠️ اكتشاف انقطاع مفاجئ في تشغيل سابق (Process Termination / Kill). آخر نقطة حفظ: ${curr.completedCount}/${curr.workloadTotal} (${curr.currentPhase})"
            )
        }
    }

    fun persistCrashEvidence(context: Context, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stack = sw.toString()

            val json = JSONObject().apply {
                put("timestamp", System.currentTimeMillis())
                put("exception", throwable.javaClass.name)
                put("message", throwable.message ?: "Unknown error")
                put("stackTrace", stack)
                put("runId", _state.value.runId)
                put("completedCount", _state.value.completedCount)
                put("phase", _state.value.currentPhase)
            }
            val file = getCrashFile(context)
            file.writeText(json.toString())

            val updated = _state.value.copy(
                status = TestRunStatus.FAILED,
                errorStack = stack,
                currentPhase = "فشل بسبب انهيار: ${throwable.message}"
            )
            _state.value = updated
            saveCheckpoint(context, updated)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist crash evidence: ${e.message}", e)
        }
    }

    fun getLatestCrashEvidence(context: Context): String? {
        try {
            val file = getCrashFile(context)
            if (!file.exists()) return null
            val json = JSONObject(file.readText())
            return buildString {
                append("🚨 تقرير الانهيار الأخير (Crash Evidence Report)\n")
                append("--------------------------------------------------\n")
                append("التوقيت: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(json.optLong("timestamp")))}\n")
                append("معرف التشغيل: ${json.optString("runId")}\n")
                append("نوع الاستثناء: ${json.optString("exception")}\n")
                append("الرسالة: ${json.optString("message")}\n")
                append("المرحلة التي حدث بها الانقطاع: ${json.optString("phase")} (العمليات المكتملة: ${json.optInt("completedCount")})\n\n")
                append("تتبع الأخطاء (Stack Trace):\n${json.optString("stackTrace")}\n")
            }
        } catch (e: Exception) {
            return "خطأ في قراءة تقرير الانهيار: ${e.message}"
        }
    }

    fun startStressTest(
        context: Context,
        database: AppDatabase,
        targetWorkload: Int = 118200,
        resumeFromCheckpoint: Boolean = false
    ) {
        if (_state.value.status == TestRunStatus.RUNNING) return

        val runId = if (resumeFromCheckpoint && _state.value.runId.isNotEmpty()) _state.value.runId else "RUN-${UUID.randomUUID().toString().uppercase().take(8)}"
        val startCompleted = if (resumeFromCheckpoint) _state.value.completedCount else 0

        val initialState = _state.value.copy(
            runId = runId,
            workloadTotal = targetWorkload,
            completedCount = startCompleted,
            status = TestRunStatus.RUNNING,
            currentPhase = if (resumeFromCheckpoint) "استئناف الاختبار الآلي ($startCompleted)" else "بدء اختبار الإجهاد الشامل (100,000 عميل + فواتير + مدفوعات)",
            startTime = if (resumeFromCheckpoint && _state.value.startTime > 0) _state.value.startTime else System.currentTimeMillis(),
            errorStack = null
        )
        _state.value = initialState
        saveCheckpoint(context, initialState)

        testJob?.cancel()
        testJob = supervisorScope.launch {
            try {
                // Execute BulkScaleEngine with robust progress callback and checkpointing
                val result = BulkScaleEngine.executeScaleInsertion(
                    db = database,
                    targetCustomers = 100000,
                    targetProducts = 500,
                    targetInvoices = 10000,
                    targetPurchases = 2000,
                    targetPayments = 5000,
                    targetReturns = 700,
                    onProgress = { progress, stage, current, total, speed ->
                        val runtime = Runtime.getRuntime()
                        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                        val dbFile = context.getDatabasePath("client_accounts_pro_db")
                        val dbSize = if (dbFile.exists()) dbFile.length() else 0L

                        val updated = _state.value.copy(
                            completedCount = current,
                            currentPhase = stage,
                            status = TestRunStatus.RUNNING,
                            lastCheckpointTime = System.currentTimeMillis(),
                            lastCommittedId = "OP-$current",
                            memoryUsageMB = usedMem,
                            databaseSizeBytes = dbSize
                        )
                        _state.value = updated
                        if (current % 2500 == 0) {
                            saveCheckpoint(context, updated)
                        }
                    }
                )

                if (result.isSuccess) {
                    val finalState = _state.value.copy(
                        status = TestRunStatus.PASSED,
                        currentPhase = "اكتمل اختبار الإجهاد بنجاح تام 100% 🟢 (${result.getOrNull()})",
                        completedCount = targetWorkload,
                        lastCheckpointTime = System.currentTimeMillis()
                    )
                    _state.value = finalState
                    saveCheckpoint(context, finalState)
                    AppDiagnosticsEngine.log(LogLevel.INFO, "TestSupervisor", "🟢 نجح اختبار الإجهاد بنجاح تام.")
                } else {
                    val ex = result.exceptionOrNull() ?: Exception("Unknown error in bulk scale execution")
                    throw ex
                }

            } catch (e: CancellationException) {
                val pausedState = _state.value.copy(status = TestRunStatus.PAUSED, currentPhase = "تم إيقاف الاختبار مؤقتاً بواسطة المستخدم")
                _state.value = pausedState
                saveCheckpoint(context, pausedState)
            } catch (e: Throwable) {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val errStack = sw.toString()

                persistCrashEvidence(context, e)
                AppDiagnosticsEngine.log(LogLevel.CRITICAL, "TestSupervisor", "🚨 فشل اختبار الإجهاد: ${e.message}", errStack)
            }
        }
    }

    fun pauseTest() {
        testJob?.cancel()
        val paused = _state.value.copy(status = TestRunStatus.PAUSED, currentPhase = "تم إيقاف الاختبار مؤقتاً")
        _state.value = paused
    }

    fun resumeTest(context: Context, database: AppDatabase) {
        if (_state.value.status == TestRunStatus.PAUSED || _state.value.status == TestRunStatus.INTERRUPTED || _state.value.status == TestRunStatus.RESOURCE_LIMIT_REACHED) {
            startStressTest(context, database, _state.value.workloadTotal, resumeFromCheckpoint = true)
        }
    }

    fun stopTest() {
        testJob?.cancel()
        val stopped = _state.value.copy(status = TestRunStatus.CANCELLED, currentPhase = "تم إلغاء الاختبار بأمان")
        _state.value = stopped
    }
}
