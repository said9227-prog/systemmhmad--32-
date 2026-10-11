package com.example.synthetic

import com.example.data.model.*
import java.math.BigDecimal

/**
 * Encapsulates the complete synthetic dataset generated for automated accounting stress tests.
 */
data class SyntheticDataset(
    val customers: List<Client>,
    val products: List<Item>,
    val categories: List<ItemCategory>,
    val units: List<ItemUnit>,
    val suppliers: List<SupplierCompany>,
    val invoices: List<Invoice>,
    val invoiceItems: List<InvoiceItem>,
    val purchases: List<ItemPurchaseHistory>,
    val payments: List<Payment>,
    val returns: List<ProductReturn>,
    val returnItems: List<ProductReturnItem>,
    val exchangeRates: List<ExchangeRate>,
    val storeSettings: StoreSettings
)

/**
 * Holds independently calculated accounting expectations for a single customer.
 */
data class CustomerFinancialExpectation(
    val customerId: Int,
    val customerName: String,
    val currency: String,
    val openingBalance: BigDecimal,
    val totalInvoicedBase: BigDecimal,
    val totalPaidBase: BigDecimal,
    val totalReturnsCreditedBase: BigDecimal,
    val expectedNetBalance: BigDecimal,
    val invoiceCount: Int,
    val paymentCount: Int,
    val returnCount: Int
)

/**
 * Independent financial expectation for an individual invoice.
 */
data class InvoiceFinancialExpectation(
    val invoiceId: Int,
    val invoiceNumber: String,
    val rawSubtotal: BigDecimal,
    val discountAmount: BigDecimal,
    val subtotalAfterDiscount: BigDecimal,
    val taxAmount: BigDecimal,
    val finalTotal: BigDecimal,
    val currency: String,
    val exchangeRate: BigDecimal,
    val convertedTotal: BigDecimal,
    val totalPaid: BigDecimal,
    val expectedRemaining: BigDecimal
)
