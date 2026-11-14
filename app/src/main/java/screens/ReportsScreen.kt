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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: PharmacyViewModel) {
    // التبويبات الجديدة
    val reports = listOf(
        "monthly" to "المالية",
        "movement" to "الحركة",
        "near" to "قريبة الانتهاء",
        "expired" to "المنتهية",
        "top_heroes" to "الأبطال",
        "suppliers" to "الموردين"
    )
    var selectedReport by remember { mutableStateOf("monthly") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        ScrollableTabRow(
            selectedTabIndex = reports.indexOfFirst { it.first == selectedReport },
            containerColor = MaterialTheme.colorScheme.surface,
            edgePadding = 0.dp,
            modifier = Modifier.fillMaxWidth(),
            divider = {}
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
            "top_heroes" -> TopHeroesReportUI(viewModel)
            "suppliers" -> SupplierReportUI(viewModel)
        }
    }
}

@Composable
fun MonthlyReportUI(viewModel: PharmacyViewModel) {
    val stats by viewModel.monthlyReportStats.collectAsState()
    val selectedMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())

    LaunchedEffect(Unit) { viewModel.loadMonthlyReportStats(selectedMonthStr) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("تقرير الشهر الحالي (${selectedMonthStr})", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("إجمالي المبيعات", fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("${stats.totalSales}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                }
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("إجمالي الأرباح", fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("${stats.totalProfit}", fontWeight = FontWeight.Bold, color = Color(0xFF3D956B), fontSize = 16.sp)
                }
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("إجمالي المصاريف", fontWeight = FontWeight.Bold, color = Color.Gray)
                    Text("${stats.totalExpenses}", fontWeight = FontWeight.Bold, color = Color(0xFFD65353), fontSize = 16.sp)
                }
                HorizontalDivider(color = Color.DarkGray)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("صافي الربح", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${stats.netProfit}", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = if (stats.netProfit >= 0) Color(0xFF3D956B) else Color.Red)
                }
            }
        }
    }
}

@Composable
fun MovementReportUI(viewModel: PharmacyViewModel) {
    val movementData by viewModel.movementReport.collectAsState()
    val selectedMonthStr = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    LaunchedEffect(Unit) { viewModel.loadMovementReport(selectedMonthStr) }

    Column {
        Text("حركة الأصناف (الشهر الحالي)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))

        if (movementData.isEmpty()) {
            Text("لا توجد حركة في هذا الشهر.", color = Color.Gray)
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
                        Text("${item.sales}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.3f))
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
        Text("الدفعات التي تنتهي خلال 90 يوماً", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(12.dp))

        if (nearExpiryData.isEmpty()) {
            Text("لا توجد دفعات قريبة من الانتهاء.", color = Color.Gray)
        } else {
            LazyColumn {
                items(nearExpiryData) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("الصلاحية: ${dateFormat.format(Date(item.expiry))} (متبقي ${item.daysLeft} يوم)", fontSize = 12.sp, color = Color(0xFFE8A23A))
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
    LaunchedEffect(Unit) { viewModel.loadExpiredReport() }

    Column {
        Text("الأدوية المنتهية والخسائر", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFD65353))
        Spacer(modifier = Modifier.height(12.dp))

        if (expiredData.isEmpty()) {
            Text("لا توجد أدوية منتهية الصلاحية. ممتاز!", color = Color.Gray)
        } else {
            LazyColumn {
                items(expiredData) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFD65353))
                            Text("الصلاحية: ${dateFormat.format(Date(item.expiry))} (منتهي منذ ${item.daysExpired} يوم)", fontSize = 12.sp, color = Color(0xFFD65353))
                            Text("الكمية التالفة: ${item.qty}", fontSize = 12.sp, color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TopHeroesReportUI(viewModel: PharmacyViewModel) {
    val byQty by viewModel.topSellingByQty.collectAsState()
    val byValue by viewModel.topSellingByValue.collectAsState()
    val byProfit by viewModel.topProfit.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadAdvancedReports() }

    var selectedHeroTab by remember { mutableStateOf(0) }

    Column {
        TabRow(
            selectedTabIndex = selectedHeroTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(selected = selectedHeroTab == 0, onClick = { selectedHeroTab = 0 }, text = { Text("بالكمية", fontSize = 12.sp) })
            Tab(selected = selectedHeroTab == 1, onClick = { selectedHeroTab = 1 }, text = { Text("بالقيمة", fontSize = 12.sp) })
            Tab(selected = selectedHeroTab == 2, onClick = { selectedHeroTab = 2 }, text = { Text("بالربح", fontSize = 12.sp) })
        }
        Spacer(modifier = Modifier.height(12.dp))

        val currentList = when (selectedHeroTab) {
            0 -> byQty
            1 -> byValue
            else -> byProfit
        }

        if (currentList.isEmpty()) {
            Text("لا توجد مبيعات كافية لتحديد الأبطال.", color = Color.Gray)
        } else {
            LazyColumn {
                items(currentList) { item ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("الكمية: ${item.qty} | المبيعات: ${item.sales}", fontSize = 11.sp, color = Color.Gray)
                            }
                            Text("ربح: ${item.profit}", fontWeight = FontWeight.Bold, color = Color(0xFF3D956B), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SupplierReportUI(viewModel: PharmacyViewModel) {
    val suppliers by viewModel.supplierSummary.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadAdvancedReports() }

    if (suppliers.isEmpty()) {
        Text("لا توجد بيانات موردين.", color = Color.Gray)
    } else {
        LazyColumn {
            items(suppliers) { sup ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(sup.supplierName, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المشتريات: ${sup.totalPurchases}", fontSize = 11.sp)
                            Text("المدفوع: ${sup.totalPaid}", fontSize = 11.sp, color = Color(0xFF3D956B))
                            Text("الدين: ${sup.totalDebt}", fontSize = 11.sp, color = Color(0xFFD65353), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}