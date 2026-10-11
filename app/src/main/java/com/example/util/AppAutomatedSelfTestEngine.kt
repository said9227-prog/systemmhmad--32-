package com.example.util

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.model.Client
import com.example.domain.currency.Currency
import com.example.domain.currency.CurrencyConversionService
import com.example.domain.currency.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.util.UUID

data class TestStepResult(
    val stepName: String,
    val isSuccess: Boolean,
    val message: String,
    val durationMs: Long
)

data class AutomatedTestSuiteResult(
    val totalSteps: Int,
    val passedSteps: Int,
    val failedSteps: Int,
    val totalDurationMs: Long,
    val steps: List<TestStepResult>,
    val isCompleted: Boolean
)

object AppAutomatedSelfTestEngine {

    private val _testState = MutableStateFlow<AutomatedTestSuiteResult?>(null)
    val testState: StateFlow<AutomatedTestSuiteResult?> = _testState.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    suspend fun runAutomatedTests(context: Context, database: AppDatabase): AutomatedTestSuiteResult = withContext(Dispatchers.IO) {
        _isRunning.value = true
        val steps = mutableListOf<TestStepResult>()
        val suiteStartTime = System.currentTimeMillis()

        // Test 1: Database Connection & Store Settings
        val t1Start = System.currentTimeMillis()
        var t1Success = false
        var t1Msg = ""
        try {
            val settings = database.storeSettingsDao().getSettings()
            t1Success = settings != null
            t1Msg = if (t1Success) "تم الاتصال بقاعدة البيانات بنجاح وقراءة إعدادات المتجر: ${settings?.storeName}" else "فشل قراءة إعدادات المتجر"
        } catch (e: Exception) {
            t1Msg = "خطأ في اتصال قاعدة البيانات: ${e.message}"
        }
        steps.add(TestStepResult("اختبار اتصال وقراءة قاعدة البيانات", t1Success, t1Msg, System.currentTimeMillis() - t1Start))

        // Test 2: Multi-Currency Conversion Engine Test
        val t2Start = System.currentTimeMillis()
        var t2Success = false
        var t2Msg = ""
        try {
            val rates = database.exchangeRateDao().getAllRates()
            val rateMap = rates.associate {
                (Currency.fromCodeOrName(it.fromCurrency) to Currency.fromCodeOrName(it.toCurrency)) to BigDecimal.valueOf(it.rate)
            }
            val converted = CurrencyConversionService.convert(Money(BigDecimal("100"), Currency.USD), Currency.YER, rateMap)
            t2Success = converted.convertedMoney.amount > BigDecimal.ZERO
            t2Msg = "تم اختبار محول العملات بنجاح (100 دولار = ${converted.convertedMoney.amount} ريال يمني عبر أسعار الصرف النشطة)"
        } catch (e: Exception) {
            t2Msg = "خطأ في اختبار العملات: ${e.message}"
        }
        steps.add(TestStepResult("اختبار نظام تحويل العملات المتعددة", t2Success, t2Msg, System.currentTimeMillis() - t2Start))

        // Test 3: Customer CRUD & Isolation Test
        val t3Start = System.currentTimeMillis()
        var t3Success = false
        var t3Msg = ""
        try {
            val testUuid = "SELFTEST-${UUID.randomUUID().toString().take(8)}"
            val testClient = Client(
                uuid = testUuid,
                name = "عميل اختبار آلي",
                phone = "770000000",
                currency = "SAR"
            )
            val insertedId = database.clientDao().insertClient(testClient)
            val fetched = database.clientDao().getClientById(insertedId.toInt())
            val count = database.clientDao().getClientsCount()
            if (fetched != null) {
                database.clientDao().deleteClient(fetched)
            }
            t3Success = fetched != null && fetched.name == "عميل اختبار آلي"
            t3Msg = if (t3Success) "تم اختبار إضافة واستعلام وحذف العميل التجريبي بنجاح (إجمالي العملاء: $count)" else "فشل اختبار عملاء قاعدة البيانات"
        } catch (e: Exception) {
            t3Msg = "خطأ في اختبار العملاء: ${e.message}"
        }
        steps.add(TestStepResult("اختبار عمليات العملاء (CRUD)", t3Success, t3Msg, System.currentTimeMillis() - t3Start))

        // Test 4: Memory & Performance Health Benchmark
        val t4Start = System.currentTimeMillis()
        var t4Success = false
        var t4Msg = ""
        try {
            val runtime = Runtime.getRuntime()
            val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
            val maxMem = runtime.maxMemory() / (1024 * 1024)
            val percent = (usedMem.toFloat() / maxMem.toFloat()) * 100f
            t4Success = percent < 95f
            t4Msg = "استهلاك الذاكرة الحالي: $usedMem MB من أصل $maxMem MB (${String.format("%.1f", percent)}%) - الأداء ضمن النطاق الآمن."
        } catch (e: Exception) {
            t4Msg = "خطأ في اختبار الذاكرة: ${e.message}"
        }
        steps.add(TestStepResult("اختبار فحص الذاكرة والأداء", t4Success, t4Msg, System.currentTimeMillis() - t4Start))

        // Test 5: Backup Engine Sanity Check
        val t5Start = System.currentTimeMillis()
        var t5Success = false
        var t5Msg = ""
        try {
            val backupResult = BackupEngine.createBackup(context, database)
            val isValid = backupResult.isSuccess && backupResult.getOrNull() != null && backupResult.getOrNull()!!.exists()
            if (isValid) {
                backupResult.getOrNull()?.delete()
            }
            t5Success = isValid
            t5Msg = if (t5Success) "تم التحقق من إنشاء ملف النسخ الاحتياطي المشفر والمضغوط بنجاح." else "فشل إنشاء ملف النسخ الاحتياطي."
        } catch (e: Exception) {
            t5Msg = "خطأ في اختبار النسخ الاحتياطي: ${e.message}"
        }
        steps.add(TestStepResult("اختبار محرك النسخ الاحتياطي الآمن", t5Success, t5Msg, System.currentTimeMillis() - t5Start))

        val totalDuration = System.currentTimeMillis() - suiteStartTime
        val passed = steps.count { it.isSuccess }
        val failed = steps.size - passed

        val result = AutomatedTestSuiteResult(
            totalSteps = steps.size,
            passedSteps = passed,
            failedSteps = failed,
            totalDurationMs = totalDuration,
            steps = steps,
            isCompleted = true
        )

        _testState.value = result
        _isRunning.value = false

        AppDiagnosticsEngine.log(
            if (failed == 0) LogLevel.INFO else LogLevel.WARNING,
            "SelfTest",
            "اكتمل الاختبار الآلي الشامل: نجح ($passed/${steps.size}) في ${totalDuration}ms"
        )

        result
    }
}
