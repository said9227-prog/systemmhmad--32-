package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.viewmodel.AppViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkScaleStressDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isRunning by viewModel.isBulkScaleInProgress.collectAsState()
    val progress by viewModel.bulkScaleProgress.collectAsState()
    val stage by viewModel.bulkScaleStage.collectAsState()
    val currentCount by viewModel.bulkScaleCurrentCount.collectAsState()
    val totalCount by viewModel.bulkScaleTotalCount.collectAsState()
    val speed by viewModel.bulkScaleSpeed.collectAsState()
    val resultMessage by viewModel.bulkScaleResult.collectAsState()

    val totalClients by viewModel.totalClientsCount.collectAsState()
    val totalInvoices by viewModel.totalInvoicesCount.collectAsState()
    val totalItems by viewModel.totalItemsCount.collectAsState()
    val totalPayments by viewModel.totalPaymentsCount.collectAsState()

    var selectedScaleOption by remember { mutableStateOf(100000) }
    var showConfirmCleanDialog by remember { mutableStateOf(false) }

    val numFmt = remember { NumberFormat.getNumberInstance(Locale.US) }

    Dialog(
        onDismissRequest = {
            if (!isRunning) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isRunning,
            dismissOnClickOutside = !isRunning,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .testTag("bulk_scale_stress_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "فحص سعة وقدرة التطبيق القصوى",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "إضافة 100,000 عميل مع فواتير وسداد وأصناف",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (!isRunning) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Current Database Statistics
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "📊 إحصائيات السجلات في النظام حالياً:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                StatBox(label = "العملاء", count = numFmt.format(totalClients), color = Color(0xFF2563EB))
                                StatBox(label = "الأصناف", count = numFmt.format(totalItems), color = Color(0xFF059669))
                                StatBox(label = "الفواتير", count = numFmt.format(totalInvoices), color = Color(0xFFD97706))
                                StatBox(label = "سندات السداد", count = numFmt.format(totalPayments), color = Color(0xFF7C3AED))
                            }
                        }
                    }

                    // Target Scale Selection (when not running)
                    if (!isRunning) {
                        Text(
                            text = "اختر حجم البيانات المطلوب إضافتها واختبارها:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )

                        val options = listOf(
                            100000 to "100,000 عميل (مع 10,000 فاتورة، 5,000 سداد، 2,000 مشتريات، 1,000 مرتجع، 500 صنف - سعودي ويمني ودولار)",
                            50000 to "50,000 عميل (مع 5,000 فاتورة، 2,500 سداد، 1,000 مشتريات، 500 مرتجع، 300 صنف)",
                            10000 to "10,000 عميل (مع 2,000 فاتورة، 1,000 سداد، 500 مشتريات، 250 مرتجع، 150 صنف)",
                            1000 to "1,000 عميل (تجربة سريعة مع فواتير ومشتريات وسداد ومرتجعات بالريال السعودي واليمني والدولار)"
                        )

                        options.forEach { (count, label) ->
                            val isSelected = selectedScaleOption == count
                            Card(
                                onClick = { selectedScaleOption = count },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedScaleOption = count }
                                    )
                                    Column {
                                        Text(
                                            text = label,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp
                                        )
                                        if (count == 100000) {
                                            Text(
                                                text = "🔥 الحجم المستهدف الكامل لاختبار أعلى أداء لقاعدة البيانات والواجهة",
                                                fontSize = 11.sp,
                                                color = Color(0xFFEA580C)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Progress Section (when running)
                    AnimatedVisibility(visible = isRunning) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                                        Text(stage, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Text(
                                        text = "${(progress * 100).toInt()}%",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "المنجز: ${numFmt.format(currentCount)} من ${numFmt.format(totalCount)}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (speed.isNotBlank()) {
                                        Text(
                                            text = "السرعة: $speed",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF059669)
                                        )
                                    }
                                }

                                Button(
                                    onClick = { viewModel.cancelBulkScaleGeneration() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("إيقاف العملية بأمان")
                                }
                            }
                        }
                    }

                    // Result Message Banner
                    if (resultMessage != null && !isRunning) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981))
                                Text(
                                    text = resultMessage!!,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF065F46)
                                )
                            }
                        }
                    }

                    // Technical Info & Safety
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(
                                text = "تستخدم الأداة تقنية التدفق الدفعي (Streaming Chunks) عبر معاملات SQLite السريعة لمنع استهلاك الذاكرة وحماية سرعة وتصفح التطبيق.",
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isRunning) {
                        Button(
                            onClick = {
                                val prodCount = when (selectedScaleOption) {
                                    100000 -> 500
                                    50000 -> 300
                                    10000 -> 150
                                    else -> 50
                                }
                                val invCount = when (selectedScaleOption) {
                                    100000 -> 10000
                                    50000 -> 5000
                                    10000 -> 2000
                                    else -> 500
                                }
                                val purCount = when (selectedScaleOption) {
                                    100000 -> 2000
                                    50000 -> 1000
                                    10000 -> 500
                                    else -> 100
                                }
                                val payCount = when (selectedScaleOption) {
                                    100000 -> 5000
                                    50000 -> 2500
                                    10000 -> 1000
                                    else -> 250
                                }
                                val retCount = when (selectedScaleOption) {
                                    100000 -> 1000
                                    50000 -> 500
                                    10000 -> 250
                                    else -> 50
                                }
                                viewModel.startBulkScaleGeneration(
                                    targetCustomers = selectedScaleOption,
                                    targetProducts = prodCount,
                                    targetInvoices = invCount,
                                    targetPurchases = purCount,
                                    targetPayments = payCount,
                                    targetReturns = retCount
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("start_bulk_scale_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("بدء الإضافة داخل التطبيق", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showConfirmCleanDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("مسح البيانات التجريبية", fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.cancelBulkScaleGeneration() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("إلغاء العملية")
                        }
                    }
                }
            }
        }
    }

    if (showConfirmCleanDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmCleanDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text("مسح السجلات التجريبية", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text("هل تريد بالتأكيد حذف كافة العملاء والفواتير والأصناف التجريبية المولدة تلقائياً؟ لن يؤثر هذا على أية بيانات حقيقية قمت بإدخالها يدوياً.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmCleanDialog = false
                        viewModel.cleanSyntheticScaleData { deletedCount ->
                            Toast.makeText(context, "تم مسح $deletedCount سجلاً تجريبياً بنجاح", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("نعم، مسح البيانات التجريبية")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmCleanDialog = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
private fun StatBox(
    label: String,
    count: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = count,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = color
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
