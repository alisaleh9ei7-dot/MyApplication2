package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.DailySales
import com.example.myapplication.ui.PharmacyViewModel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: PharmacyViewModel, onNavigate: (String) -> Unit = {}) {
    val stats by viewModel.dashboardStats.collectAsState()
    val chartData by viewModel.weeklySalesData.collectAsState()
    val recentSales by viewModel.recentSales.collectAsState() // 👈 إضافة أحدث الفواتير
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("HH:mm a", Locale("ar"))

    // متغير لحفظ الدواء الذي تم العثور عليه
    var scannedItem by remember { mutableStateOf<com.example.myapplication.data.local.entity.ItemEntity?>(null) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            viewModel.getItemDetailsByBarcode(result.contents) { item ->
                if (item != null) {
                    scannedItem = item
                    showDetailsDialog = true
                } else {
                    Toast.makeText(context, "لم يتم العثور على الدواء!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showDetailsDialog && scannedItem != null) {
        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            title = { Text(scannedItem!!.name, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الشركة: ${scannedItem!!.company ?: "-"}")
                    Text("الشكل: ${scannedItem!!.dosageForm ?: "-"}")
                    Text("الكمية المتوفرة: ${scannedItem!!.currentStock}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = { TextButton(onClick = { showDetailsDialog = false }) { Text("إغلاق") } }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.loadDashboardStats()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. الترحيب والباركود
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(45.dp)) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("أهلاً بك،", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Text("ملخص أداء الصيدلية اليوم", fontSize = 12.sp, color = Color.Gray)
                    }
                }
                IconButton(
                    onClick = {
                        val options = ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                            setPrompt("قم بتوجيه الكاميرا نحو باركود الدواء")
                            setBeepEnabled(false)
                            setOrientationLocked(true)
                        }
                        barcodeLauncher.launch(options)
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // 2. يحتاج انتباهك (التنبيهات)
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("🚨 يحتاج انتباهك", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            AlertCard(
                title = "نواقص المخزون",
                count = stats.lowStockCount,
                icon = Icons.Default.Warning,
                color = Color(0xFFF57C00),
                onClick = { onNavigate("shortages") } // 👈 أصبحت تفتح شاشة النواقص
            )
        }
        item {
            AlertCard(
                title = "قاربت على الانتهاء",
                count = stats.expiringSoonCount,
                icon = Icons.Default.HourglassEmpty,
                color = Color(0xFFDC2626),
                onClick = { onNavigate("reports") }
            )
        }

        // 3. وصول سريع
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("⚡ وصول سريع", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp))
        }
        item {
            QuickActionCard("بيع جديد", Icons.Default.ShoppingCart, MaterialTheme.colorScheme.primary) { onNavigate("sales") }
        }
        item {
            QuickActionCard("شراء جديد", Icons.Default.AddShoppingCart, MaterialTheme.colorScheme.secondary) { onNavigate("purchases") }
        }

        // 4. نبض اليوم (الإحصائيات)
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("📊 نبض اليوم", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp))
        }
        item { DashboardCard("مبيعات اليوم", String.format(Locale.US, "%.0f", stats.todaySales), MaterialTheme.colorScheme.primary) }
        item { DashboardCard("أرباح اليوم", String.format(Locale.US, "%.0f", stats.todayProfit), Color(0xFF16A34A)) }
        item { DashboardCard("قيمة المخزون", String.format(Locale.US, "%.0f", stats.inventoryValue), Color(0xFF8B5CF6)) }
        item { DashboardCard("مصاريف الشهر", String.format(Locale.US, "%.0f", stats.monthExpenses), Color(0xFFDC2626)) }

        // 5. الرسم البياني
        item(span = { GridItemSpan(maxLineSpan) }) {
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📈 مبيعات آخر 7 أيام", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (chartData.isEmpty() || chartData.all { it.amount == 0.0 }) {
                        Text("لا توجد مبيعات مسجلة هذا الأسبوع", color = Color.Gray, fontSize = 12.sp)
                    } else {
                        BarChart(data = chartData)
                    }
                }
            }
        }

        // 6. أحدث الفواتير
        if (recentSales.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("🧾 أحدث الفواتير", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 8.dp))
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        recentSales.take(3).forEach { sale ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(sale.customerName ?: "زبون نقدي", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(dateFormat.format(Date(sale.saleDate)), fontSize = 11.sp, color = Color.Gray)
                                }
                                Text("${String.format(Locale.US, "%.0f", sale.totalAmount)} ل.س", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun AlertCard(title: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$count أصناف", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            }
        }
    }
}

@Composable
fun QuickActionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().height(80.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = CircleShape, color = color.copy(alpha = 0.1f), modifier = Modifier.size(40.dp)) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun DashboardCard(title: String, value: String, color: Color) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun BarChart(data: List<DailySales>) {
    val maxAmount = data.maxOf { it.amount }.coerceAtLeast(1.0)

    // 👇 استخراج الألوان هنا (في بيئة Composable) قبل الدخول إلى Canvas
    val barColor = MaterialTheme.colorScheme.primary
    val textColor = Color.Gray.toArgb()

    Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
        val barWidth = size.width / (data.size * 2)
        val spacing = barWidth / 2
        data.forEachIndexed { index, item ->
            val barHeight = ((item.amount / maxAmount) * size.height * 0.8f).toFloat()

            drawRoundRect(
                color = barColor, // 👈 نستخدم المتغير هنا
                topLeft = Offset(x = index * (barWidth + spacing) + spacing, y = size.height - barHeight - 30f),
                size = Size(width = barWidth, height = barHeight),
                cornerRadius = CornerRadius(8f, 8f)
            )

            drawContext.canvas.nativeCanvas.drawText(
                item.dayName, index * (barWidth + spacing) + spacing + (barWidth / 4), size.height - 5f,
                android.graphics.Paint().apply {
                    color = textColor // 👈 نستخدم المتغير هنا
                    textSize = 30f
                }
            )
        }
    }
}