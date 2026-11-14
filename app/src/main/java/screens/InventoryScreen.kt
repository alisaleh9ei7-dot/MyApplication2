package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

// أنواع الترتيب والفلترة
enum class SortOption(val title: String) {
    ALPHABETICAL("أبجدي (أ-ي)"),
    LOWEST_STOCK("الأقل كمية"),
    HIGHEST_STOCK("الأكثر كمية"),
    EXPIRING_SOON("قاربت على الانتهاء"),
    EXPIRED("منتهية الصلاحية"),
    LOW_STOCK("نواقص فقط")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(viewModel: PharmacyViewModel) {
    val context = LocalContext.current
    val allItems by viewModel.items.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    val expiringItems by viewModel.expiringItems.collectAsState()
    val expiredItems by viewModel.expiredItems.collectAsState()

// تحميل بيانات الصلاحيات عند فتح الشاشة
    LaunchedEffect(Unit) {
        viewModel.loadExpiryFilters()
    }

    var selectedSort by remember { mutableStateOf(SortOption.ALPHABETICAL) }
    var sortExpanded by remember { mutableStateOf(false) }

    // حساب الإحصائيات
    val totalItems = allItems.size
    val totalQuantity = allItems.sumOf { it.currentStock }

    // ملاحظة: لحساب قيمة المخزون بالنت، نحتاج لجلب الدفعات لكل صنف (وهذا قد يكون ثقيلاً على الشاشة).
    // كحل مؤقت، سنعرض قيمة المخزون الإجمالية من الـ ViewModel (التي تعتمد على الدفعات).
    val dashboardStats by viewModel.dashboardStats.collectAsState()
    val inventoryValue = dashboardStats.inventoryValue

    // تطبيق الفلترة والترتيب
    val displayItems = remember(allItems, searchQuery, selectedSort, expiringItems, expiredItems) {
        // 1. البحث
        var list = allItems.filter { item ->
            item.name.contains(searchQuery, true) ||
                    (item.company?.contains(searchQuery, true) == true)
        }

        // 2. تطبيق الفلتر
        list = when (selectedSort) {
            SortOption.ALPHABETICAL -> list.sortedBy { it.name }
            SortOption.LOWEST_STOCK -> list.sortedBy { it.currentStock }
            SortOption.HIGHEST_STOCK -> list.sortedByDescending { it.currentStock }
            SortOption.LOW_STOCK -> list.filter { it.currentStock <= it.reorderLevel }.sortedBy { it.currentStock }

            // 👇 استخدام القوائم الجديدة للصلاحيات 👇
            SortOption.EXPIRING_SOON -> expiringItems.filter { item -> list.any { it.itemId == item.itemId } }
            SortOption.EXPIRED -> expiredItems.filter { item -> list.any { it.itemId == item.itemId } }
        }
        list
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {

        // --- 1. قسم الإحصائيات (3 بطاقات) ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
             InventoryStatCard(
                title = "الأصناف",
                value = totalItems.toString(),
                icon = Icons.Default.Inventory,
                iconColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            InventoryStatCard(
                title = "الكميات",
                value = String.format(Locale.US, "%.0f", totalQuantity),
                icon = Icons.Default.ShoppingCart,
                iconColor = Color(0xFF3D956B),
                modifier = Modifier.weight(1f)
            )
            InventoryStatCard(
                title = "قيمة النت",
                value = String.format(Locale.US, "%.0f", inventoryValue),
                icon = Icons.Default.MonetizationOn,
                iconColor = Color(0xFFE8A23A),
                modifier = Modifier.weight(1f)
            )
        }

// --- 2. شريط البحث والفلترة ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // البحث (يأخذ مساحة أكبر)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                placeholder = { Text("بحث بالاسم أو الشركة...", fontSize = 12.sp) },
                modifier = Modifier.weight(1.5f), // 👈 وزن أكبر
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

            // القائمة المنسدلة (تأخذ مساحة أقل)
            FilterDropdown(
                items = SortOption.values().toList(),
                selected = selectedSort,
                onSelected = { selectedSort = it },
                itemLabel = { it.title },
                modifier = Modifier.weight(1f), // 👈 وزن أقل، وبدون عرض ثابت
                label = null // 👈 إزالة التسمية لتوفير المساحة
            )
        }

        // --- 3. قائمة الأصناف (بعمود واحد) ---
        if (displayItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد أصناف مطابقة", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
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

    val displayBatches by viewModel.getBatchesForItem(item.itemId).collectAsState(initial = emptyList())
    val salePrice = displayBatches.firstOrNull()?.salePrice ?: 0.0
    val purchasePrice = displayBatches.firstOrNull()?.purchasePrice ?: 0.0
    val nearestExpiry = displayBatches.minByOrNull { it.expiryDate }?.expiryDate

    var showReorderDialog by remember { mutableStateOf(false) }
    var showPriceDialog by remember { mutableStateOf(false) }
    var batchToWriteOff by remember { mutableStateOf<BatchEntity?>(null) }

    val dateFormatFull = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dateFormatShort = SimpleDateFormat("MM-yyyy", Locale.US)

    val isLowStock = item.currentStock <= item.reorderLevel
    val isExpired = nearestExpiry != null && nearestExpiry < System.currentTimeMillis()

    // لون الشريط الجانبي
    val statusColor = when {
        item.currentStock == 0.0 -> Color(0xFFD65353)
        isExpired -> Color(0xFFD65353)
        isLowStock -> Color(0xFFE8A23A)
        else -> Color(0xFF3D956B)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // 👇 الشريط الملون (يمين) 👇
            Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(statusColor))

            // 👇 محتوى البطاقة (داخل Column) 👇
            Column(modifier = Modifier.padding(12.dp).weight(1f)) {
                // السطر الأول: الاسم (يمين) + الصلاحية (يسار)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.company ?: "-"} | ${item.dosageForm ?: "-"}",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (nearestExpiry != null) {
                        val expiryColor = if (isExpired) Color.Red else Color(0xFFE8A23A)
                        Text(
                            text = "صلاحية: ${dateFormatShort.format(Date(nearestExpiry))}",
                            fontSize = 10.sp,
                            color = expiryColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(6.dp))

                // السطر الثاني: الكمية، الطلب، الأسعار (كلها في سطر واحد)
                Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("الرصيد", fontSize = 9.sp, color = Color.Gray)
                        Text(
                            text = String.format(Locale.US, "%.0f", item.currentStock),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (isLowStock) Color.Red else MaterialTheme.colorScheme.primary
                        )
                    }
                    Column {
                        Text("الطلب", fontSize = 9.sp, color = Color.Gray)
                        Text(
                            text = String.format(Locale.US, "%.0f", item.reorderLevel),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column {
                        Text("النت", fontSize = 9.sp, color = Color.Gray)
                        Text(
                            text = String.format(Locale.US, "%.0f", purchasePrice),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFFE8A23A)
                        )
                    }
                    Column {
                        Text("البيع", fontSize = 9.sp, color = Color.Gray)
                        Text(
                            text = String.format(Locale.US, "%.0f", salePrice),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF3D956B)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // --- القسم القابل للتوسيع (الدفعات) ---
                AnimatedVisibility(visible = expanded) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showReorderDialog = true },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("حد الطلب", fontSize = 11.sp) }

                            OutlinedButton(
                                onClick = { showPriceDialog = true },
                                modifier = Modifier.weight(1f).height(32.dp),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("تعديل السعر", fontSize = 11.sp) }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (batches.isEmpty()) {
                            Text("لا توجد دفعات.", fontSize = 11.sp, color = Color.Red)
                        } else {
                            batches.forEach { batch ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column {
                                            val expiryStr = if (batch.expiryDate > 0) dateFormatFull.format(Date(batch.expiryDate)) else "-"
                                            Text("صلاحية: $expiryStr", fontSize = 10.sp, color = if (batch.expiryDate < System.currentTimeMillis()) Color.Red else Color.DarkGray)
                                            Text("الكمية: ${batch.currentQty} | السعر: ${batch.salePrice}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        }
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
            } // 👈 يغلق Column (محتوى البطاقة)
        } // 👈 يغلق Row (الذي يحتوي على الشرائط والمحتوى)
    } // 👈 يغلق Card

    // --- النوافذ المنبثقة (يجب أن تكون داخل الدالة وخارج Card) ---
    if (showReorderDialog) {
        var newLevel by remember { mutableStateOf(item.reorderLevel.toString()) }
        AlertDialog(
            onDismissRequest = { showReorderDialog = false },
            title = { Text("تعديل حد إعادة الطلب") },
            text = { OutlinedTextField(value = newLevel, onValueChange = { newLevel = it }, label = { Text("الحد الجديد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { Button(onClick = { val level = newLevel.toDoubleOrNull(); if (level != null) { viewModel.updateReorderLevel(item.itemId, level); showReorderDialog = false } }) { Text("حفظ") } },
            dismissButton = { TextButton(onClick = { showReorderDialog = false }) { Text("إلغاء") } }
        )
    }

    if (showPriceDialog) {
        var newPrice by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPriceDialog = false },
            title = { Text("تعديل سعر البيع") },
            text = { OutlinedTextField(value = newPrice, onValueChange = { newPrice = it }, label = { Text("السعر الجديد") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) },
            confirmButton = { Button(onClick = { val price = newPrice.toDoubleOrNull(); if (price != null && price >= 0) { viewModel.updateSellingPrice(item.itemId, price); Toast.makeText(context, "تم التحديث", Toast.LENGTH_SHORT).show(); showPriceDialog = false } }) { Text("حفظ") } },
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
                    Text("المتوفر: ${batchToWriteOff!!.currentQty}", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = writeOffQty, onValueChange = { writeOffQty = it }, label = { Text("الكمية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("السبب") })
                }
            },
            confirmButton = { Button(onClick = { val qty = writeOffQty.toDoubleOrNull(); if (qty != null && qty > 0) { viewModel.writeOffStock(batchToWriteOff!!.batchId, item.itemId, qty, reason) { success, message -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show(); if (success) batchToWriteOff = null } } }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("تأكيد") } },
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
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.background(iconColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(6.dp)) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(title, fontSize = 10.sp, color = Color.Gray)
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }

    @Composable
    fun <T> FilterDropdown(
        items: List<T>,
        selected: T,
        onSelected: (T) -> Unit,
        itemLabel: (T) -> String,
        modifier: Modifier = Modifier,
        label: String? = null
    ) {
        var expanded by remember { mutableStateOf(false) }

        Box(modifier = modifier) {
            OutlinedTextField(
                value = itemLabel(selected),
                onValueChange = {},
                readOnly = true,
                label = label?.let { { Text(it) } },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                trailingIcon = {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                },
                modifier = Modifier.fillMaxWidth()
            )

            // الطبقة الشفافة لالتقاط الضغط
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { expanded = true }
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                items.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(itemLabel(item), fontSize = 12.sp) },
                        onClick = {
                            onSelected(item)
                            expanded = false
                        }
                    )
                }
            }
        }
    }