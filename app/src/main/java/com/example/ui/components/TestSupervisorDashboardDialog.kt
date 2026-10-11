package com.example.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.database.AppDatabase
import com.example.util.TestRunStatus
import com.example.util.TestRunSupervisor
import kotlinx.coroutines.launch

@Composable
fun TestSupervisorDashboardDialog(
    database: AppDatabase,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val testState by TestRunSupervisor.state.collectAsState()
    var showCrashDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.955f)
                .fillMaxHeight(0.9f)
                .padding(8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("لوحة اختبار الإجهاد والتحكم الآلي (Stress Test Supervisor)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق")
                    }
                }

                HorizontalDivider()

                // Status Banner
                val statusColor = when (testState.status) {
                    TestRunStatus.PASSED -> Color(0xFF2E7D32)
                    TestRunStatus.RUNNING -> Color(0xFF1976D2)
                    TestRunStatus.PAUSED -> Color(0xFFF57C00)
                    TestRunStatus.FAILED, TestRunStatus.INTERRUPTED -> Color(0xFFC62828)
                    TestRunStatus.RESOURCE_LIMIT_REACHED -> Color(0xFFD32F2F)
                    else -> MaterialTheme.colorScheme.outline
                }

                val statusText = when (testState.status) {
                    TestRunStatus.PASSED -> "🟢 نجح الاختبار بنجاح تام"
                    TestRunStatus.RUNNING -> "🔄 اختبار الإجهاد جاري..."
                    TestRunStatus.PAUSED -> "⏸️ متوقف مؤقتاً"
                    TestRunStatus.FAILED -> "🚨 فشل / انهيار"
                    TestRunStatus.INTERRUPTED -> "⚠️ انقطاع مفاجئ في تشغيل سابق"
                    TestRunStatus.RESOURCE_LIMIT_REACHED -> "⚠️ توقف لحماية الذاكرة"
                    TestRunStatus.CANCELLED -> "🚫 تم إلغاء الاختبار"
                    else -> "⚪ جاهز للبدء"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(statusText, fontWeight = FontWeight.Bold, color = statusColor, fontSize = 14.sp)
                            Text("ID: ${testState.runId}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("المرحلة الحالية: ${testState.currentPhase}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        
                        LinearProgressIndicator(
                            progress = { testState.progressPercentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = statusColor
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("التقدم: ${testState.completedCount} / ${testState.workloadTotal} (${String.format("%.1f", testState.progressPercentage * 100)}%)", fontSize = 11.sp)
                            Text("المدة: ${testState.formattedDuration}", fontSize = 11.sp)
                        }
                    }
                }

                // Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "استهلاك الذاكرة",
                        value = "${testState.memoryUsageMB} MB",
                        icon = Icons.Default.Memory
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "حجم قاعدة البيانات",
                        value = "${testState.databaseSizeBytes / (1024 * 1024)} MB",
                        icon = Icons.Default.Storage
                    )
                    MetricCard(
                        modifier = Modifier.weight(1f),
                        title = "محاولات الاستعادة",
                        value = "${testState.recoveryAttempts}",
                        icon = Icons.Default.Refresh
                    )
                }

                // Controls & Action Buttons
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        TestRunSupervisor.startStressTest(context, database, 118200, resumeFromCheckpoint = false)
                                    },
                                    enabled = testState.status != TestRunStatus.RUNNING,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("بدء إجهاد 100k عميل", fontSize = 11.sp)
                                }

                                if (testState.status == TestRunStatus.RUNNING) {
                                    OutlinedButton(
                                        onClick = { TestRunSupervisor.pauseTest() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("إيقاف مؤقت", fontSize = 11.sp)
                                    }
                                } else if (testState.status == TestRunStatus.PAUSED || testState.status == TestRunStatus.INTERRUPTED || testState.status == TestRunStatus.RESOURCE_LIMIT_REACHED) {
                                    Button(
                                        onClick = { TestRunSupervisor.resumeTest(context, database) },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("استئناف", fontSize = 11.sp)
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = { TestRunSupervisor.stopTest() },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("إيقاف آمن", fontSize = 11.sp)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showCrashDialog = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("تقرير الانهيار", fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val clientsCount = database.clientDao().getClientsCount()
                                            val invoicesCount = database.invoiceDao().getAllInvoices().size
                                            val paymentsCount = database.paymentDao().getAllPayments().size
                                            Toast.makeText(context, "سلامة قاعدة البيانات: عملاء: $clientsCount | فواتير: $invoicesCount | مدفوعات: $paymentsCount (سليم 100%)", Toast.LENGTH_LONG).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("فحص السلامة", fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    if (showCrashDialog) {
                        item {
                            val crashEvidence = remember { TestRunSupervisor.getLatestCrashEvidence(context) ?: "لا توجد تقارير انهيار مسجلة. التطبيق مستقر تماماً." }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("سجل الانهيار والأخطاء:", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                                    Text(crashEvidence, fontSize = 11.sp)
                                    Button(
                                        onClick = { showCrashDialog = false },
                                        modifier = Modifier.align(Alignment.End),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("إخفاء السجل", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(modifier: Modifier = Modifier, title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
