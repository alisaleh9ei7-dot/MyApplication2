package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.local.entity.ItemEntity
import com.example.myapplication.ui.PharmacyViewModel
import com.example.myapplication.ui.PurchaseItemInput
import com.example.myapplication.ui.components.DatePickerField
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.first
import com.example.myapplication.utils.ExpiryDatePolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(viewModel: PharmacyViewModel) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val allItems by viewModel.items.collectAsState()

    var supplierName by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("") }
    var amountPaid by remember { mutableStateOf("0") }
    var purchaseItems by remember { mutableStateOf(listOf<PurchaseItemInput>()) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchResults by remember { mutableStateOf(false) }
    var selectedItemForDetails by remember { mutableStateOf<ItemEntity?>(null) }
    var showAddNewItemDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    val total = purchaseItems.sumOf { it.paidUnits * it.purchasePrice }
    val paid = amountPaid.toDoubleOrNull() ?: 0.0
    val debt = total - paid

    val filteredItems = remember(searchQuery, allItems) {
        if (searchQuery.isBlank()) emptyList()
        else allItems.filter { it.name.contains(searchQuery, true) || (it.company?.contains(searchQuery, true) == true) }.take(5)
    }

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val item = allItems.find { it.barcode == result.contents }
            if (item != null) selectedItemForDetails = item
            else Toast.makeText(context, "لم يتم العثور على دواء بهذا الباركود", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("بيانات فاتورة الشراء", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                val savedSuppliers by viewModel.supplierNames.collectAsState()
                SupplierAutocompleteField(value = supplierName, onValueChange = { supplierName = it }, suppliers = savedSuppliers)
                OutlinedTextField(
                    value = invoiceNumber, onValueChange = { invoiceNumber = it },
                    label = { Text("رقم الفاتورة *") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
            }
        }

        Box {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; showSearchResults = it.isNotBlank() },
                placeholder = { Text("ابحث عن دواء أو امسح الباركود...") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { showAddNewItemDialog = true }) {
                            Icon(Icons.Default.AddCircle, contentDescription = "دواء جديد", tint = Color(0xFF3D956B))
                        }
                        IconButton(onClick = {
                            barcodeLauncher.launch(ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                                setPrompt("امسح باركود الدواء")
                                setBeepEnabled(false); setOrientationLocked(true)
                            })
                        }) { Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            )

            if (showSearchResults && filteredItems.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 60.dp).heightIn(max = 200.dp), elevation = CardDefaults.cardElevation(8.dp)) {
                    LazyColumn {
                        items(filteredItems) { item ->
                            Row(modifier = Modifier.fillMaxWidth().clickable {
                                selectedItemForDetails = item
                                searchQuery = ""; showSearchResults = false; focusManager.clearFocus()
                            }.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(item.name, fontWeight = FontWeight.Bold)
                                    Text("المخزون: ${item.currentStock} | ${item.company ?: "-"}", fontSize = 11.sp, color = Color.Gray)
                                }
                                Icon(Icons.Default.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        if (selectedItemForDetails != null) {
            AddPurchaseDetailsCard(
                item = selectedItemForDetails!!,
                viewModel = viewModel,
                onDismiss = { selectedItemForDetails = null },
                onAdd = { newInput -> purchaseItems = purchaseItems + newInput; selectedItemForDetails = null }
            )
        }

        if (purchaseItems.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("أصناف الفاتورة (${purchaseItems.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الصنف", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(2f))
                        Text("الكمية", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("السعر", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(24.dp))
                    }
                    HorizontalDivider()

                    purchaseItems.forEachIndexed { index, item ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, fontSize = 12.sp, modifier = Modifier.weight(2f))

                            OutlinedTextField(
                                value = item.paidUnits.toString(),
                                onValueChange = { newVal ->
                                    val updated = purchaseItems.toMutableList()
                                    updated[index] = item.copy(paidUnits = newVal.toDoubleOrNull() ?: 1.0)
                                    purchaseItems = updated
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = item.purchasePrice.toString(),
                                onValueChange = { newVal ->
                                    val updated = purchaseItems.toMutableList()
                                    updated[index] = item.copy(purchasePrice = newVal.toDoubleOrNull() ?: 0.0)
                                    purchaseItems = updated
                                },
                                modifier = Modifier.weight(1f).height(50.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                singleLine = true
                            )

                            IconButton(onClick = { purchaseItems = purchaseItems - item }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الإجمالي", fontWeight = FontWeight.Bold)
                        Text("$total", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("المدفوع", color = Color.Gray)
                        OutlinedTextField(value = amountPaid, onValueChange = { amountPaid = it }, modifier = Modifier.width(120.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الدين", fontWeight = FontWeight.Bold, color = if (debt > 0) Color.Red else Color(0xFF3D956B))
                        Text("$debt", fontWeight = FontWeight.Bold, color = if (debt > 0) Color.Red else Color(0xFF3D956B))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (supplierName.isBlank() || invoiceNumber.isBlank()) {
                                Toast.makeText(context, "أدخل اسم المورد ورقم الفاتورة", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isSaving = true
                            viewModel.processPurchase(supplierName, invoiceNumber, paid, purchaseItems,
                                onSuccess = {
                                    isSaving = false
                                    Toast.makeText(context, "تم الحفظ!", Toast.LENGTH_SHORT).show()
                                    supplierName = ""; invoiceNumber = ""; amountPaid = "0"; purchaseItems = emptyList()
                                },
                                onError = { error -> isSaving = false; Toast.makeText(context, error, Toast.LENGTH_LONG).show() }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = !isSaving
                    ) {
                        if (isSaving) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        else Text("💾 حفظ فاتورة الشراء", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showAddNewItemDialog) {
        AddNewItemDialogForPurchase(
            onDismiss = { showAddNewItemDialog = false },
            onAdd = { name, company, form, barcode ->
                selectedItemForDetails = ItemEntity(
                    itemId = "", name = name, company = company, dosageForm = form, barcode = barcode, currentStock = 0.0, reorderLevel = 2.0
                )
                showAddNewItemDialog = false
            }
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPurchaseDetailsCard(
    item: ItemEntity,
    viewModel: PharmacyViewModel,
    onDismiss: () -> Unit,
    onAdd: (PurchaseItemInput) -> Unit
) {
    var form by remember { mutableStateOf(item.dosageForm ?: "") }
    var company by remember { mutableStateOf(item.company ?: "") }
    var paidUnits by remember { mutableStateOf("1") }
    var freeUnits by remember { mutableStateOf("0") }
    var purchasePrice by remember { mutableStateOf("") }
    var salePrice by remember { mutableStateOf("") }
    var clearancePrice by remember { mutableStateOf("") }
    var expiryLong by remember {
        mutableStateOf(ExpiryDatePolicy.addDays(ExpiryDatePolicy.todayDateKey(), 365))
    }
    var barcode by remember { mutableStateOf(item.barcode ?: "") }

    LaunchedEffect(Unit) {
        if (item.itemId.isNotEmpty()) {
            val batches = viewModel.getBatchesForItem(item.itemId).first()
            val lastBatch = batches.firstOrNull()
            if (lastBatch != null) {
                purchasePrice = lastBatch.purchasePrice.toString()
                salePrice = lastBatch.salePrice.toString()
            }
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(4.dp), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("تفاصيل: ${item.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, contentDescription = "إغلاق") }
            }

            OutlinedTextField(value = form, onValueChange = { form = it }, label = { Text("الشكل") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("الشركة") }, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = paidUnits, onValueChange = { paidUnits = it }, label = { Text("المدفوعة") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(value = freeUnits, onValueChange = { freeUnits = it }, label = { Text("المجانية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = purchasePrice, onValueChange = { purchasePrice = it }, label = { Text("سعر الشراء") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(value = salePrice, onValueChange = { salePrice = it }, label = { Text("سعر البيع") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }

            OutlinedTextField(value = clearancePrice, onValueChange = { clearancePrice = it }, label = { Text("سعر التصفية (اختياري)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())

            DatePickerField(label = "تاريخ الصلاحية", initialDate = expiryLong, onDateSelected = { expiryLong = it })

            Button(
                onClick = {
                    val pu = paidUnits.toDoubleOrNull()
                    val pp = purchasePrice.toDoubleOrNull()
                    val sp = salePrice.toDoubleOrNull()

                    if (pu != null && pp != null && sp != null) {
                        onAdd(PurchaseItemInput(
                            name = item.name, form = form, company = company,
                            paidUnits = pu, freeUnits = freeUnits.toDoubleOrNull() ?: 0.0,
                            purchasePrice = pp, salePrice = sp, expiryDate = expiryLong, barcode = barcode.ifBlank { null }
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D956B))
            ) { Text("إضافة للفاتورة") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNewItemDialogForPurchase(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var form by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة دواء جديد كلياً") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الدواء *") })
                OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("الشركة") })
                OutlinedTextField(value = form, onValueChange = { form = it }, label = { Text("الشكل") })
                OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("الباركود (اختياري)") })
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) onAdd(name, company, form, barcode.ifBlank { null })
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    suppliers: List<String>,
    modifier: Modifier = Modifier,
    label: String = "اسم المورد *",
    onNext: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val query = value.trim()
    val filtered = remember(query, suppliers) {
        if (query.isEmpty()) emptyList()
        else suppliers.filter { it.contains(query, ignoreCase = true) }.take(6)
    }
    val exactMatch = suppliers.any { it.equals(query, ignoreCase = true) }
    val showAddNew = query.isNotEmpty() && !exactMatch
    val hasItems = filtered.isNotEmpty() || showAddNew

    ExposedDropdownMenuBox(
        expanded = expanded && hasItems,
        onExpandedChange = { expanded = it && value.isNotBlank() },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { new ->
                val isDeleting = new.length < value.length
                onValueChange(new)
                expanded = new.isNotBlank() && !isDeleting
            },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded && hasItems,
            onDismissRequest = { expanded = false }
        ) {
            filtered.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onValueChange(name); expanded = false })
            }
            if (showAddNew) {
                DropdownMenuItem(
                    text = { Text("➕ إضافة \"$query\" كمورد جديد", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                    onClick = { onValueChange(query); expanded = false }
                )
            }
        }
    }
}