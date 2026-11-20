package com.example.myapplication.ui

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
import androidx.room.withTransaction
import com.example.myapplication.utils.PrescriptionStorage
import com.example.myapplication.utils.ExpiryDatePolicy

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

data class MonthlyReportStats(
    val totalSales: Double = 0.0,
    val totalProfit: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val netProfit: Double = 0.0
)

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
    private val db: AppDatabase
) : ViewModel() {

    // --- التقارير الجديدة (الأبطال والديون) ---
    private val _topSellingByQty = MutableStateFlow<List<MovementReportItem>>(emptyList())
    val topSellingByQty: StateFlow<List<MovementReportItem>> = _topSellingByQty

    private val _topSellingByValue = MutableStateFlow<List<MovementReportItem>>(emptyList())
    val topSellingByValue: StateFlow<List<MovementReportItem>> = _topSellingByValue

    private val _topProfit = MutableStateFlow<List<MovementReportItem>>(emptyList())
    val topProfit: StateFlow<List<MovementReportItem>> = _topProfit

    private val _supplierSummary = MutableStateFlow<List<SupplierSummary>>(emptyList())
    val supplierSummary: StateFlow<List<SupplierSummary>> = _supplierSummary

    fun loadAdvancedReports() {
        viewModelScope.launch(Dispatchers.IO) {
            val byQty = reportDao.getTopSellingByQuantity()
            val byValue = reportDao.getTopSellingByValue()
            val byProfit = reportDao.getTopProfitByValue()
            val suppliers = reportDao.getSupplierSummary()

            withContext(Dispatchers.Main) {
                _topSellingByQty.value = byQty
                _topSellingByValue.value = byValue
                _topProfit.value = byProfit
                _supplierSummary.value = suppliers
            }
        }
    }

    // --- التقارير ---
    private val _nearExpiryReport = MutableStateFlow<List<NearExpiryReportItem>>(emptyList())
    val nearExpiryReport: StateFlow<List<NearExpiryReportItem>> = _nearExpiryReport

    private val _movementReport = MutableStateFlow<List<MovementReportItem>>(emptyList())
    val movementReport: StateFlow<List<MovementReportItem>> = _movementReport

    fun loadNearExpiryReport() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = ExpiryDatePolicy.todayDateKey()
            val futureDate = ExpiryDatePolicy.addDays(today, 90)
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

    private val _expiredReport = MutableStateFlow<List<ExpiredReportItem>>(emptyList())
    val expiredReport: StateFlow<List<ExpiredReportItem>> = _expiredReport

    fun loadExpiredReport() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = ExpiryDatePolicy.todayDateKey()
            val result = reportDao.getExpiredBatches(today)
            withContext(Dispatchers.Main) { _expiredReport.value = result }
        }
    }

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
            val expiryToday = ExpiryDatePolicy.todayDateKey()
            val expiryFutureDate = ExpiryDatePolicy.addDays(expiryToday, 90)

            val stats = DashboardStats(
                todaySales = dashboardDao.getSalesBetween(startOfToday, endOfToday) ?: 0.0,
                todayProfit = dashboardDao.getProfitBetween(startOfToday, endOfToday) ?: 0.0,
                inventoryValue = dashboardDao.getTotalInventoryValue(expiryToday) ?: 0.0,
                monthExpenses = dashboardDao.getExpensesForMonth(monthKey) ?: 0.0,
                lowStockCount = dashboardDao.getLowStockCount(),
                expiringSoonCount = dashboardDao.getExpiringSoonCount(expiryToday, expiryFutureDate),
                monthSales = dashboardDao.getSalesBetween(startOfMonth, endOfToday) ?: 0.0,
                monthProfit = dashboardDao.getProfitBetween(startOfMonth, endOfToday) ?: 0.0,
                monthOfferProfit = dashboardDao.getOfferProfitBetween(startOfMonth, endOfToday) ?: 0.0
            )

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

            val recent = dashboardDao.getRecentSales()

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
    val companies: StateFlow<List<CompanyEntity>> = companyDao.getAllCompanies().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredItems: StateFlow<List<ItemEntity>> = itemDao.getAllItems().combine(searchQuery) { items, query ->
        if (query.isBlank()) items else items.filter { it.name.contains(query, true) || it.itemId.contains(query, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _expiringItems = MutableStateFlow<List<ItemEntity>>(emptyList())
    val expiringItems: StateFlow<List<ItemEntity>> = _expiringItems

    private val _expiredItems = MutableStateFlow<List<ItemEntity>>(emptyList())
    val expiredItems: StateFlow<List<ItemEntity>> = _expiredItems

    fun loadExpiryFilters() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = ExpiryDatePolicy.todayDateKey()
            val futureDate = ExpiryDatePolicy.addDays(today, 90)
            val expired = itemDao.getItemsWithExpiredBatches(today)
            val expiring = itemDao.getItemsWithExpiringBatches(today, futureDate)
            withContext(Dispatchers.Main) {
                _expiredItems.value = expired
                _expiringItems.value = expiring
            }
        }
    }

    // --- السلة ---
    private val _cartItems = MutableStateFlow<Map<ItemEntity, Int>>(emptyMap())
    val cartItems: StateFlow<Map<ItemEntity, Int>> = _cartItems

    fun addToCart(item: ItemEntity, quantity: Int = 1) {
        val c = _cartItems.value.toMutableMap()
        c[item] = (c[item] ?: 0) + quantity
        _cartItems.value = c
    }

    fun getItemDetailsByBarcode(barcode: String, onResult: (ItemEntity?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = itemDao.getItemByBarcode(barcode)
            withContext(Dispatchers.Main) { onResult(item) }
        }
    }

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

    fun removeFromCart(item: ItemEntity) {
        val c = _cartItems.value.toMutableMap()
        c.remove(item)
        _cartItems.value = c
    }

    fun clearCart() { _cartItems.value = emptyMap() }

    fun getBatchesForItem(itemId: String) = batchDao.getAvailableBatchesForItem(itemId)
    fun getPriceForItem(itemId: String) = batchDao.getAvailableBatchesForItem(itemId).map { batches ->
        batches.firstOrNull()?.salePrice ?: 0.0
    }

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

    val expenses: StateFlow<List<ExpenseEntity>> = expenseDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings: StateFlow<List<SettingEntity>> = settingDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val supplierLedger: StateFlow<List<SupplierLedgerEntity>> = supplierLedgerDao.getAllLedger().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val supplierNames: StateFlow<List<String>> = supplierLedgerDao.getAllLedger()
        .map { ledger -> ledger.map { it.supplierName }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val customerNames: StateFlow<List<String>> = saleDao.getAllCustomerNames().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getPatientMedications(name: String): Flow<List<PatientMedicationSummary>> = saleDao.getPatientMedications(name)

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

    fun updateReorderLevel(itemId: String, level: Double) { viewModelScope.launch(Dispatchers.IO) { itemDao.updateReorderLevel(itemId, level) } }
    fun updateSellingPrice(itemId: String, newPrice: Double) { viewModelScope.launch(Dispatchers.IO) { batchDao.updateSellingPriceForItem(itemId, newPrice) } }
    fun repairInventoryBalances(onSuccess: () -> Unit) { viewModelScope.launch(Dispatchers.IO) { itemDao.repairAllBalances(); withContext(Dispatchers.Main) { onSuccess() } } }
    fun addNewItem(
        name: String,
        company: String,
        dosageForm: String,
        reorderLevel: Double,
        barcode: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newItemId = "ITEM-" + UUID.randomUUID().toString().take(8)
                itemDao.insertItems(listOf(ItemEntity(
                    itemId = newItemId,
                    name = name,
                    company = company,
                    dosageForm = dosageForm,
                    currentStock = 0.0, // الرصيد يبدأ بـ 0
                    reorderLevel = reorderLevel,
                    barcode = barcode
                )))
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    fun writeOffStock(batchId: String, itemId: String, qtyToWriteOff: Double, reason: String, onResult: (Boolean, String) -> Unit) {
        if (qtyToWriteOff.isNaN() || qtyToWriteOff.isInfinite() || qtyToWriteOff <= 0.0) {
            onResult(false, "الكمية يجب أن تكون أكبر من صفر")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.withTransaction {
                    val batch = batchDao.getBatchById(batchId)
                        ?: throw IllegalStateException("الدفعة غير موجودة")
                    if (batch.itemId != itemId) {
                        throw IllegalStateException("الدفعة لا تخص هذا الصنف")
                    }
                    if (batch.currentQty < qtyToWriteOff) {
                        throw IllegalStateException("الكمية المطلوبة أكبر من المتوفر في هذه الدفعة")
                    }

                    val now = System.currentTimeMillis()
                    batchDao.deductBatchQuantity(batchId, qtyToWriteOff)
                    itemDao.deductItemStock(itemId, qtyToWriteOff)
                    stockMovementDao.insertMovement(StockMovementEntity(
                        movementDate = now,
                        movementType = "إتلاف",
                        batchId = batchId,
                        itemId = itemId,
                        quantity = -qtyToWriteOff,
                        reference = "WRITE-OFF-${now.toString().takeLast(5)}",
                        note = reason
                    ))
                }
                withContext(Dispatchers.Main) { onResult(true, "تم إتلاف الكمية بنجاح") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, e.message ?: "حدث خطأ غير متوقع") }
            }
        }
    }

    fun saveSetting(key: String, value: String) { viewModelScope.launch(Dispatchers.IO) { settingDao.upsert(SettingEntity(key, value, "تعديل من التطبيق")) } }
    fun addSetting(key: String, value: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = settingDao.insertIfAbsent(SettingEntity(key, value, "إضافة جديدة"))
            withContext(Dispatchers.Main) { onResult(result != -1L) }
        }
    }

    fun addExpense(amount: Double, category: String, description: String, dateLong: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val newId = "EXP-" + UUID.randomUUID().toString().take(6)
            val monthKey = SimpleDateFormat("yyyyMM", Locale.US).format(Date(dateLong)).toLongOrNull() ?: 0L
            expenseDao.insert(ExpenseEntity(newId, dateLong, category, description, amount, monthKey))
        }
    }
    fun deleteExpense(id: String) { viewModelScope.launch(Dispatchers.IO) { expenseDao.deleteById(id) } }

    fun addSupplierRecord(name: String, notes: String, orderValue: Double, paidAmount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            supplierLedgerDao.insertRecord(SupplierLedgerEntity(
                opDate = System.currentTimeMillis(),
                supplierName = name,
                orderValue = orderValue,
                invoiceNo = "INV-" + System.currentTimeMillis().toString().takeLast(5),
                paidAmount = paidAmount,
                debt = orderValue - paidAmount,
                notes = notes
            ))
        }
    }

    // --- البيع الآمن (Checkout with Transaction) ---
    private val _isCheckingOut = MutableStateFlow(false)
    val isCheckingOut: StateFlow<Boolean> = _isCheckingOut

    fun checkout(customerName: String?, prescriptionPath: String?,
                 onError: (String) -> Unit = {}, onSuccess: (String) -> Unit) {
        val cart = _cartItems.value
        if (cart.isEmpty() || !_isCheckingOut.compareAndSet(false, true)) return

        // نحفظ اسم الملف فقط، لا المسار المطلق (حتى تعمل الاستعادة على أي جهاز)
        val storedPrescription = PrescriptionStorage.toStoredName(prescriptionPath)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val invoice = db.withTransaction {
                    val now = System.currentTimeMillis()
                    val invoiceNumber = "INV-" + SimpleDateFormat("yyyyMMdd-HHmmssSSS", Locale.US).format(Date(now))
                    val monthKey = SimpleDateFormat("yyyyMM", Locale.US).format(Date(now)).toLongOrNull() ?: 0L

                    for ((item, cartQty) in cart) {
                        var left = cartQty.toDouble()

                        val expiryToday = ExpiryDatePolicy.todayDateKey(now)
                        for (batch in batchDao.getSellableBatchesSync(item.itemId, expiryToday)) {
                            if (left <= 0.0) break
                            val take = minOf(batch.currentQty, left)

                            if (batchDao.deductIfAvailable(batch.batchId, take) != 1) {
                                throw Exception("تغيّرت كمية ${item.name}، أعد المحاولة")
                            }

                            saleDao.insertSale(SaleHistoryEntity(
                                saleDate = now, batchId = batch.batchId, legacyName = item.name,
                                quantity = take, unitPrice = batch.salePrice, totalAmount = take * batch.salePrice,
                                profit = take * (batch.salePrice - batch.purchasePrice),
                                offerProfit = take * (batch.purchasePrice - batch.unitCost),
                                month = monthKey, invoiceRef = invoiceNumber,
                                customerName = customerName, prescriptionImagePath = storedPrescription
                            ))

                            stockMovementDao.insertMovement(StockMovementEntity(
                                movementDate = now, movementType = "بيع", batchId = batch.batchId,
                                itemId = item.itemId, quantity = -take, reference = invoiceNumber, note = "بيع عبر التطبيق"
                            ))

                            left -= take
                        }

                        if (left > 0.0) {
                            throw Exception("الكمية غير كافية أو منتهية الصلاحية: ${item.name}")
                        }

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
                db.withTransaction {
                    val sale = saleDao.getSaleById(saleId)
                    if (sale != null && sale.batchId != null) {
                        val batch = batchDao.getBatchById(sale.batchId)
                        if (batch != null) {
                            batchDao.restoreBatchQuantity(sale.batchId, sale.quantity)
                            itemDao.deductItemStock(batch.itemId, -sale.quantity)
                            saleDao.deleteSaleMovement(sale.batchId, sale.invoiceRef)
                            saleDao.deleteSaleById(saleId)
                        }
                    }
                }
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun editSale(saleId: Long, newQty: Double, newPrice: Double, newDate: Long, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (newQty.isNaN() || newQty <= 0.0 || newPrice.isNaN() || newPrice < 0.0) {
                    throw Exception("الكمية والسعر غير صالحين")
                }
                db.withTransaction {
                    val sale = saleDao.getSaleById(saleId)
                    if (sale == null || sale.batchId == null) throw Exception("الفاتورة غير موجودة")
                    val batch = batchDao.getBatchById(sale.batchId) ?: throw Exception("الدفعة غير موجودة")

                    val deltaQty = newQty - sale.quantity
                    if (deltaQty > 0 && batch.currentQty < deltaQty) throw Exception("الكمية المطلوبة أكبر من المتوفر!")

                    batchDao.deductBatchQuantity(batch.batchId, deltaQty)
                    val historicalCostPerUnit = if (sale.quantity > 0) (sale.totalAmount - (sale.profit ?: 0.0)) / sale.quantity else 0.0
                    val newTotal = newQty * newPrice
                    val newProfit = newTotal - (newQty * historicalCostPerUnit)

                    saleDao.insertSale(sale.copy(
                        quantity = newQty, unitPrice = newPrice, totalAmount = newTotal, profit = newProfit,
                        saleDate = newDate, month = SimpleDateFormat("yyyyMM", Locale.US).format(Date(newDate)).toLongOrNull() ?: 0L
                    ))
                    stockMovementDao.updateSaleMovementQuantity(batch.batchId, sale.invoiceRef, -newQty)
                    itemDao.repairSingleBalance(batch.itemId)
                }
                withContext(Dispatchers.Main) { onResult(true, "تم التعديل بنجاح") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "حدث خطأ: ${e.message}") }
            }
        }
    }

    // --- المشتريات ---
    fun processPurchase(supplierName: String, invoiceNumber: String, amountPaid: Double, items: List<PurchaseItemInput>, onSuccess: () -> Unit, onError: (String) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                db.withTransaction {
                    var totalInvoice = 0.0
                    for (item in items) {
                        val existingItem = itemDao.getItemByName(item.name)
                        var finalItemId = existingItem?.itemId

                        if (existingItem == null) {
                            finalItemId = "ITEM-" + UUID.randomUUID().toString().take(8)
                            itemDao.insertItems(listOf(ItemEntity(
                                itemId = finalItemId, name = item.name, dosageForm = item.form,
                                company = item.company, currentStock = 0.0, reorderLevel = 2.0, barcode = item.barcode
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

                        val matchingBatch = batchDao.getMatchingBatch(finalItemId ?: "", item.expiryDate, item.purchasePrice, offerString)

                        val targetBatchId: String
                        if (matchingBatch != null) {
                            targetBatchId = matchingBatch.batchId
                            batchDao.updateBatch(matchingBatch.copy(
                                currentQty = matchingBatch.currentQty + totalUnits,
                                totalUnits = matchingBatch.totalUnits + totalUnits,
                                dataStatus = "محدثة"
                            ))
                        } else {
                            targetBatchId = "BATCH-" + UUID.randomUUID().toString().take(8)
                            batchDao.insertBatch(BatchEntity(
                                batchId = targetBatchId, itemId = finalItemId ?: "", drugName = item.name, dosageForm = item.form,
                                company = item.company, originalOffer = offerString, currentQty = totalUnits, expiryDate = item.expiryDate,
                                purchasePrice = item.purchasePrice, paidUnits = item.paidUnits, freeUnits = item.freeUnits,
                                totalUnits = totalUnits, unitCost = effectiveUnitCost, salePrice = item.salePrice, clearancePrice = null,
                                legacyBatchId = null, dataStatus = "جديدة"
                            ))
                        }
                        // حركة الشراء تُسجَّل في الحالتين (كانت تُسجَّل للدفعة الجديدة فقط)
                        stockMovementDao.insertMovement(StockMovementEntity(
                            movementDate = System.currentTimeMillis(), movementType = "شراء", batchId = targetBatchId,
                            itemId = finalItemId, quantity = totalUnits, reference = invoiceNumber, note = "شراء من المورد: $supplierName"
                        ))
                        if (finalItemId != null) itemDao.deductItemStock(finalItemId, -totalUnits)
                    }

                    supplierLedgerDao.insertRecord(SupplierLedgerEntity(
                        opDate = System.currentTimeMillis(),
                        supplierName = supplierName,
                        orderValue = totalInvoice,
                        invoiceNo = "INV-" + System.currentTimeMillis().toString().takeLast(5),
                        paidAmount = amountPaid,
                        debt = totalInvoice - amountPaid,
                        notes = "فاتورة رقم: $invoiceNumber"
                    ))
                }
                withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onError(e.message ?: "فشل حفظ الفاتورة") }
            }
        }
    }
}

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
    private val db: AppDatabase
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PharmacyViewModel::class.java)) {
            return PharmacyViewModel(
                itemDao, batchDao, saleDao, stockMovementDao,
                expenseDao, supplierLedgerDao, settingDao, dashboardDao,
                companyDao, reportDao, db
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}