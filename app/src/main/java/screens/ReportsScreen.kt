package com.example.myapplication.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.PharmacyViewModel
import com.example.myapplication.ui.components.DatePickerField // <--- تمت إضافة هذا الاستيراد
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: PharmacyViewModel) {
    var selectedReport by remember { mutableStateOf("monthly") }
    val reports = listOf(
        "monthly" to "المبيعات والأرباح",
        "movement" to "حركة الأصناف",
        "near" to "الصلاحيات القريبة",
        "expired" to "الأدوية المنتهية"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScrollableTabRow(
            selectedTabIndex = reports.indexOfFirst { it.first == selectedReport },
            containerColor = MaterialTheme.colorScheme.surface,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            reports.forEach { (key, title) ->
                Tab(
                    selected = selectedReport == key,
                    onClick = { selectedReport = key },
                    text = { Text(title, fontSize = 12.sp, fontWeight = if (selectedReport == key) FontWeight.Bold else FontWeight.Normal) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedReport) {
            "monthly" -> MonthlyReportUI(viewModel)
            "movement" -> MovementReportUI(viewModel)
            "near" -> NearExpiryReportUI(viewModel)
            "expired" -> ExpiredReportUI(viewModel)
        }
    }
}

@Composable
fun MonthlyReportUI(viewModel: PharmacyViewModel) {
    val stats by viewModel.monthlyReportStats.collectAsState()
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }
    val selectedMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(selectedDate))

    LaunchedEffect(selectedMonthStr) { viewModel.loadMonthlyReportStats(selectedMonthStr) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DatePickerField(
            label = "اختر الشهر لعرض التقرير",
            initialDate = selectedDate,
            onDateSelected = { selectedDate = it }
        )

        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // المبيعات
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("إجمالي المبيعات:", fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("${stats.totalSales}", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB), fontSize = 16.sp)
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)

                // الأرباح
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("إجمالي الأرباح:", fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("${stats.totalProfit}", fontWeight = FontWeight.Bold, color = Color(0xFF16A34A), fontSize = 16.sp)
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)

                // المصاريف
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("إجمالي المصاريف:", fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("${stats.totalExpenses}", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), fontSize = 16.sp)
                }
                HorizontalDivider(thickness = 1.dp, color = Color.DarkGray)

                // الصافي
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("صافي الربح:", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                    Text(
                        "${stats.netProfit}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = if (stats.netProfit >= 0) Color(0xFF16A34A) else Color.Red
                    )
                }
            }
        }

        if (stats.totalSales == 0.0 && stats.totalExpenses == 0.0) {
            Text("لا توجد حركات مسجلة في هذا الشهر.", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
fun MovementReportUI(viewModel: PharmacyViewModel) {
    val movementData by viewModel.movementReport.collectAsState()

    // 1. نستخدم Long لحفظ التاريخ (للتقويم)
    var selectedDate by remember { mutableStateOf(System.currentTimeMillis()) }

    // 2. نحول التاريخ إلى نص بصيغة "yyyy-MM" لكي يقرأه الـ ViewModel
    val selectedMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(selectedDate))

    // 3. نرسل النص للـ ViewModel عند كل تغيير
    LaunchedEffect(selectedMonthStr) { viewModel.loadMovementReport(selectedMonthStr) }

    Column {
        // 4. استبدلنا OutlinedTextField بـ DatePickerField
        DatePickerField(
            label = "اختر شهر التقرير",
            initialDate = selectedDate,
            onDateSelected = { newDate ->
                selectedDate = newDate
            }
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (movementData.isEmpty()) {
            Text("لا توجد مبيعات في هذا الشهر.", color = Color.Gray)
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("الصنف", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(2f))
                Text("الكمية", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("المبيعات", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
            }
            HorizontalDivider()
            LazyColumn {
                items(movementData) { item ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item.name, fontSize = 12.sp, modifier = Modifier.weight(2f))
                        Text("${item.qty}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("${item.sales}", fontSize = 12.sp, color = Color(0xFF2563EB), modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun NearExpiryReportUI(viewModel: PharmacyViewModel) {
    val nearExpiryData by viewModel.nearExpiryReport.collectAsState()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    LaunchedEffect(Unit) { viewModel.loadNearExpiryReport() }

    Column {
        Text("الدفعات التي تنتهي خلال 90 يوماً", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(12.dp))

        if (nearExpiryData.isEmpty()) {
            Text("لا توجد دفعات قريبة من الانتهاء.", color = Color.Gray)
        } else {
            LazyColumn {
                items(nearExpiryData) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("الصلاحية: ${dateFormat.format(Date(item.expiry))} (متبقي ${item.daysLeft} يوم)", fontSize = 12.sp, color = Color(0xFFE65100))
                            Text("الكمية: ${item.qty}", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpiredReportUI(viewModel: PharmacyViewModel) {
    val expiredData by viewModel.expiredReport.collectAsState()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // جلب البيانات عند فتح الشاشة
    LaunchedEffect(Unit) { viewModel.loadExpiredReport() }

    Column {
        Text("الأدوية المنتهية والخسائر", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Red)
        Spacer(modifier = Modifier.height(12.dp))

        if (expiredData.isEmpty()) {
            Text("لا توجد أدوية منتهية الصلاحية. ممتاز!", color = Color.Gray)
        } else {
            LazyColumn {
                items(expiredData) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)) // لون أحمر فاتح
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFB71C1C))
                            Text(
                                "الصلاحية: ${dateFormat.format(Date(item.expiry))} (منتهي منذ ${item.daysExpired} يوم)",
                                fontSize = 12.sp,
                                color = Color(0xFFD32F2F)
                            )
                            Text("الكمية التالفة: ${item.qty}", fontSize = 12.sp, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}