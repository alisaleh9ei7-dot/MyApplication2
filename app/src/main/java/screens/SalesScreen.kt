package com.example.myapplication.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.data.local.entity.ItemEntity
import com.example.myapplication.ui.PharmacyViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(viewModel: PharmacyViewModel) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredItems by viewModel.filteredItems.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()

    var isScanning by remember { mutableStateOf(true) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) isScanning = true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // --- حالة الخانات السفلية (اسم المريض، الوصفة، المجموع) ---
    var customerName by remember { mutableStateOf("") }
    var prescriptionUri by remember { mutableStateOf<Uri?>(null) }
    var editableTotal by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    // خريطة لحفظ أسعار العناصر في السلة لحساب المجموع
    val cartPrices = remember { mutableStateMapOf<String, Double>() }
    val calculatedTotal = cartPrices.values.sum()
    val scope = rememberCoroutineScope()

// تحديث المجموع تلقائياً عند تغير السلة
    LaunchedEffect(calculatedTotal, cartItems) {
        if (cartItems.isNotEmpty()) {
            editableTotal = String.format(Locale.US, "%.2f", calculatedTotal)
        } else {
            editableTotal = ""
            customerName = ""
            prescriptionUri = null
            cartPrices.clear()
        }
    }

    val takePrescriptionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) Toast.makeText(context, "تم إرفاق الوصفة", Toast.LENGTH_SHORT).show()
        else prescriptionUri = null
    }

    fun createImageUri(): Uri {
        val imageFile = File(context.cacheDir, "temp_prescription_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imageFile)
    }

    Scaffold(
        bottomBar = {
            // الشريط السفلي الثابت الجديد
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // الصف الأول: اسم المريض + الوصفة
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = customerName,
                            onValueChange = { customerName = it },
                            placeholder = { Text("اسم المريض (اختياري)", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        IconButton(
                            onClick = {
                                val uri = createImageUri()
                                prescriptionUri = uri
                                takePrescriptionLauncher.launch(uri)
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .background(if (prescriptionUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "وصفة",
                                tint = if (prescriptionUri != null) Color.White else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // الصف الثاني: المجموع (قابل للتعديل) + زر الحفظ
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = editableTotal,
                            onValueChange = { editableTotal = it },
                            label = { Text("المجموع (ل.س)", fontSize = 12.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Button(
                            onClick = {
                                if (cartItems.isEmpty()) return@Button
                                isProcessing = true
                                scope.launch {
                                    val finalImagePath = if (prescriptionUri != null) {
                                        withContext(Dispatchers.IO) { compressAndSaveImage(context, prescriptionUri!!) }
                                    } else null
// الكود الجديد
                                    viewModel.checkout(
                                        customerName = customerName.ifBlank { null },
                                        prescriptionPath = finalImagePath,
                                        onError = { errorMessage ->
                                            isProcessing = false // إعادة الزر للعمل
                                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show() // إظهار الخطأ
                                        },
                                        onSuccess = { invoiceNumber ->
                                            isProcessing = false
                                            viewModel.clearCart()
                                            Toast.makeText(context, "تم البيع! رقم الفاتورة: $invoiceNumber", Toast.LENGTH_LONG).show()
                                            // ... أي شيء آخر (مثلاً إغلاق النافذة)
                                        }
                                    )
                                }
                            },
                            enabled = cartItems.isNotEmpty() && !isProcessing,
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isProcessing) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            else Text("حفظ الفاتورة", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // --- الكاميرا ---
            if (isScanning) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    if (hasCameraPermission) {
                        EmbeddedBarcodeScanner { barcode ->
                            viewModel.addToCartByBarcode(barcode) { success, message ->
                                if (success) vibratePhone(context)
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Text("الرجاء إعطاء صلاحية الكاميرا", color = Color.White, modifier = Modifier.align(Alignment.Center))
                    }

                    IconButton(
                        onClick = { isScanning = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Red.copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }
            } else {
                Button(
                    onClick = {
                        if (hasCameraPermission) isScanning = true
                        else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("فتح الكاميرا لمسح الدواء")
                }
            }

            // --- البحث الذكي ---
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("ابحث عن دواء بالاسم...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color.Gray)
                            }
                        }
                    }
                )

                if (searchQuery.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 65.dp)
                            .heightIn(max = 250.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        if (filteredItems.isEmpty()) {
                            Text("لا توجد نتائج", modifier = Modifier.padding(16.dp), color = Color.Gray)
                        } else {
                            LazyColumn {
                                items(filteredItems) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.addToCart(item, 1)
                                                viewModel.updateSearchQuery("")
                                                vibratePhone(context)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Medication, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("المخزون: ${item.currentStock} | ${item.company ?: ""}", fontSize = 12.sp, color = Color.Gray)
                                        }
                                    }
                                    HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text("سلة الفاتورة", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, color = Color.Gray)

            // --- قائمة السلة ---
            if (cartItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("السلة فارغة", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cartItems.entries.toList(), key = { it.key.itemId }) { entry ->
                        val item = entry.key
                        val qty = entry.value
                        // تمرير دالة لجلب السعر وتحديث المجموع
                        CartItemRow(
                            item = item,
                            qty = qty,
                            viewModel = viewModel,
                            onIncrease = { viewModel.addToCart(item, 1) },
                            onRemove = { viewModel.removeFromCart(item) },
                            onPriceCalculated = { itemId, totalPrice ->
                                cartPrices[itemId] = totalPrice // حفظ السعر لحساب المجموع
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CartItemRow(
    item: ItemEntity,
    qty: Int,
    viewModel: PharmacyViewModel,
    onIncrease: () -> Unit,
    onRemove: () -> Unit,
    onPriceCalculated: (String, Double) -> Unit // 👈 أضفنا itemId هنا
) {
    // جلب سعر الدواء من الدفعة الأولى
    val batches by viewModel.getBatchesForItem(item.itemId).collectAsState(initial = emptyList())
    val unitPrice = batches.firstOrNull()?.salePrice ?: 0.0
    val totalPrice = unitPrice * qty

    // إرسال السعر للشاشة الأم لتحديث المجموع
    LaunchedEffect(totalPrice) {
        onPriceCalculated(item.itemId, totalPrice)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "السعر: ${String.format(Locale.US, "%.2f", unitPrice)} | الإجمالي: ${String.format(Locale.US, "%.2f", totalPrice)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red)
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("$qty", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }

                IconButton(onClick = onIncrease, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "زيادة", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun EmbeddedBarcodeScanner(onBarcodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val barcodeView = remember {
        DecoratedBarcodeView(context).apply {
            barcodeView.decoderFactory = DefaultDecoderFactory(
                listOf(BarcodeFormat.QR_CODE, BarcodeFormat.CODE_128, BarcodeFormat.EAN_13, BarcodeFormat.EAN_8)
            )
            cameraSettings.isContinuousFocusEnabled = true
        }
    }

    var lastScannedCode by remember { mutableStateOf("") }
    var lastScannedTime by remember { mutableStateOf(0L) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> barcodeView.resume()
                Lifecycle.Event.ON_PAUSE -> barcodeView.pause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            barcodeView.pause()
        }
    }

    LaunchedEffect(Unit) {
        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                val code = result?.text ?: return
                val currentTime = System.currentTimeMillis()

                if (code != lastScannedCode || currentTime - lastScannedTime > 2000) {
                    lastScannedCode = code
                    lastScannedTime = currentTime
                    onBarcodeScanned(code)
                }
            }
            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
        })
    }

    AndroidView(
        factory = { barcodeView },
        modifier = Modifier.fillMaxSize()
    )
}

fun compressAndSaveImage(context: Context, imageUri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(imageUri)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val maxDimension = 800f
        val ratio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
        val newWidth: Int
        val newHeight: Int

        if (ratio > 1) {
            newWidth = maxDimension.toInt()
            newHeight = (maxDimension / ratio).toInt()
        } else {
            newHeight = maxDimension.toInt()
            newWidth = (maxDimension * ratio).toInt()
        }

        val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)

        val prescriptionsDir = File(context.filesDir, "prescriptions")
        if (!prescriptionsDir.exists()) prescriptionsDir.mkdirs()

        val fileName = "presc_${System.currentTimeMillis()}.jpg"
        val file = File(prescriptionsDir, fileName)

        val outputStream = FileOutputStream(file)
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream)
        outputStream.flush()
        outputStream.close()

        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}