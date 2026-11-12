package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Save
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
fun SettingsScreen(viewModel: PharmacyViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()

    // --- 1. حالات بيانات الصيدلية ---
    var pharmacyName by remember { mutableStateOf("") }
    var pharmacyPhone by remember { mutableStateOf("") }
    var pharmacyAddress by remember { mutableStateOf("") }
    var currencySymbol by remember { mutableStateOf("") }

    // --- 2. حالات التنبيهات ---
    var lowStockThreshold by remember { mutableStateOf("2") }
    var expiryAlertDays by remember { mutableStateOf("90") }

    // تحديث الحقول عند تحميل البيانات من قاعدة البيانات
    LaunchedEffect(settings) {
        pharmacyName = settings.find { it.key == "pharmacy_name" }?.value ?: ""
        pharmacyPhone = settings.find { it.key == "pharmacy_phone" }?.value ?: ""
        pharmacyAddress = settings.find { it.key == "pharmacy_address" }?.value ?: ""
        currencySymbol = settings.find { it.key == "currency_symbol" }?.value ?: "ل.س"
        lowStockThreshold = settings.find { it.key == "low_stock_threshold" }?.value ?: "2"
        expiryAlertDays = settings.find { it.key == "expiry_alert_days" }?.value ?: "90"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F6F9))
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- قسم: بيانات الصيدلية ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Business, contentDescription = null, tint = Color(0xFF2563EB))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("بيانات الصيدلية", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                }
                Text("ستظهر هذه البيانات في الفواتير المطبوعة أو ملفات PDF.", fontSize = 12.sp, color = Color.Gray)

                OutlinedTextField(
                    value = pharmacyName,
                    onValueChange = { pharmacyName = it },
                    label = { Text("اسم الصيدلية") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = pharmacyPhone,
                    onValueChange = { pharmacyPhone = it },
                    label = { Text("رقم الهاتف") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = pharmacyAddress,
                    onValueChange = { pharmacyAddress = it },
                    label = { Text("العنوان") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = currencySymbol,
                    onValueChange = { currencySymbol = it },
                    label = { Text("رمز العملة (مثلاً: ل.س، $، ر.س)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }

        // --- قسم: التنبيهات والحدود ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = Color(0xFFF57C00))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("التنبيهات والحدود", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1E293B))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lowStockThreshold,
                        onValueChange = { lowStockThreshold = it },
                        label = { Text("حد النواقص الافتراضي") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = expiryAlertDays,
                        onValueChange = { expiryAlertDays = it },
                        label = { Text("تنبيه الصلاحية (أيام)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // --- زر الحفظ ---
        Button(
            onClick = {
                // حفظ البيانات في قاعدة البيانات
                viewModel.saveSetting("pharmacy_name", pharmacyName)
                viewModel.saveSetting("pharmacy_phone", pharmacyPhone)
                viewModel.saveSetting("pharmacy_address", pharmacyAddress)
                viewModel.saveSetting("currency_symbol", currencySymbol)
                viewModel.saveSetting("low_stock_threshold", lowStockThreshold)
                viewModel.saveSetting("expiry_alert_days", expiryAlertDays)

                Toast.makeText(context, "تم حفظ الإعدادات بنجاح", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("حفظ التعديلات", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}