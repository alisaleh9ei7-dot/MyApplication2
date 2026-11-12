package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
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
import com.example.myapplication.ui.PharmacyViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuppliersScreen(viewModel: PharmacyViewModel) {
    val context = LocalContext.current
    val ledger by viewModel.supplierLedger.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    // حالات النوافذ المنبثقة
    var supplierToDelete by remember { mutableStateOf<String?>(null) }
    var supplierToEdit by remember { mutableStateOf<String?>(null) }
    var supplierForHistory by remember { mutableStateOf<String?>(null) }

    // --- حالة جديدة لنافذة التسديد ---
    var supplierForPayment by remember { mutableStateOf<Pair<String, Double>?>(null) }

    val groupedSuppliers = ledger.groupBy { it.supplierName }
        .filterKeys { it.contains(searchQuery, ignoreCase = true) }

    val totalSuppliers = groupedSuppliers.size
    val totalPurchases = ledger.sumOf { it.orderValue }
    val totalPaid = ledger.sumOf { it.paidAmount }
    val totalDebt = ledger.sumOf { it.debt }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "إضافة مورد")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox(title = "عدد الموردين", value = totalSuppliers.toString(), color = Color(0xFF2F8F6B), modifier = Modifier.weight(1f))
                StatBox(title = "إجمالي المشتريات", value = totalPurchases.toString(), color = Color(0xFF4F83B8), modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox(title = "إجمالي المدفوع", value = totalPaid.toString(), color = Color(0xFF6B9FCA), modifier = Modifier.weight(1f))
                StatBox(title = "إجمالي الدين", value = totalDebt.toString(), color = Color(0xFFD79A3D), modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = searchQuery, onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث باسم المورد...", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groupedSuppliers.keys.toList()) { supplierName ->
                    val records = groupedSuppliers[supplierName] ?: emptyList()
                    val supplierPurchases = records.sumOf { it.orderValue }
                    val supplierPaid = records.sumOf { it.paidAmount }
                    val supplierDebt = records.sumOf { it.debt }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(supplierName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                                Text("${records.size} فاتورة", fontSize = 11.sp, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // أزرار التحكم
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(onClick = { supplierForHistory = supplierName }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9))) {
                                    Text("السجل", fontSize = 10.sp, color = Color.Black)
                                }
                                Button(onClick = { supplierToEdit = supplierName }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9))) {
                                    Text("تعديل", fontSize = 10.sp, color = Color.Black)
                                }
                                // 👇 زر التسديد الجديد 👇
                                Button(
                                    onClick = {
                                        if (supplierDebt > 0) supplierForPayment = Pair(supplierName, supplierDebt)
                                        else Toast.makeText(context, "لا يوجد دين لتسديده", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(0.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD1FAE5))
                                ) {
                                    Text("تسديد", fontSize = 10.sp, color = Color(0xFF047857), fontWeight = FontWeight.Bold)
                                }
                                // 👆 ----------------- 👆
                                Button(onClick = { supplierToDelete = supplierName }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2))) {
                                    Text("حذف", fontSize = 10.sp, color = Color.Red)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FinanceBox(title = "المشتريات", value = supplierPurchases.toString(), bgColor = Color(0xFFF2F8F5), textColor = Color(0xFF2F8F6B), modifier = Modifier.weight(1f))
                                FinanceBox(title = "المدفوع", value = supplierPaid.toString(), bgColor = Color(0xFFF3F7FB), textColor = Color(0xFF4F83B8), modifier = Modifier.weight(1f))
                                FinanceBox(title = "الدين", value = supplierDebt.toString(), bgColor = Color(0xFFFFF8EC), textColor = Color(0xFFD79A3D), modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // --- إضافة نافذة الدفع هنا ---
    if (supplierForPayment != null) {
        AddPaymentDialog(
            supplierName = supplierForPayment!!.first,
            currentDebt = supplierForPayment!!.second,
            onDismiss = { supplierForPayment = null },
            onSave = { amount ->
                // نقوم بإضافة سجل جديد: قيمة الطلبية = 0، والمبلغ المدفوع = مبلغ التسديد
                viewModel.addSupplierRecord(
                    name = supplierForPayment!!.first,
                    notes = "تسديد دفعة نقدية",
                    orderValue = 0.0,
                    paidAmount = amount
                )
                Toast.makeText(context, "تم تسجيل الدفعة بنجاح", Toast.LENGTH_SHORT).show()
                supplierForPayment = null
            }
        )
    }

    if (showAddDialog) {
        AddSupplierDialog(onDismiss = { showAddDialog = false }, onSave = { name, notes, orderValue, paidAmount ->
            viewModel.addSupplierRecord(name, notes, orderValue, paidAmount)
            Toast.makeText(context, "تمت الإضافة", Toast.LENGTH_SHORT).show()
            showAddDialog = false
        })
    }

    if (supplierToDelete != null) {
        AlertDialog(
            onDismissRequest = { supplierToDelete = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف المورد (${supplierToDelete})؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSupplier(supplierToDelete!!) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                        supplierToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { supplierToDelete = null }) { Text("إلغاء") } }
        )
    }

    if (supplierToEdit != null) {
        EditSupplierDialog(
            supplierName = supplierToEdit!!,
            currentNotes = ledger.firstOrNull { it.supplierName == supplierToEdit }?.notes ?: "",
            onDismiss = { supplierToEdit = null },
            onSave = { newName, newNotes ->
                viewModel.updateSupplier(supplierToEdit!!, newName, newNotes)
                Toast.makeText(context, "تم التعديل", Toast.LENGTH_SHORT).show()
                supplierToEdit = null
            }
        )
    }

    if (supplierForHistory != null) {
        val supplierInvoices by viewModel.getSupplierInvoices(supplierForHistory!!).collectAsState(initial = emptyList())
        AlertDialog(
            onDismissRequest = { supplierForHistory = null },
            title = { Text("سجل حركات: $supplierForHistory") },
            text = {
                if (supplierInvoices.isEmpty()) {
                    Text("لا توجد حركات مسجلة.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(supplierInvoices) { inv ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text("حركة: ${inv.invoiceNo ?: "بدون رقم"}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("قيمة: ${inv.orderValue} | مدفوع: ${inv.paidAmount} | دين: ${inv.debt}", fontSize = 11.sp)
                                Text("الملاحظات: ${inv.notes ?: "-"}", fontSize = 10.sp, color = Color.Gray)
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { supplierForHistory = null }) { Text("إغلاق") } }
        )
    }
}

@Composable
fun StatBox(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(4.dp).height(30.dp).background(color, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(title, fontSize = 10.sp, color = Color.Gray)
                Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }
    }
}

@Composable
fun FinanceBox(title: String, value: String, bgColor: Color, textColor: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = bgColor)) {
        Column(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 10.sp, color = Color.Gray)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
        }
    }
}

// --- نافذة تسديد الدفعة الجديدة ---
@Composable
fun AddPaymentDialog(supplierName: String, currentDebt: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var amount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تسديد للمورد: $supplierName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("إجمالي الدين الحالي: $currentDebt", color = Color.Red, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("المبلغ المدفوع *") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val valAmount = amount.toDoubleOrNull()
                    if (valAmount != null && valAmount > 0) {
                        onSave(valAmount)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
            ) { Text("تسديد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun AddSupplierDialog(onDismiss: () -> Unit, onSave: (String, String, Double, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var orderValue by remember { mutableStateOf("") }
    var paidAmount by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة مورد / فاتورة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المورد *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = orderValue, onValueChange = { orderValue = it }, label = { Text("قيمة الطلبية") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(value = paidAmount, onValueChange = { paidAmount = it }, label = { Text("المبلغ المدفوع") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onSave(name, notes, orderValue.toDoubleOrNull() ?: 0.0, paidAmount.toDoubleOrNull() ?: 0.0) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
fun EditSupplierDialog(supplierName: String, currentNotes: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(supplierName) }
    var notes by remember { mutableStateOf(currentNotes) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل المورد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المورد *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onSave(name, notes) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}