package com.example.myapplication.data.v8.entity

/** وحدة البيع في بند الفاتورة. */
enum class UnitType { BOX, STRIP }

/** مصدر الدفعة. */
enum class BatchSource { PURCHASE, OPENING, STOCK_IN, CUSTOMER_RETURN }

/**
 * الأنواع المعرفة في المجال. في الإصدار الأول تسمح الحماية بـ OPENING وPURCHASE وSALE وSALE_CANCEL فقط؛
 * بقية الأنواع، ومنها SUPPLIER_RETURN_DIFFERENCE، تبقى محظورة حتى تعريف قواعدها.
 */
enum class MovementType {
    OPENING, PURCHASE, STOCK_IN, SALE, SALE_CANCEL, CUSTOMER_RETURN,
    SUPPLIER_RETURN, STOCK_OUT, COST_ADJUSTMENT, SUPPLIER_RETURN_DIFFERENCE
}

enum class PurchaseStatus { DRAFT, CONFIRMED, CANCELLED }
enum class SaleStatus { PENDING, CONFIRMED, CANCELLED }

/** حالة سجل لا يُحذف (مصروف، حركة مورد، حركة نقدية). */
enum class RecordStatus { ACTIVE, CANCELLED }

enum class SupplierEntryType { OPENING, PAYMENT, RECEIPT }
enum class CashEntryType { OPENING, OWNER_DEPOSIT, OWNER_WITHDRAWAL, CASH_IN, CASH_OUT }
