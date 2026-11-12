package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
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
import com.example.myapplication.data.local.entity.ExpenseEntity
import com.example.myapplication.ui.PharmacyViewModel
import com.example.myapplication.ui.components.DatePickerField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(viewModel: PharmacyViewModel) {
    val context = LocalContext.current
    val expenses by viewModel.expenses.collectAsState()

    // حالة النموذج
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var expenseDateLong by remember { mutableStateOf(System.currentTimeMillis()) }

    // حالة الحذف
    var expenseToDelete by remember { mutableStateOf<ExpenseEntity?>(null) }

    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val currentMonthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date()).toLongOrNull() ?: 0L

    // حساب إجمالي مصاريف الشهر الحالي
    val currentMonthTotal = expenses
        .filter { it.month == currentMonthKey }
        .sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F6F9))
    ) {
        // --- 1. بطاقة ملخص الشهر ---
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFDC2626)) // لون أحمر يعبر عن المصاريف
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("إجمالي مصاريف الشهر الحالي", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Text(
                        text = String.format(Locale.US, "%.2f", currentMonthTotal),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(Icons.Default.Receipt, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
            }
        }

        // --- 2. نموذج إضافة مصروف ---
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("إضافة مصروف جديد", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("التصنيف (مثلاً: كهرباء، رواتب)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("الوصف (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("المبلغ *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        DatePickerField(
                            label = "التاريخ",
                            initialDate = expenseDateLong,
                            onDateSelected = { expenseDateLong = it }
                        )
                    }
                }

                Button(
                    onClick = {
                        val amt = amount.toDoubleOrNull()
                        if (amt == null || amt <= 0 || category.isBlank()) {
                            Toast.makeText(context, "يرجى إدخال تصنيف ومبلغ صحيح", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        viewModel.addExpense(amt, category, description, expenseDateLong)
                        Toast.makeText(context, "تم حفظ المصروف بنجاح", Toast.LENGTH_SHORT).show()
                        // تفريغ الحقول
                        amount = ""
                        category = ""
                        description = ""
                        expenseDateLong = System.currentTimeMillis()
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("حفظ المصروف", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        // --- 3. قائمة المصاريف ---
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "سجل المصاريف (${expenses.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color(0xFF1E293B),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (expenses.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد مصاريف مسجلة", color = Color.Gray)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(expenses, key = { it.expenseId }) { expense ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(expense.category, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF1E293B))
                                if (!expense.description.isNullOrBlank()) {
                                    Text(expense.description, fontSize = 12.sp, color = Color.Gray)
                                }
                                Text(dateFormat.format(Date(expense.expenseDate)), fontSize = 11.sp, color = Color.LightGray)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "-${String.format(Locale.US, "%.2f", expense.amount)}",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626),
                                    fontSize = 14.sp
                                )
                                IconButton(onClick = { expenseToDelete = expense }) {
                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Gray, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- 4. نافذة تأكيد الحذف ---
    if (expenseToDelete != null) {
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red) },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف مصروف (${expenseToDelete!!.category}) بقيمة ${expenseToDelete!!.amount}؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExpense(expenseToDelete!!.expenseId)
                        Toast.makeText(context, "تم الحذف", Toast.LENGTH_SHORT).show()
                        expenseToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { expenseToDelete = null }) { Text("إلغاء") } }
        )
    }
}