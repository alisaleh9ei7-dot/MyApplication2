package com.example.myapplication.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.myapplication.data.local.entity.CompanyEntity
import com.example.myapplication.data.local.entity.ItemEntity
import com.example.myapplication.ui.PharmacyViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// كلاس لتمثيل الدواء في سلة المشتريات
data class PurchaseOrderItem(
    val name: String,
    val company: String,
    var requestedQty: Double = 1.0,
    var netPrice: Double = 0.0
) {
    val totalPrice: Double get() = requestedQty * netPrice
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortagesScreen(viewModel: PharmacyViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val allItems by viewModel.items.collectAsState()
    val companies by viewModel.companies.collectAsState()

    // 🟢 حالة الفلترة
    var selectedCompany by remember { mutableStateOf<CompanyEntity?>(null) }
    var expanded by remember { mutableStateOf(false) }

    // 🟢 سلة المشتريات
    val purchaseCart = remember { mutableStateListOf<PurchaseOrderItem>() }
    val grandTotal = purchaseCart.sumOf { it.totalPrice }
    var showCartSheet by remember { mutableStateOf(false) }
    var showAddItemDialog by remember { mutableStateOf(false) }

    // 🟢 قائمة النواقص (قابلة للتعديل والإضافة)
    val systemShortages = remember { mutableStateListOf<ItemEntity>() }

    // تحديث القائمة تلقائياً عند تغير المخزون أو الشركة
    LaunchedEffect(allItems, selectedCompany) {
        val filtered = allItems.filter { item ->
            val isLowStock = item.currentStock <= item.reorderLevel
            val isCompanyMatch = selectedCompany == null || item.company == selectedCompany?.companyName
            isLowStock && isCompanyMatch
        }
        systemShortages.clear()
        systemShortages.addAll(filtered)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("طلبية نواقص", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    // زر السلة في الشريط العلوي
                    BadgedBox(
                        badge = {
                            if (purchaseCart.isNotEmpty()) {
                                Badge { Text(purchaseCart.size.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = { showCartSheet = true }) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "السلة", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddItemDialog = true },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة دواء", modifier = Modifier.size(28.dp))
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues).background(MaterialTheme.colorScheme.background)) {

            // --- فلتر الشركة ---
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.padding(16.dp)
            ) {
                OutlinedTextField(
                    value = selectedCompany?.companyName ?: "كل الشركات",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("فلترة حسب الشركة") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("كل الشركات") },
                        onClick = { selectedCompany = null; expanded = false }
                    )
                    companies.forEach { company ->
                        DropdownMenuItem(
                            text = { Text(company.companyName) },
                            onClick = { selectedCompany = company; expanded = false }
                        )
                    }
                }
            }

            Text(
                text = "الأدوية الناقصة (${systemShortages.size}):",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (systemShortages.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("لا توجد نواقص لهذه الشركة", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(systemShortages, key = { it.itemId }) { item ->
                        val isAlreadyInCart = purchaseCart.any { it.name == item.name }
                        ShortageItemCard(
                            name = item.name,
                            company = item.company ?: "-",
                            stock = item.currentStock.toString(),
                            isAdded = isAlreadyInCart,
                            onAdd = {
                                if (!isAlreadyInCart) {
                                    // 👇 جلب آخر سعر لهذا الدواء وإضافته للسلة 👇
                                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
                                        .launch {
                                            viewModel.getBatchesForItem(item.itemId)
                                                .collect { batches ->
                                                    val latestPrice =
                                                        batches.firstOrNull()?.salePrice ?: 0.0
                                                    purchaseCart.add(
                                                        PurchaseOrderItem(
                                                            name = item.name,
                                                            company = item.company ?: "-",
                                                            netPrice = latestPrice // 👈 نمرر السعر هنا
                                                        )
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        "تمت الإضافة للسلة",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                        }
                                }
                            }
                        )
                    }
                }
            }
        }
    } // 👈 إغلاق الـ Scaffold

    // --- نافذة السلة (Bottom Sheet) ---
    if (showCartSheet) {
        ModalBottomSheet(
            onDismissRequest = { showCartSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp)) {
                Text("سلة المشتريات", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))

                if (purchaseCart.isEmpty()) {
                    Text("السلة فارغة", color = Color.Gray, modifier = Modifier.padding(vertical = 24.dp))
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(purchaseCart, key = { it.name }) { cartItem ->
                            CartItemRow(
                                cartItem = cartItem,
                                onRemove = { purchaseCart.remove(cartItem) },
                                onUpdate = { qty, price ->
                                    cartItem.requestedQty = qty
                                    cartItem.netPrice = price
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الإجمالي الكلي:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("${String.format(Locale.US, "%.2f", grandTotal)} ل.س", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            exportPurchasePdf(context, purchaseCart, grandTotal)
                            showCartSheet = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCartCheckout, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("إتمام الشراء")
                    }
                }
            }
        }
    }

    // --- نافذة إضافة دواء جديد ---
    if (showAddItemDialog) {
        AddItemToOrderDialog(
            allItems = allItems,
            companies = companies,
            viewModel = viewModel,
            onDismiss = { showAddItemDialog = false },
            onAdd = { name, company, qty, price ->
                // نبحث إذا كان موجوداً في المخزون
                val existingItem = allItems.find { it.name == name }

                // إذا كان موجوداً، نضيفه كما هو. وإذا لم يكن موجوداً، ننشئ عنصراً جديداً
                val itemToAdd = existingItem ?: ItemEntity(
                    itemId = "NEW-${System.currentTimeMillis()}", // معرف مؤقت
                    name = name,
                    company = company,
                    currentStock = 0.0, // لأنه غير موجود
                    reorderLevel = 1.0
                )

                if (systemShortages.none { it.name == name }) {
                    systemShortages.add(itemToAdd)
                    Toast.makeText(context, "تمت الإضافة إلى النواقص", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "موجود بالفعل في القائمة", Toast.LENGTH_SHORT).show()
                }
                showAddItemDialog = false
            }
        )
    }
} // 👈 إغلاق دالة ShortagesScreen بالكامل هنا

// بطاقة الدواء الناقص في النظام
@Composable
fun ShortageItemCard(name: String, company: String, stock: String, isAdded: Boolean, onAdd: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("الشركة: $company | المخزون: $stock", fontSize = 11.sp, color = Color.Gray)
            }
            Button(
                onClick = onAdd,
                enabled = !isAdded,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAdded) Color.Gray else MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(if (isAdded) Icons.Default.Check else Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isAdded) "مضاف" else "إضافة", fontSize = 12.sp)
            }
        }
    }
}

// بطاقة السلة
@Composable
fun CartItemRow(cartItem: PurchaseOrderItem, onRemove: () -> Unit, onUpdate: (Double, Double) -> Unit) {
    var qty by remember { mutableStateOf(cartItem.requestedQty.toString()) }
    var price by remember { mutableStateOf(if (cartItem.netPrice == 0.0) "" else cartItem.netPrice.toString()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(cartItem.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(cartItem.company, fontSize = 11.sp, color = Color.Gray)
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = qty,
                    onValueChange = {
                        qty = it
                        onUpdate(it.toDoubleOrNull() ?: 1.0, price.toDoubleOrNull() ?: 0.0)
                    },
                    label = { Text("الكمية", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = {
                        price = it
                        onUpdate(qty.toDoubleOrNull() ?: 1.0, it.toDoubleOrNull() ?: 0.0)
                    },
                    label = { Text("سعر النت", fontSize = 10.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "الإجمالي: ${String.format(Locale.US, "%.2f", cartItem.totalPrice)} ل.س",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// نافذة إضافة دواء جديد
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemToOrderDialog(
    allItems: List<ItemEntity>,
    companies: List<CompanyEntity>,
    viewModel: PharmacyViewModel,
    onDismiss: () -> Unit,
    onAdd: (String, String, Double, Double) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }

    var companyExpanded by remember { mutableStateOf(false) }
    var showSearchResults by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val searchResults = if (searchQuery.isBlank()) emptyList() else allItems.filter { it.name.contains(searchQuery, true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة دواء للطلبية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            showSearchResults = it.isNotEmpty()
                        },
                        label = { Text("ابحث عن دواء في المخزون...") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = ""; showSearchResults = false }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح")
                                }
                            }
                        }
                    )

                    if (showSearchResults && searchResults.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 60.dp).heightIn(max = 150.dp),
                            elevation = CardDefaults.cardElevation(8.dp)
                        ) {
                            LazyColumn {
                                items(searchResults) { item ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            name = item.name
                                            company = item.company ?: ""
                                            searchQuery = ""
                                            showSearchResults = false

                                            scope.launch {
                                                viewModel.getBatchesForItem(item.itemId).collect { batches ->
                                                    val latestPrice = batches.firstOrNull()?.salePrice ?: 0.0
                                                    if (latestPrice > 0) {
                                                        price = latestPrice.toString()
                                                    }
                                                }
                                            }
                                        }.padding(12.dp)
                                    ) {
                                        Text(item.name, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("(${item.company ?: "-"})", color = Color.Gray, fontSize = 12.sp)
                                    }
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("أو املأ البيانات يدوياً:", fontSize = 12.sp, color = Color.Gray)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الدواء") },
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = companyExpanded,
                    onExpandedChange = { companyExpanded = !companyExpanded }
                ) {
                    OutlinedTextField(
                        value = company,
                        onValueChange = {
                            company = it
                            companyExpanded = true
                        },
                        label = { Text("الشركة") },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = companyExpanded,
                        onDismissRequest = { companyExpanded = false }
                    ) {
                        companies.forEach { comp ->
                            DropdownMenuItem(
                                text = { Text(comp.companyName) },
                                onClick = { company = comp.companyName; companyExpanded = false }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { qty = it },
                        label = { Text("الكمية") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("سعر النت") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && company.isNotBlank()) {
                    onAdd(name, company, qty.toDoubleOrNull() ?: 1.0, price.toDoubleOrNull() ?: 0.0)
                }
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

// دالة تصدير PDF
fun exportPurchasePdf(context: Context, cart: List<PurchaseOrderItem>, total: Double) {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
    val textPaint = Paint().apply { textSize = 14f; textAlign = Paint.Align.RIGHT }
    val pageWidth = 595f; val margin = 40f
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    var currentY = 60f

    canvas.drawText("طلبية مشتريات - صيدلية", pageWidth / 2, currentY, titlePaint)
    currentY += 30f
    canvas.drawText("التاريخ: ${dateFormat.format(Date())}", pageWidth - margin, currentY, textPaint)
    currentY += 40f

    cart.forEachIndexed { index, orderItem ->
        currentY += 30f
        canvas.drawText("${index + 1}. ${orderItem.name} (${orderItem.company})", pageWidth - margin, currentY, textPaint)
        currentY += 20f
        canvas.drawText("   الكمية: ${orderItem.requestedQty} | السعر: ${orderItem.netPrice} | الإجمالي: ${orderItem.totalPrice}", pageWidth - margin, currentY, textPaint)
    }

    currentY += 40f
    textPaint.isFakeBoldText = true; textPaint.textSize = 18f
    canvas.drawText("الإجمالي الكلي: ${String.format(Locale.US, "%.2f", total)} ل.س", pageWidth - margin, currentY, textPaint)

    pdfDocument.finishPage(page)

    val file = File(context.cacheDir, "PurchaseOrder_${System.currentTimeMillis()}.pdf")
    try { pdfDocument.writeTo(FileOutputStream(file)) } catch (e: Exception) { e.printStackTrace() } finally { pdfDocument.close() }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة طلبية الشراء..."))
}