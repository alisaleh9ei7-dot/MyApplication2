package com.example.myapplication.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.PharmacyViewModel
import com.example.myapplication.utils.BackupManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: PharmacyViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // --- حالات النسخ الاحتياطي ---
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }

    // --- 1. حالات بيانات الصيدلية ---
    var pharmacyName by remember { mutableStateOf("") }
    var pharmacyPhone by remember { mutableStateOf("") }
    var pharmacyAddress by remember { mutableStateOf("") }
    var currencySymbol by remember { mutableStateOf("") }

    // --- 2. حالات التنبيهات ---
    var lowStockThreshold by remember { mutableStateOf("2") }
    var expiryAlertDays by remember { mutableStateOf("90") }

    // تحديث الحقول عند تحميل البيانات
    LaunchedEffect(settings) {
        pharmacyName = settings.find { it.key == "pharmacy_name" }?.value ?: ""
        pharmacyPhone = settings.find { it.key == "pharmacy_phone" }?.value ?: ""
        pharmacyAddress = settings.find { it.key == "pharmacy_address" }?.value ?: ""
        currencySymbol = settings.find { it.key == "currency_symbol" }?.value ?: "ل.س"
        lowStockThreshold = settings.find { it.key == "low_stock_threshold" }?.value ?: "2"
        expiryAlertDays = settings.find { it.key == "expiry_alert_days" }?.value ?: "90"
    }

    // مشغل اختيار ملف الاستعادة
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            isRestoring = true
            scope.launch {
                val success = BackupManager.restoreBackup(context, uri)
                isRestoring = false
                if (success) {
                    Toast.makeText(context, "تمت الاستعادة بنجاح! سيُعاد تشغيل التطبيق.", Toast.LENGTH_LONG).show()
                    kotlinx.coroutines.delay(1500)
                    BackupManager.restartApp(context)
                } else {
                    Toast.makeText(context, "فشلت الاستعادة. تأكد من صحة الملف.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- قسم: بيانات الصيدلية ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("بيانات الصيدلية", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
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
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("التنبيهات والحدود", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
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
        // --- قسم: النسخ الاحتياطي والاستعادة ---
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF3D956B))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("النسخ الاحتياطي والبيانات", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Text("قم بحفظ نسخة من بياناتك أو استعادتها في حال تغيير الهاتف.", fontSize = 12.sp, color = Color.Gray)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // زر النسخ الاحتياطي
                    Button(
                        onClick = {
                            isBackingUp = true
                            scope.launch {
                                val uri = BackupManager.createBackup(context)
                                isBackingUp = false
                                if (uri != null) {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "application/zip"
                                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "حفظ النسخة الاحتياطية..."))
                                } else {
                                    Toast.makeText(context, "فشل إنشاء النسخة الاحتياطية", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isBackingUp && !isRestoring,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D956B))
                    ) {
                        if (isBackingUp) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else {
                            Icon(Icons.Default.Backup, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("نسخ احتياطي")
                        }
                    }

                    // زر الاستعادة
                    OutlinedButton(
                        onClick = { restoreLauncher.launch("application/zip") },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isBackingUp && !isRestoring,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD65353))
                    ) {
                        if (isRestoring) CircularProgressIndicator(color = Color(0xFFD65353), modifier = Modifier.size(20.dp))
                        else {
                            Icon(Icons.Default.Restore, contentDescription = null, tint = Color(0xFFD65353))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("استعادة", color = Color(0xFFD65353))
                        }
                    }
                }
            }
        }

        // --- زر الحفظ ---
        Button(
            onClick = {
                viewModel.saveSetting("pharmacy_name", pharmacyName)
                viewModel.saveSetting("pharmacy_phone", pharmacyPhone)
                viewModel.saveSetting("pharmacy_address", pharmacyAddress)
                viewModel.saveSetting("currency_symbol", currencySymbol)
                viewModel.saveSetting("low_stock_threshold", lowStockThreshold)
                viewModel.saveSetting("expiry_alert_days", expiryAlertDays)

                Toast.makeText(context, "تم حفظ الإعدادات بنجاح", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth().height(55.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("حفظ التعديلات", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}