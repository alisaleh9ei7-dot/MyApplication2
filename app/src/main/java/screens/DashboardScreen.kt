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

// مبيعات اليوم
        item {
            DashboardCard(
                title = "مبيعات اليوم",
                value = String.format(Locale.US, "%.0f", stats.todaySales),
                color = MaterialTheme.colorScheme.primary,
                icon = Icons.Default.BarChart
            )
        }

// أرباح اليوم (الأساسية)
        item {
            DashboardCard(
                title = "أرباح اليوم",
                value = String.format(Locale.US, "%.0f", stats.todayProfit),
                color = Color(0xFF3D956B),
                icon = Icons.Default.TrendingUp
            )
        }

// 👇 أرباح العروض (الجديدة) 👇
        item {
            DashboardCard(
                title = "أرباح العروض",
                value = String.format(Locale.US, "%.0f", stats.monthOfferProfit),
                color = Color(0xFFE8A23A),
                icon = Icons.Default.CardGiftcard
            )
        }

// قيمة المخزون
        item {
            DashboardCard(
                title = "قيمة المخزون",
                value = String.format(Locale.US, "%.0f", stats.inventoryValue),
                color = Color(0xFF7986CB),
                icon = Icons.Default.Inventory
            )
        }

// مصاريف الشهر
        item {
            DashboardCard(
                title = "مصاريف الشهر",
                value = String.format(Locale.US, "%.0f", stats.monthExpenses),
                color = Color(0xFFD65353),
                icon = Icons.Default.Wallet
            )
        }
a
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
fun DashboardCard(
    title: String,
    value: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector // 👈 أضفنا الأيقونة هنا
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // الدائرة الملونة التي تحتوي على الأيقونة
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // النص والرقم
            Column {
                Text(title, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
                Spacer(modifier = Modifier.height(4.dp))
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            }
        }
    }
}

@Composable
fun BarChart(data: List<DailySales>) {
    val maxAmount = data.maxOf { it.amount }.coerceAtLeast(1.0)

    // استخراج الألوان هنا لأن Canvas لا يدعم MaterialTheme مباشرة
    val barColor = MaterialTheme.colorScheme.primary
    val textColor = Color.Gray
    val labelColor = Color(0xFF252B3A)

    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val barWidth = size.width / (data.size * 1.5f)
        val spacing = barWidth * 0.5f
        val textPaint = android.graphics.Paint().apply {
            color = textColor.toArgb()
            textSize = 32f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        val labelPaint = android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 30f
            textAlign = android.graphics.Paint.Align.CENTER
        }

        data.forEachIndexed { index, item ->
            val barHeight = ((item.amount / maxAmount) * size.height * 0.7f).toFloat()
            val x = index * (barWidth + spacing) + spacing + (barWidth / 2)
            val y = size.height - barHeight - 40f

            // 1. رسم العمود
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x - (barWidth / 2), y),
                size = Size(width = barWidth, height = barHeight),
                cornerRadius = CornerRadius(8f, 8f)
            )

            // 2. رسم الرقم فوق العمود (القيمة)
            val valueText = String.format(Locale.US, "%.0f", item.amount)
            drawContext.canvas.nativeCanvas.drawText(
                valueText,
                x,
                y - 10f, // فوق العمود بقليل
                textPaint
            )

            // 3. رسم اسم اليوم تحت العمود
            drawContext.canvas.nativeCanvas.drawText(
                item.dayName,
                x,
                size.height - 5f,
                labelPaint
            )
        }
    }
}