package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.local.entity.BatchEntity
import com.example.myapplication.data.local.entity.ItemEntity
import com.example.myapplication.ui.PharmacyViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// تصنيفات الفلترة
enum class InventoryFilter {
    ALL, LOW_STOCK, EXPIRING, EXPIRED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(viewModel: PharmacyViewModel) {
    val context = LocalContext.current
    val allItems by viewModel.items.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredItems by viewModel.filteredItems.collectAsState()

    // حالة الفلتر المختار
    var selectedFilter by remember { mutableStateOf(InventoryFilter.ALL) }

    // إحصائيات
    val totalItems = allItems.size
    val totalQuantity = allItems.sumOf { it.currentStock }
    val lowStockCount = allItems.count { it.currentStock <= it.reorderLevel }

    // تطبيق الفلترة
    val displayItems = when (selectedFilter) {
        InventoryFilter.ALL -> filteredItems
        InventoryFilter.LOW_STOCK -> filteredItems.filter { it.currentStock <= it.reorderLevel }
        InventoryFilter.EXPIRING -> filteredItems // سنربطها بالصلاحية لاحقاً
        InventoryFilter.EXPIRED -> filteredItems // سنربطها بالصلاحية لاحقاً
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // --- قسم الإحصائيات العلوي ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InventoryStatCard(
                title = "إجمالي الأصناف",
                value = totalItems.toString(),
                icon = Icons.Default.Inventory,
                iconColor = Color(0xFF2563EB),
                modifier = Modifier.weight(1f)
            )
            InventoryStatCard(
                title = "إجمالي الكميات",
                value = String.format(Locale.US, "%.0f", totalQuantity),
                icon = Icons.Default.ShoppingCart,
                iconColor = Color(0xFF16A34A),
                modifier = Modifier.weight(1f)
            )
        }

        // --- أزرار الفلترة (Chips) ---
        ScrollableTabRow(
            selectedTabIndex = selectedFilter.ordinal,
            edgePadding = 16.dp,
            containerColor = MaterialTheme.colorScheme.background,
            divider = {},
            indicator = {}
        ) {
            FilterChip(
                selected = selectedFilter == InventoryFilter.ALL,
                onClick = { selectedFilter = InventoryFilter.ALL },
                label = { Text("الكل ($totalItems)") },
                modifier = Modifier.padding(end = 8.dp)
            )
            FilterChip(
                selected = selectedFilter == InventoryFilter.LOW_STOCK,
                onClick = { selectedFilter = InventoryFilter.LOW_STOCK },
                label = { Text("قليلة ($lowStockCount)", color = if (lowStockCount > 0) Color.Red else Color.Unspecified) },
                modifier = Modifier.padding(end = 8.dp)
            )
            FilterChip(
                selected = selectedFilter == InventoryFilter.EXPIRING,
                onClick = { selectedFilter = InventoryFilter.EXPIRING },
                label = { Text("قاربت") },
                modifier = Modifier.padding(end = 8.dp)
            )
            FilterChip(
                selected = selectedFilter == InventoryFilter.EXPIRED,
                onClick = { selectedFilter = InventoryFilter.EXPIRED },
                label = { Text("منتهية") }
            )
        }

        // --- شريط البحث ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("بحث في المخزون...", fontSize = 14.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color.Gray)
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            // زر إصلاح الأرصدة
            IconButton(
                onClick = {
                    viewModel.repairInventoryBalances {
                        Toast.makeText(context, "تم إصلاح الأرصدة بنجاح!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Build, contentDescription = "إصلاح الأرصدة", tint = MaterialTheme.colorScheme.primary)
            }
        }

        // --- شبكة الأصناف (Grid) ---
        if (displayItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد أصناف مطابقة", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2), // عمودين
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayItems, key = { it.itemId }) { item ->
                    InventoryItemCard(item = item, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun InventoryItemCard(item: ItemEntity, viewModel: PharmacyViewModel) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val batches by if (expanded) {
        viewModel.getBatchesForItem(item.itemId).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<BatchEntity>()) }
    }

    var showReorderDialog by remember { mutableStateOf(false) }
    var showPriceDialog by remember { mutableStateOf(false) }
    var batchToWriteOff by remember { mutableStateOf<BatchEntity?>(null) }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val isLowStock = item.currentStock <= item.reorderLevel

    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // رأس البطاقة: اسم الصنف والشركة
            Text(
                text = item.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.company ?: "غير محدد",
                fontSize = 11.sp,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // بيانات المخزون
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("الرصيد", fontSize = 10.sp, color = Color.Gray)
                    Text(
                        text = String.format(Locale.US, "%.0f", item.currentStock),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (isLowStock) Color.Red else MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("حد الطلب", fontSize = 10.sp, color = Color.Gray)
                    Text(
                        text = String.format(Locale.US, "%.0f", item.reorderLevel),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // زر التفاصيل/الطي
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(if (expanded) "إخفاء الدفعات" else "عرض الدفعات", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // --- القسم القابل للتوسيع (الدفعات) ---
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                    // أزرار التحكم
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { showReorderDialog = true },
                            modifier = Modifier.weight(1f).height(32.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF2FF)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("حد الطلب", fontSize = 10.sp, color = Color(0xFF3730A3))
                        }
                        Button(
                            onClick = { showPriceDialog = true },
                            modifier = Modifier.weight(1f).height(32.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFECFDF5)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("تعديل السعر", fontSize = 10.sp, color = Color(0xFF047857))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (batches.isEmpty()) {
                        Text("لا توجد دفعات مسجلة.", fontSize = 11.sp, color = Color.Red)
                    } else {
                        batches.forEach { batch ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        val expiryStr = if (batch.expiryDate > 0) dateFormat.format(Date(batch.expiryDate)) else "غير محدد"
                                        Text("صلاحية: $expiryStr", fontSize = 10.sp, color = if (batch.expiryDate < System.currentTimeMillis()) Color.Red else Color.DarkGray)
                                        Text("كمية: ${batch.currentQty}", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("السعر: ${batch.salePrice}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        IconButton(
                                            onClick = { batchToWriteOff = batch },
                                            modifier = Modifier.size(24.dp).background(Color(0xFFFFEBEE), RoundedCornerShape(4.dp))
                                        ) {
                                            Icon(Icons.Default.DeleteSweep, contentDescription = "إتلاف", tint = Color.Red, modifier = Modifier.size(14.dp))
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

    // --- النوافذ المنبثقة (Dialogs) ---
    if (showReorderDialog) {
        var newLevel by remember { mutableStateOf(item.reorderLevel.toString()) }
        AlertDialog(
            onDismissRequest = { showReorderDialog = false },
            title = { Text("تعديل حد إعادة الطلب") },
            text = {
                OutlinedTextField(
                    value = newLevel,
                    onValueChange = { newLevel = it },
                    label = { Text("الحد الجديد") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                Button(onClick = {
                    val level = newLevel.toDoubleOrNull()
                    if (level != null) {
                        viewModel.updateReorderLevel(item.itemId, level)
                        showReorderDialog = false
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showReorderDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showPriceDialog) {
        var newPrice by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPriceDialog = false },
            title = { Text("تعديل سعر البيع (لكل الدفعات)") },
            text = {
                OutlinedTextField(
                    value = newPrice,
                    onValueChange = { newPrice = it },
                    label = { Text("السعر الجديد") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                Button(onClick = {
                    val price = newPrice.toDoubleOrNull()
                    if (price != null && price >= 0) {
                        viewModel.updateSellingPrice(item.itemId, price)
                        Toast.makeText(context, "تم تحديث سعر البيع", Toast.LENGTH_SHORT).show()
                        showPriceDialog = false
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { showPriceDialog = false }) { Text("إلغاء") } }
        )
    }

    if (batchToWriteOff != null) {
        var writeOffQty by remember { mutableStateOf("") }
        var reason by remember { mutableStateOf("تالف") }

        AlertDialog(
            onDismissRequest = { batchToWriteOff = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
            title = { Text("إتلاف دواء") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("الكمية المتوفرة في الدفعة: ${batchToWriteOff!!.currentQty}", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = writeOffQty,
                        onValueChange = { writeOffQty = it },
                        label = { Text("الكمية المراد إتلافها") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text("سبب الإتلاف") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qty = writeOffQty.toDoubleOrNull()
                        if (qty != null && qty > 0) {
                            viewModel.writeOffStock(batchToWriteOff!!.batchId, item.itemId, qty, reason) { success, message ->
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                if (success) batchToWriteOff = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("تأكيد الإتلاف") }
            },
            dismissButton = { TextButton(onClick = { batchToWriteOff = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
fun InventoryStatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.background(iconColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, fontSize = 10.sp, color = Color.Gray)
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
