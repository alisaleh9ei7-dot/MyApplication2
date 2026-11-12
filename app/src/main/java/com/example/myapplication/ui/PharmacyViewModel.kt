package com.example.myapplication.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.local.AppDatabase
import com.example.myapplication.data.local.dao.*
import com.example.myapplication.data.local.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.myapplication.data.local.dao.CompanyDao
import com.example.myapplication.data.local.entity.CompanyEntity
import androidx.room.withTransaction

// كلاس لحفظ إحصائيات لوحة التحكم
data class DashboardStats(
    val todaySales: Double = 0.0,
    val todayProfit: Double = 0.0,
    val inventoryValue: Double = 0.0,
    val monthExpenses: Double = 0.0,
    val lowStockCount: Int = 0,
    val expiringSoonCount: Int = 0,
    val monthSales: Double = 0.0,
    val monthProfit: Double = 0.0,
    val monthOfferProfit: Double = 0.0
)

data class DailySales(val dayName: String, val amount: Double)

// كلاس لحفظ إحصائيات التقرير الشهري
data class MonthlyReportStats(
    val totalSales: Double = 0.0,
    val totalProfit: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0
)

class PharmacyViewModel(
    private val itemDao: ItemDao,
    private val batchDao: BatchDao,
    private val saleDao: SaleDao,
    private val stockMovementDao: StockMovementDao,
    private val expenseDao: ExpenseDao,
    private val supplierLedgerDao: SupplierLedgerDao,
    private val settingDao: SettingDao,
    private val dashboardDao: DashboardDao,
    private val companyDao: CompanyDao,
    private val reportDao: ReportDao,
    private val db: AppDatabase // 👈 أضفنا قاعدة البيانات هنا
) : ViewModel() {

    // --- التقارير ---
    private val _nearExpiryReport = MutableStateFlow<List<NearExpiryReportItem>>(emptyList())
    val nearExpiryReport: StateFlow<List<NearExpiryReportItem>> = _nearExpiryReport

    private val _movementReport = MutableStateFlow<List<MovementReportItem>>(emptyList())
    val movementReport: StateFlow<List<MovementReportItem>> = _movementReport

    fun loadNearExpiryReport() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = System.currentTimeMillis()
            val futureDate = today + (90L * 24 * 60 * 60 * 1000)
            val result = reportDao.getNearExpiryBatches(today, futureDate)
            withContext(Dispatchers.Main) { _nearExpiryReport.value = result }
        }
    }

    fun loadMovementReport(monthStr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dateFormat = SimpleDateFormat("yyyy-MM", Locale.US)
            val startDate = dateFormat.parse(monthStr)?.time ?: 0L
            val cal = Calendar.getInstance().apply { timeInMillis = startDate }
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
            val endDate = cal.timeInMillis

            val result = reportDao.getMovementReport(startDate, endDate)
            withContext(Dispatchers.Main) { _movementReport.value = result }
        }
    }

    // --- تقرير الأدوية المنتهية ---
    private val _expiredReport = MutableStateFlow<List<ExpiredReportItem>>(emptyList())
    val expiredReport: StateFlow<List<ExpiredReportItem>> = _expiredReport

    fun loadExpiredReport() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = System.currentTimeMillis()
            val result = reportDao.getExpiredBatches(today)
            withContext(Dispatchers.Main) { _expiredReport.value = result }
        }
    }

    // --- تقرير المبيعات والأرباح الشهرية ---
    private val _monthlyReportStats = MutableStateFlow(MonthlyReportStats())
    val monthlyReportStats: StateFlow<MonthlyReportStats> = _monthlyReportStats

    fun loadMonthlyReportStats(monthStr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val dateFormat = SimpleDateFormat("yyyy-MM", Locale.US)
            val startDate = dateFormat.parse(monthStr)?.time ?: 0L
            val cal = Calendar.getInstance().apply { timeInMillis = startDate }
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
            cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59)
            val endDate = cal.timeInMillis

            val sales = dashboardDao.getSalesBetween(startDate, endDate) ?: 0.0
            val profit = dashboardDao.getProfitBetween(startDate, endDate) ?: 0.0
            val expenses = reportDao.getExpensesBetween(startDate, endDate) ?: 0.0

            val stats = MonthlyReportStats(
                totalSales = sales,
                totalProfit = profit,
                totalExpenses = expenses,
                netProfit = profit - expenses
            )
            withContext(Dispatchers.Main) { _monthlyReportStats.value = stats }
        }
    }

    // --- لوحة التحكم ---
    private val _dashboardStats = MutableStateFlow(DashboardStats())
    val dashboardStats: StateFlow<DashboardStats> = _dashboardStats

    private val _weeklySalesData = MutableStateFlow<List<DailySales>>(emptyList())
    val weeklySalesData: StateFlow<List<DailySales>> = _weeklySalesData

    private val _recentSales = MutableStateFlow<List<SaleHistoryEntity>>(emptyList())
    val recentSales: StateFlow<List<SaleHistoryEntity>> = _recentSales

    fun loadDashboardStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val now = Calendar.getInstance()
            val startOfToday = now.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val endOfToday = now.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59); set(Calendar.MILLISECOND, 999) }.timeInMillis
            val startOfMonth = now.apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
            val monthKey = SimpleDateFormat("yyyyMM", Locale.US).format(Date()).toLongOrNull() ?: 0L
            val futureDate = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 90) }.timeInMillis

            val stats = DashboardStats(
                todaySales = dashboardDao.getSalesBetween(startOfToday, endOfToday) ?: 0.0,
                todayProfit = dashboardDao.getProfitBetween(startOfToday, endOfToday) ?: 0.0,
                inventoryValue = dashboardDao.getTotalInventoryValue() ?: 0.0,
                monthExpenses = dashboardDao.getExpensesForMonth(monthKey) ?: 0.0,
                lowStockCount = dashboardDao.getLowStockCount(),
                expiringSoonCount = dashboardDao.getExpiringSoonCount(startOfToday, futureDate),
                monthSales = dashboardDao.getSalesBetween(startOfMonth, endOfToday) ?: 0.0,
                monthProfit = dashboardDao.getProfitBetween(startOfMonth, endOfToday) ?: 0.0,
                monthOfferProfit = dashboardDao.getOfferProfitBetween(startOfMonth, endOfToday) ?: 0.0
            )

            // --- جلب بيانات الرسم البياني لآخر 7 أيام ---
            val sevenDaysAgo = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -6)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            }.timeInMillis

            val weeklySales = dashboardDao.getSalesSince(sevenDaysAgo)
            val chartData = mutableListOf<DailySales>()

            for (i in 6 downTo 0) {
                val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
                val dayFormat = SimpleDateFormat("EEEE", Locale("ar")).format(day.time)

                val startOfDay = day.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis
                val endOfDay = day.apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59) }.timeInMillis

                val dayTotal = weeklySales.filter { it.saleDate in startOfDay..endOfDay }.sumOf { it.totalAmount }
                chartData.add(DailySales(dayFormat, dayTotal))
            }

            // --- جلب أحدث المبيعات ---
            val recent = dashboardDao.getRecentSales()

            // --- تحديث الواجهة بكل البيانات ---
            withContext(Dispatchers.Main) {
                _dashboardStats.value = stats
                _weeklySalesData.value = chartData
                _recentSales.value = recent
            }
        }
    }

    // --- البحث والأصناف ---
    val searchQuery = MutableStateFlow("")
    fun updateSearchQuery(query: String) { searchQuery.value = query }

    val items: StateFlow<List<ItemEntity>> = itemDao.getAllItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // قائمة الشركات للمنسدلة
    val companies: StateFlow<List<CompanyEntity>> = companyDao.getAllCompanies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val filteredItems: StateFlow<List<ItemEntity>> = itemDao.getAllItems().combine(searchQuery) { items, query ->
        if (query.isBlank()) items else items.filter { it.name.contains(query, true) || it.itemId.contains(query, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- السلة ---
    private val _cartItems = MutableStateFlow<Map<ItemEntity, Int>>(emptyMap())
    val cartItems: StateFlow<Map<ItemEntity, Int>> = _cartItems

    fun addToCart(item: ItemEntity, quantity: Int = 1) { val c = _cartItems.value.toMutableMap(); c[item] = (c[item] ?: 0) + quantity; _cartItems.value = c }

    // 1. البحث عن دواء بالباركود (للوحة التحكم)
    fun getItemDetailsByBarcode(barcode: String, onResult: (ItemEntity?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = itemDao.getItemByBarcode(barcode)
            withContext(Dispatchers.Main) { onResult(item) }
        }
    }

    // 2. إضافة دواء للسلة بالباركود (لشاشة المبيعات)
    fun addToCartByBarcode(barcode: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = itemDao.getItemByBarcode(barcode)
            if (item != null) {
                if (item.currentStock > 0) {
                    withContext(Dispatchers.Main) {
                        addToCart(item, 1)
                        onResult(true, "تمت إضافة ${item.name} للسلة")
                    }
                } else {
                    withContext(Dispatchers.Main) { onResult(false, "الصنف موجود ولكن الكمية صفر!") }
                }
            } else {
                withContext(Dispatchers.Main) { onResult(false, "لم يتم العثور على دواء بهذا الباركود") }
            }
        }
    }

    fun removeFromCart(item: ItemEntity) { val c = _cartItems.value.toMutableMap(); c.remove(item); _cartItems.value = c }
    fun clearCart() { _cartItems.value = emptyMap() }

    // --- الدفعات والمبيعات والمصاريف والإعدادات والموردين ---
    fun getBatchesForItem(itemId: String) = batchDao.getAvailableBatchesForItem(itemId)

    // جلب سجل المبيعات بنظام Paging مع دعم البحث والتحديث اللحظي
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedSalesHistory: Flow<PagingData<SaleHistoryEntity>> = searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            val source = if (query.isBlank()) {
                saleDao.getPagedSalesHistory()
            } else {
                saleDao.getPagedSalesHistorySearch(query)
            }
            Pager(
                config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                pagingSourceFactory = { source }
            ).flow
        }
        .cachedIn(viewModelScope)

    // دالة مساعدة لجلب سعر البيع للدواء (لأول دفعة متاحة)
    fun getPriceForItem(itemId: String) = batchDao.getAvailableBatchesForItem(itemId).map { batches ->
        batches.firstOrNull()?.salePrice ?: 0.0
    }

    val expenses: StateFlow<List<ExpenseEntity>> = expenseDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings: StateFlow<List<SettingEntity>> = settingDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val supplierLedger: StateFlow<List<SupplierLedgerEntity>> = supplierLedgerDao.getAllLedger().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // جلب أسماء المرضى للاقتراحات
    val customerNames: StateFlow<List<String>> = saleDao.getAllCustomerNames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // جلب ملخص أدوية المريض
    fun getPatientMedications(name: String): Flow<List<PatientMedicationSummary>> {
        return saleDao.getPatientMedications(name)
    }

    // --- إدارة الموردين ---
    fun getSupplierInvoices(name: String): Flow<List<SupplierLedgerEntity>> = supplierLedgerDao.getInvoicesBySupplier(name)
    fun deleteSupplier(name: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            if (supplierLedgerDao.getInvoiceCount(name) > 0) withContext(Dispatchers.Main) { onResult(false, "لا يمكن حذف مورد لديه فواتير مسجلة!") }
            else { supplierLedgerDao.deleteSupplierRecords(name); withContext(Dispatchers.Main) { onResult(true, "تم حذف المورد بنجاح") } }
        }
    }
    fun updateSupplier(oldName: String, newName: String, newNotes: String) {
        viewModelScope.launch(Dispatchers.IO) { supplierLedgerDao.updateSupplier(oldName, newName, newNotes) }
    }

    // --- أدوات المخزون ---
    fun updateReorderLevel(itemId: String, level: Double) { viewModelScope.launch(Dispatchers.IO) { itemDao.updateReorderLevel(itemId, level) } }
    fun updateSellingPrice(itemId: String, newPrice: Double) { viewModelScope.launch(Dispatchers.IO) { batchDao.updateSellingPriceForItem(itemId, newPrice) } }
    fun repairInventoryBalances(onSuccess: () -> Unit) { viewModelScope.launch(Dispatchers.IO) { itemDao.repairAllBalances(); withContext(Dispatchers.Main) { onSuccess() } } }
    // دالة إتلاف دواء (تالف أو منتهي الصلاحية)
    fun writeOffStock(batchId: String, itemId: String, qtyToWriteOff: Double, reason: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val batch = batchDao.getBatchById(batchId)
                if (batch == null) {
                    withContext(Dispatchers.Main) { onResult(false, "الدفعة غير موجودة") }
                    return@launch
                }
                if (batch.currentQty < qtyToWriteOff) {
                    withContext(Dispatchers.Main) { onResult(false, "الكمية المطلوبة أكبر من المتوفر في هذه الدفعة") }
                    return@launch
                }

                // 1. خصم الكمية من الدفعة
                batchDao.deductBatchQuantity(batchId, qtyToWriteOff)

                // 2. خصم الكمية من إجمالي الصنف
                itemDao.deductItemStock(itemId, qtyToWriteOff)

                // 3. تسجيل حركة مخزون (بدون التأثير على المبيعات والأرباح)
                stockMovementDao.insertMovement(StockMovementEntity(
                    movementDate = System.currentTimeMillis(),
                    movementType = "إتلاف",
                    batchId = batchId,
                    itemId = itemId,
                    quantity = -qtyToWriteOff,
                    reference = "WRITE-OFF-${System.currentTimeMillis().toString().takeLast(5)}",
                    note = reason
                ))

                withContext(Dispatchers.Main) { onResult(true, "تم إتلاف الكمية بنجاح") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "حدث خطأ: ${e.message}") }
            }
        }
    }

    // --- الإعدادات والمصاريف ---
    fun saveSetting(key: String, value: String) { viewModelScope.launch(Dispatchers.IO) { settingDao.upsert(SettingEntity(key, value, "تعديل من التطبيق")) } }
    fun addSetting(key: String, value: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = settingDao.insertIfAbsent(SettingEntity(key, value, "إضافة جديدة"))
            withContext(Dispatchers.Main) { onResult(result != -1L) }
        }
    }
    fun addExpense(amount: Double, category: String, description: String, dateLong: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val latestId = expenseDao.getLatestExpenseId()
            val nextNumber = if (latestId == null) 1 else (latestId.replace("EXP-", "").toIntOrNull() ?: 0) + 1
            val newId = "EXP-" + String.format("%04d", nextNumber)
            val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(dateLong)).toLongOrNull() ?: 0L
            expenseDao.insert(ExpenseEntity(newId, dateLong, category, description, amount, monthKey))
        }
    }
    fun deleteExpense(id: String) { viewModelScope.launch(Dispatchers.IO) { expenseDao.deleteById(id) } }

    fun addSupplierRecord(name: String, notes: String, orderValue: Double, paidAmount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            supplierLedgerDao.insertRecord(SupplierLedgerEntity(opDate = System.currentTimeMillis(), supplierName = name, orderValue = orderValue, invoiceNo = "INV-" + System.currentTimeMillis().toString().takeLast(5), paidAmount = paidAmount, debt = orderValue - paidAmount, notes = notes))
        }
    }

    // --- البيع الآمن (Checkout with Transaction) ---
    private val _isCheckingOut = MutableStateFlow(false)
    val isCheckingOut: StateFlow<Boolean> = _isCheckingOut

    fun checkout(customerName: String?, prescriptionPath: String?,
                 onError: (String) -> Unit = {}, onSuccess: (String) -> Unit) {
        val cart = _cartItems.value
        if (cart.isEmpty() || !_isCheckingOut.compareAndSet(false, true)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val invoice = db.withTransaction {
                    val now = System.currentTimeMillis()
                    val invoiceNumber = "INV-" + SimpleDateFormat("yyyyMMdd-HHmmssSSS", Locale.US).format(Date(now))
                    val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(now)).toLongOrNull() ?: 0L

                    for ((item, cartQty) in cart) {
                        var left = cartQty.toDouble()

                        // جلب الدفعات الصالحة فقط (غير منتهية)
                        for (batch in batchDao.getSellableBatchesSync(item.itemId, now)) {
                            if (left <= 0.0) break
                            val take = minOf(batch.currentQty, left)

                            // خصم ذري (آمن)
                            if (batchDao.deductIfAvailable(batch.batchId, take) != 1) {
                                throw Exception("تغيّرت كمية ${item.name}، أعد المحاولة")
                            }

                            // تسجيل الفاتورة
                            saleDao.insertSale(SaleHistoryEntity(
                                saleDate = now, batchId = batch.batchId, legacyName = item.name,
                                quantity = take, unitPrice = batch.salePrice, totalAmount = take * batch.salePrice,
                                profit = take * (batch.salePrice - batch.purchasePrice),
                                offerProfit = take * (batch.purchasePrice - batch.unitCost),
                                month = monthKey, invoiceRef = invoiceNumber,
                                customerName = customerName, prescriptionImagePath = prescriptionPath
                            ))

                            // تسجيل حركة المخزون
                            stockMovementDao.insertMovement(StockMovementEntity(
                                movementDate = now, movementType = "بيع", batchId = batch.batchId,
                                itemId = item.itemId, quantity = -take, reference = invoiceNumber, note = "بيع عبر التطبيق"
                            ))

                            left -= take
                        }

                        if (left > 0.0) {
                            throw Exception("الكمية غير كافية أو منتهية الصلاحية: ${item.name}")
                        }

                        // خصم الإجمالي من جدول الأصناف
                        itemDao.deductItemStock(item.itemId, cartQty.toDouble())
                    }
                    invoiceNumber
                }
                clearCart()
                withContext(Dispatchers.Main) { onSuccess(invoice) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "فشلت عملية البيع") }
            } finally {
                _isCheckingOut.value = false
            }
        }
    }

    // --- حذف وتعديل فاتورة بيع ---
    fun deleteSaleAndRestock(saleId: Long, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sale = saleDao.getSaleById(saleId)
                if (sale != null && sale.batchId != null) {
                    batchDao.restoreBatchQuantity(sale.batchId, sale.quantity)
                    saleDao.deleteSaleMovement(sale.batchId, sale.invoiceRef)
                    saleDao.deleteSaleById(saleId)
                }
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun editSale(saleId: Long, newQty: Double, newPrice: Double, newDate: Long, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sale = saleDao.getSaleById(saleId)
                if (sale == null || sale.batchId == null) { withContext(Dispatchers.Main) { onResult(false, "الفاتورة غير موجودة") }; return@launch }
                val batch = batchDao.getBatchById(sale.batchId)
                if (batch == null) { withContext(Dispatchers.Main) { onResult(false, "الدفعة غير موجودة") }; return@launch }

                val deltaQty = newQty - sale.quantity
                if (deltaQty > 0 && batch.currentQty < deltaQty) { withContext(Dispatchers.Main) { onResult(false, "الكمية المطلوبة أكبر من المتوفر!") }; return@launch }

                batchDao.deductBatchQuantity(batch.batchId, deltaQty)
                val historicalCostPerUnit = if (sale.quantity > 0) (sale.totalAmount - (sale.profit ?: 0.0)) / sale.quantity else 0.0
                val newTotal = newQty * newPrice
                val newProfit = newTotal - (newQty * historicalCostPerUnit)
                saleDao.insertSale(sale.copy(
                    quantity = newQty,
                    unitPrice = newPrice,
                    totalAmount = newTotal,
                    profit = newProfit,
                    saleDate = newDate,
                    month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(newDate)).toLongOrNull() ?: 0L
                ))
                stockMovementDao.updateSaleMovementQuantity(batch.batchId, sale.invoiceRef, -newQty)
                itemDao.repairSingleBalance(batch.itemId)
                withContext(Dispatchers.Main) { onResult(true, "تم التعديل بنجاح") }
            } catch (e: Exception) { withContext(Dispatchers.Main) { onResult(false, "حدث خطأ: ${e.message}") } }
        }
    }

    // --- المشتريات ---
    fun processPurchase(supplierName: String, invoiceNumber: String, amountPaid: Double, items: List<PurchaseItemInput>, onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            var totalInvoice = 0.0
            for (item in items) {
                val existingItem = itemDao.getItemByName(item.name)
                var finalItemId = existingItem?.itemId

                if (existingItem == null) {
                    finalItemId = "ITEM-" + System.currentTimeMillis().toString().takeLast(4)
                    itemDao.insertItems(listOf(ItemEntity(
                        itemId = finalItemId,
                        name = item.name,
                        dosageForm = item.form,
                        company = item.company,
                        currentStock = 0.0,
                        reorderLevel = 2.0,
                        barcode = item.barcode
                    )))
                } else {
                    if (item.barcode != null && existingItem.barcode == null) {
                        itemDao.updateBarcode(finalItemId!!, item.barcode)
                    }
                }

                val totalUnits = item.paidUnits + item.freeUnits
                val lineTotal = item.paidUnits * item.purchasePrice
                totalInvoice += lineTotal
                val effectiveUnitCost = if (totalUnits > 0) lineTotal / totalUnits else 0.0
                val offerString = if (item.freeUnits == 0.0) "${item.paidUnits.toInt()}+0" else "${item.paidUnits.toInt()}+${item.freeUnits.toInt()}"
                val matchingBatch = batchDao.getMatchingBatch(finalItemId ?: "", item.expiryDate, offerString)

                if (matchingBatch != null) {
                    batchDao.insertBatches(listOf(matchingBatch.copy(currentQty = matchingBatch.currentQty + totalUnits, totalUnits = matchingBatch.totalUnits + totalUnits, dataStatus = "محدثة")))
                } else {
                    val batchId = "$finalItemId|${SimpleDateFormat("yyyyMM", Locale.US).format(Date(item.expiryDate))}|${System.currentTimeMillis().toString().takeLast(3)}"
                    batchDao.insertBatches(listOf(BatchEntity(batchId, finalItemId ?: "", item.name, item.form, item.company, offerString, totalUnits, item.expiryDate, item.purchasePrice, item.paidUnits, item.freeUnits, totalUnits, effectiveUnitCost, item.salePrice, null, null, "جديدة")))
                    stockMovementDao.insertMovement(StockMovementEntity(
                        movementDate = System.currentTimeMillis(), movementType = "شراء", batchId = batchId,
                        itemId = finalItemId, quantity = totalUnits, reference = invoiceNumber, note = "شراء من المورد: $supplierName"
                    ))
                }
                if (finalItemId != null) itemDao.deductItemStock(finalItemId, -totalUnits)
            }
            addSupplierRecord(supplierName, "فاتورة رقم: $invoiceNumber", totalInvoice, amountPaid)
            withContext(Dispatchers.Main) { onSuccess() }
        }
    }
}

data class PurchaseItemInput(
    val name: String,
    val form: String,
    val company: String,
    val paidUnits: Double,
    val freeUnits: Double,
    val purchasePrice: Double,
    val salePrice: Double,
    val expiryDate: Long,
    val barcode: String? = null
)

class PharmacyViewModelFactory(
    private val itemDao: ItemDao,
    private val batchDao: BatchDao,
    private val saleDao: SaleDao,
    private val stockMovementDao: StockMovementDao,
    private val expenseDao: ExpenseDao,
    private val supplierLedgerDao: SupplierLedgerDao,
    private val settingDao: SettingDao,
    private val dashboardDao: DashboardDao,
    private val companyDao: CompanyDao,
    private val reportDao: ReportDao,
    private val db: AppDatabase // 👈 أضفناها هنا
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PharmacyViewModel::class.java)) {
            return PharmacyViewModel(
                itemDao, batchDao, saleDao, stockMovementDao,
                expenseDao, supplierLedgerDao, settingDao, dashboardDao,
                companyDao, reportDao, db // 👈 نمررها هنا
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}