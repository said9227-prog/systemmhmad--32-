package com.example.util

import android.content.Context
import androidx.work.*
import com.example.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class InstallmentRescheduleWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            InstallmentManager.rescheduleAllPendingReminders(context)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "InstallmentReschedulePeriodicWork"
        const val WORK_NAME_ONE_TIME = "InstallmentRescheduleOneTimeWork"

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<InstallmentRescheduleWorker>(
                1, TimeUnit.DAYS,
                1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun enqueueOneTime(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<InstallmentRescheduleWorker>()
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        suspend fun enqueueIfRemindersExist(context: Context) = withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context)
                val settings = db.storeSettingsDao().getSettings()
                val hasGeneral = settings?.isGeneralInstallmentReminderEnabled == true
                val countUnhandled = db.installmentReminderDao().getUnhandledReminders().size
                val now = System.currentTimeMillis()
                val pendingInstallments = db.installmentDao().getAllInstallments().count { !it.isPaid && it.dueDate > now }

                if (hasGeneral || countUnhandled > 0 || pendingInstallments > 0) {
                    enqueuePeriodic(context)
                    enqueueOneTime(context)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
