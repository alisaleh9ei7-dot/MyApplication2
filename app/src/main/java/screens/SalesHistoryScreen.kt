package com.example.myapplication.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.myapplication.data.local.entity.SaleHistoryEntity
import com.example.myapplication.ui.PharmacyViewModel
import com.example.myapplication.ui.components.DatePickerField
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.shape.RoundedCornerShape

// كلاس لتجميع أصناف الفاتورة الواحدة في كائن واحد
data class InvoiceGroup(
    val invoiceRef: String,
    val date: Long,
    val customerName: String?,
    val prescriptionPath: String?,
    val items: List<SaleHistoryEntity>
) {
    val totalAmount: Double get() = items.sumOf { it.totalAmount }
    val totalProfit: Double get() = items.sumOf { it.profit ?: 0.0 }
    val itemCount: Int get() = items.size
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesHistoryScreen(viewModel: PharmacyViewModel) {
    val context = LocalContext.current
    val lazyPagingItems = viewModel.pagedSalesHistory.collectAsLazyPagingItems()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    // حالة فتح التفاصيل
    var selectedInvoice by remember { mutableStateOf<InvoiceGroup?>(null) }
    // حالة الحذف والتعديل
    var saleToDelete by remember { mutableStateOf<SaleHistoryEntity?>(null) }
    var saleToEdit by remember { mutableStateOf<SaleHistoryEntity?>(null) }

    // تجميع المبيعات في فواتير بناءً على رقم الفاتورة (invoiceRef)
    val groupedInvoices = remember(lazyPagingItems.itemSnapshotList.items) {
        lazyPagingItems.itemSnapshotList.items
            .filterNotNull()
            .groupBy { it.invoiceRef ?: it.saleId.toString() }  // إذا لم يوجد رقم فاتورة، نستخدم رقم البيع
            .map { entry ->
                val ref = entry.key
                val itemsList = entry.value
                InvoiceGroup(
                    invoiceRef = ref,
                    date = itemsList.first().saleDate,
                    customerName = itemsList.first().customerName,
                    prescriptionPath = itemsList.firstOrNull { !it.prescriptionImagePath.isNullOrBlank() }?.prescriptionImagePath,
                    items = itemsList
                )
            }.sortedByDescending { it.date } // الأحدث أولاً
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // --- شريط البحث ---
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = { Text("ابحث باسم المريض أو رقم الفاتورة...", fontSize = 12.sp) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح", tint = Color.Gray)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (groupedInvoices.isEmpty() && lazyPagingItems.loadState.refresh is LoadState.NotLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد فواتير مطابقة", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // --- قائمة الفواتير (البطاقات) ---
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(groupedInvoices, key = { it.invoiceRef }) { invoice ->
                    InvoiceCard(invoice = invoice, dateFormat = dateFormat, onClick = { selectedInvoice = invoice })
                }

                item {
                    if (lazyPagingItems.loadState.append is LoadState.Loading) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }

    // --- نافذة التفاصيل المنبثقة من الأسفل (Bottom Sheet) ---
    if (selectedInvoice != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedInvoice = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            InvoiceDetailsContent(
                invoice = selectedInvoice!!,
                dateFormat = dateFormat,
                onClose = { selectedInvoice = null },
                onDeleteItem = { saleToDelete = it },
                onEditItem = { saleToEdit = it },
                onSharePdf = { shareInvoicePdf(context, selectedInvoice!!) }
            )
        }
    }

    // --- نافذة حذف الصنف ---
    if (saleToDelete != null) {
        AlertDialog(
            onDismissRequest = { saleToDelete = null },
            title = { Text("تأكيد الحذف") },
            text = { Text("هل أنت متأكد من حذف هذا الصنف من الفاتورة وإرجاع الكمية للمخزون؟") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteSaleAndRestock(saleToDelete!!.saleId) {
                        Toast.makeText(context, "تم الحذف بنجاح", Toast.LENGTH_SHORT).show()
                    }
                    saleToDelete = null
                    selectedInvoice = null // إغلاق النافذة السفلية لتحديث البيانات
                }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { saleToDelete = null }) { Text("إلغاء") } }
        )
    }

    // --- نافذة تعديل الصنف ---
    if (saleToEdit != null) {
        var editQty by remember { mutableStateOf(saleToEdit!!.quantity.toString()) }
        var editPrice by remember { mutableStateOf(saleToEdit!!.unitPrice.toString()) }
        var editDate by remember { mutableStateOf(saleToEdit!!.saleDate) }

        AlertDialog(
            onDismissRequest = { saleToEdit = null },
            title = { Text("تعديل الصنف") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(saleToEdit!!.legacyName ?: "", fontWeight = FontWeight.Bold)
                    OutlinedTextField(value = editQty, onValueChange = { editQty = it }, label = { Text("الكمية") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = editPrice, onValueChange = { editPrice = it }, label = { Text("سعر البيع") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    DatePickerField(label = "تاريخ البيع", initialDate = editDate, onDateSelected = { editDate = it })
                }
            },
            confirmButton = {
                Button(onClick = {
                    val qty = editQty.toDoubleOrNull()
                    val price = editPrice.toDoubleOrNull()
                    if (qty != null && price != null && qty > 0 && price >= 0) {
                        viewModel.editSale(saleToEdit!!.saleId, qty, price, editDate) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            if (success) {
                                saleToEdit = null
                                selectedInvoice = null // إغلاق التفاصيل لتحديث القائمة
                            }
                        }
                    } else {
                        Toast.makeText(context, "أدخل أرقاماً صحيحة", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { saleToEdit = null }) { Text("إلغاء") } }
        )
    }
}

// 👇 مكون البطاقة الرئيسية للفاتورة 👇
@Composable
fun InvoiceCard(invoice: InvoiceGroup, dateFormat: SimpleDateFormat, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // التاريخ واسم المريض
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(dateFormat.format(Date(invoice.date)), fontSize = 11.sp, color = Color.Gray)
                Text(invoice.customerName ?: "زبون نقدي", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // الملخص
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${invoice.itemCount} أصناف", fontSize = 12.sp, color = Color.Gray)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (invoice.prescriptionPath != null) {
                        Icon(Icons.Default.Image, contentDescription = "وصفة", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("${String.format(Locale.US, "%.2f", invoice.totalAmount)} ل.س", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// 👇 مكون محتوى نافذة التفاصيل (Bottom Sheet) 👇
@Composable
fun InvoiceDetailsContent(
    invoice: InvoiceGroup,
    dateFormat: SimpleDateFormat,
    onClose: () -> Unit,
    onDeleteItem: (SaleHistoryEntity) -> Unit,
    onEditItem: (SaleHistoryEntity) -> Unit,
    onSharePdf: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp)) {

        // الرأس
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("تفاصيل الفاتورة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("رقم: ${invoice.invoiceRef}", fontSize = 12.sp, color = Color.Gray)
                Text("التاريخ: ${dateFormat.format(Date(invoice.date))}", fontSize = 12.sp, color = Color.Gray)
            }
            if (invoice.prescriptionPath != null) {
                IconButton(onClick = { openPrescriptionImage(context, invoice.prescriptionPath) }) {
                    Icon(Icons.Default.Image, contentDescription = "الوصفة", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()

        // قائمة الأدوية
        LazyColumn(modifier = Modifier.weight(1f, fill = false).heightIn(max = 350.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(invoice.items) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.legacyName ?: "-", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("السعر: ${item.unitPrice} | الربح: ${item.profit ?: 0.0}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("الكمية: ${item.quantity}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            IconButton(onClick = { onEditItem(item) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل", modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onDeleteItem(item) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.Red, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // الإجمالي والأرباح
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("إجمالي الأصناف:", color = Color.Gray)
            Text("${invoice.itemCount}", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("إجمالي المبلغ:", color = Color.Gray)
            Text("${String.format(Locale.US, "%.2f", invoice.totalAmount)} ل.س", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("صافي الربح:", color = Color.Gray)
            Text("${String.format(Locale.US, "%.2f", invoice.totalProfit)} ل.س", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // أزرار الإجراءات
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSharePdf, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("مشاركة PDF")
            }
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                Text("إغلاق")
            }
        }
    }
}

// دالة عرض صورة الوصفة
fun openPrescriptionImage(context: Context, imagePath: String) {
    try {
        val file = File(imagePath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "لم يتم العثور على الصورة", Toast.LENGTH_SHORT).show()
    }
}

// دالة إنشاء ومشاركة الـ PDF (مُحدثة لتدعم الفاتورة كاملة)
fun shareInvoicePdf(context: Context, invoice: InvoiceGroup) {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas

    val titlePaint = Paint().apply { textSize = 24f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
    val textPaint = Paint().apply { textSize = 16f; textAlign = Paint.Align.RIGHT }
    val pageWidth = 595f; val margin = 40f
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    var currentY = 120f

    canvas.drawText("فاتورة مبيعات", pageWidth / 2, 60f, titlePaint)
    canvas.drawText("رقم الفاتورة: ${invoice.invoiceRef}", pageWidth - margin, currentY, textPaint)
    currentY += 30f
    canvas.drawText("التاريخ: ${dateFormat.format(Date(invoice.date))}", pageWidth - margin, currentY, textPaint)

    if (!invoice.customerName.isNullOrBlank()) {
        currentY += 30f
        canvas.drawText("المريض: ${invoice.customerName}", pageWidth - margin, currentY, textPaint)
    }

    currentY += 40f
    canvas.drawText("الأصناف:", pageWidth - margin, currentY, Paint(textPaint).apply { isFakeBoldText = true })

    invoice.items.forEach { item ->
        currentY += 30f
        canvas.drawText("- ${item.legacyName} | كمية: ${item.quantity} | سعر: ${item.unitPrice}", pageWidth - margin, currentY, textPaint)
    }

    currentY += 40f
    textPaint.isFakeBoldText = true; textPaint.textSize = 20f
    canvas.drawText("الإجمالي: ${String.format(Locale.US, "%.2f", invoice.totalAmount)}", pageWidth - margin, currentY, textPaint)

    pdfDocument.finishPage(page)

    val file = File(context.cacheDir, "Invoice_${invoice.invoiceRef}.pdf")
    try { pdfDocument.writeTo(FileOutputStream(file)) } catch (e: Exception) { e.printStackTrace() } finally { pdfDocument.close() }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "مشاركة الفاتورة عبر..."))
}