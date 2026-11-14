package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.PharmacyViewModel
import com.example.myapplication.ui.PurchaseItemInput
import com.example.myapplication.ui.components.DatePickerField
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import androidx.compose.runtime.*
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.ExposedDropdownMenuBoxScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(viewModel: PharmacyViewModel) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current // 👈 للتحكم في التنقل بين الحقول

    // حالة الفاتورة
    var supplierName by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("") }
    var amountPaid by remember { mutableStateOf("0") }

    // قائمة الأصناف المضافة
    var purchaseItems by remember { mutableStateOf(listOf<PurchaseItemInput>()) }

    // حالة النافذة المنبثقة
    var showAddItemDialog by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // الحسابات
    val total = purchaseItems.sumOf { it.paidUnits * it.purchasePrice }
    val paid = amountPaid.toDoubleOrNull() ?: 0.0
    val debt = total - paid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
// --- 1. بيانات الفاتورة والمورد ---
// --- 1. بيانات الفاتورة والمورد ---
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("بيانات فاتورة الشراء", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)

                // 👇 المكون الجديد الذكي (بدلاً من الحقلين القديمين) 👇
                val savedSuppliers by viewModel.supplierNames.collectAsState()

                SupplierAutocompleteField(
                    value = supplierName,
                    onValueChange = { supplierName = it },
                    suppliers = savedSuppliers,
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                )

                // حقل رقم الفاتورة
                OutlinedTextField(
                    value = invoiceNumber,
                    onValueChange = { invoiceNumber = it },
                    label = { Text("رقم الفاتورة *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
            }
        }

        // --- 2. زر إضافة صنف ---
        Button(
            onClick = { showAddItemDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary) // 👈 اللون النيلي الجديد
        ) {
            Text("➕ إضافة صنف جديد للفاتورة", color = Color.White)
        }

        // --- 3. جدول الأصناف المضافة ---
        if (purchaseItems.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("أصناف الفاتورة (${purchaseItems.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF173B66))
                    Spacer(modifier = Modifier.height(8.dp))

                    // رأس الجدول
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الصنف", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(2f))
                        Text("الكمية", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("القيمة", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(24.dp))
                    }
                    HorizontalDivider()

                    // صفوف الأصناف
                    purchaseItems.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(item.name, fontSize = 12.sp, modifier = Modifier.weight(2f))
                            Text("${(item.paidUnits + item.freeUnits).toInt()}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("${item.paidUnits * item.purchasePrice}", fontSize = 12.sp, color = Color(0xFF2563EB), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { purchaseItems = purchaseItems - item }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                    }
                }
            }
        }

        // --- 4. ملخص الفاتورة والحفظ ---
        if (purchaseItems.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)), elevation = CardDefaults.cardElevation(1.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ملخص الفاتورة", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF173B66))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("قيمة الطلبية", color = Color.Gray)
                        Text("$total", fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("المبلغ المدفوع", color = Color.Gray)
                        OutlinedTextField(
                            value = amountPaid, onValueChange = { amountPaid = it },
                            modifier = Modifier.width(120.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            singleLine = true
                        )
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("الدين الناتج", fontWeight = FontWeight.Bold, color = if (debt > 0) Color.Red else Color.Green)
                        Text("$debt", fontWeight = FontWeight.Bold, color = if (debt > 0) Color.Red else Color.Green)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (supplierName.isBlank() || invoiceNumber.isBlank()) {
                                Toast.makeText(context, "يرجى إدخال اسم المورد ورقم الفاتورة", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            isSaving = true
                            viewModel.processPurchase(
                                supplierName = supplierName,
                                invoiceNumber = invoiceNumber,
                                amountPaid = paid,
                                items = purchaseItems,
                                onSuccess = {
                                    isSaving = false
                                    Toast.makeText(context, "تم حفظ الفاتورة بنجاح!", Toast.LENGTH_LONG).show()
                                    supplierName = ""
                                    invoiceNumber = ""
                                    amountPaid = "0"
                                    purchaseItems = emptyList()
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text("💾 حفظ فاتورة الشراء", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // --- النافذة المنبثقة لإضافة صنف ---
    if (showAddItemDialog) {
        AddPurchaseItemDialog(
            onDismiss = { showAddItemDialog = false },
            onAdd = { newItem ->
                purchaseItems = purchaseItems + newItem
                showAddItemDialog = false
            }
        )
    }
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
                // القائمة تفتح فقط عند الكتابة، وتغلق عند الحذف أو الفراغ
                expanded = new.isNotBlank() && !isDeleting
            },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryEditable) // 👈 حل المشكلة الأساسية
        )

        ExposedDropdownMenu(
            expanded = expanded && hasItems,
            onDismissRequest = { expanded = false }
        ) {
            filtered.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onValueChange(name)
                        expanded = false
                    }
                )
            }
            if (showAddNew) {
                DropdownMenuItem(
                    text = { Text("➕ إضافة \"$query\" كمورد جديد", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                    onClick = {
                        onValueChange(query)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPurchaseItemDialog(onDismiss: () -> Unit, onAdd: (PurchaseItemInput) -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // 👇 تعريف طوابير التركيز لكل حقل 👇
    val focusName = remember { FocusRequester() }
    val focusForm = remember { FocusRequester() }
    val focusCompany = remember { FocusRequester() }
    val focusBarcode = remember { FocusRequester() }
    val focusPaid = remember { FocusRequester() }
    val focusFree = remember { FocusRequester() }
    val focusPurchase = remember { FocusRequester() }
    val focusSale = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(300)
        focusName.requestFocus() // التركيز على الاسم عند فتح النافذة
    }

    var name by remember { mutableStateOf("") }
    var form by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var paidUnits by remember { mutableStateOf("") }
    var freeUnits by remember { mutableStateOf("0") }
    var purchasePrice by remember { mutableStateOf("") }
    var salePrice by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var expiryLong by remember { mutableStateOf(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000) }

    val barcodeLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            vibratePhone(context)
            barcode = result.contents
            Toast.makeText(context, "تم مسح الباركود: $barcode", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة صنف للفاتورة") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null // 👈 لإلغاء تأثير الضغط الرمادي
                    ) {
                        focusManager.clearFocus() // 👈 يغلق الكيبورد عند الضغط في أي مساحة فارغة
                    },
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. الاسم
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الدواء *") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusName),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusForm.requestFocus() })
                )

                // 2. الشكل
                OutlinedTextField(
                    value = form, onValueChange = { form = it }, label = { Text("الشكل (حب، شراب)") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusForm), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusCompany.requestFocus() })
                )

                // 3. الشركة
                OutlinedTextField(
                    value = company, onValueChange = { company = it }, label = { Text("الشركة") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusCompany), singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusBarcode.requestFocus() })
                )

                // 4. الباركود
                OutlinedTextField(
                    value = barcode,
                    onValueChange = { barcode = it },
                    label = { Text("الباركود (اختياري)") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusBarcode),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusPaid.requestFocus() }),
                    trailingIcon = {
                        IconButton(onClick = {
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                                setPrompt("قم بتوجيه الكاميرا نحو باركود الدواء")
                                setBeepEnabled(false)
                                setOrientationLocked(true)
                            }
                            barcodeLauncher.launch(options)
                        }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )

                // 5. الكمية المدفوعة والمجانية
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = paidUnits, onValueChange = { paidUnits = it }, label = { Text("المدفوعة *") },
                        modifier = Modifier.weight(1f).focusRequester(focusPaid),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusFree.requestFocus() }),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = freeUnits, onValueChange = { freeUnits = it }, label = { Text("المجانية") },
                        modifier = Modifier.weight(1f).focusRequester(focusFree),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusPurchase.requestFocus() }),
                        singleLine = true
                    )
                }

                // 6. سعر الشراء والبيع
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = purchasePrice, onValueChange = { purchasePrice = it }, label = { Text("سعر الشراء *") },
                        modifier = Modifier.weight(1f).focusRequester(focusPurchase),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusSale.requestFocus() }),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = salePrice, onValueChange = { salePrice = it }, label = { Text("سعر البيع *") },
                        modifier = Modifier.weight(1f).focusRequester(focusSale),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true
                    )
                }

// 7. التاريخ
                DatePickerField(
                    label = "تاريخ الصلاحية *",
                    initialDate = expiryLong,
                    onDateSelected = { expiryLong = it }
                )
                LaunchedEffect(Unit) {
                    focusManager.clearFocus()
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val pu = paidUnits.toDoubleOrNull()
                val fu = freeUnits.toDoubleOrNull() ?: 0.0
                val pp = purchasePrice.toDoubleOrNull()
                val sp = salePrice.toDoubleOrNull()

                if (name.isNotBlank() && pu != null && pp != null && sp != null) {
                    onAdd(
                        PurchaseItemInput(
                            name = name, form = form, company = company,
                            paidUnits = pu, freeUnits = fu,
                            purchasePrice = pp, salePrice = sp, expiryDate = expiryLong,
                            barcode = if (barcode.isBlank()) null else barcode
                        )
                    )
                } else {
                    Toast.makeText(context, "يرجى تعبئة الحقول الأساسية (الاسم، الكمية، السعر)", Toast.LENGTH_SHORT).show()
                }
            }) { Text("إضافة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}