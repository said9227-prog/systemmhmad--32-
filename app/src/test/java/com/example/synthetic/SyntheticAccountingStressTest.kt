package com.example.synthetic

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.AppRepository
import com.example.domain.currency.Currency
import com.example.domain.currency.CurrencyConversionService
import com.example.domain.currency.MissingExchangeRateException
import com.example.domain.currency.Money
import com.example.util.BackupEngine
import com.example.util.FinancialCalculator
import com.example.util.RestoreEngine
import com.example.util.RestoreMode
import com.example.util.RestoreOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.system.measureTimeMillis

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SyntheticAccountingStressTest {
    companion object {
        private val generator = SyntheticDataGenerator(masterSeed = 20261011L)
        val dataset: SyntheticDataset by lazy { generator.generateFullDataset() }
    }

    private lateinit var context: Context
    private lateinit var testDb: AppDatabase
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // PHASE 3 ISOLATION: Strict In-Memory Room Database, isolated from production "client_accounts_pro_db"
        testDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(testDb)
    }

    @After
    fun tearDown() {
        testDb.close()
    }

    // =========================================================================
    // PHASE 3 — ISOLATION AND SAFETY ASSERTIONS
    // =========================================================================
    @Test
    fun testPhase3_verifyProductionDatabaseIsolation() = runBlocking {
        // Assert that the test database is strictly isolated and active
        assertEquals(0, testDb.clientDao().getClientsCount())
        assertTrue("Test database must be open", testDb.openHelper.writableDatabase.isOpen)

        // Verify that the production database has zero synthetic records
        val prodDb = AppDatabase.getDatabase(context)
        val prodClientsCount = prodDb.clientDao().getClientsCount()
        assertEquals("Production database must never contain synthetic clients", 0, prodClientsCount)
        val prodInvoicesCount = prodDb.invoiceDao().getAllInvoices().size
        assertEquals("Production database must never contain synthetic invoices", 0, prodInvoicesCount)
    }

    // =========================================================================
    // PHASE 2 & 6 — BULK INSERTION AND PERFORMANCE STRESS TEST
    // =========================================================================
    @Test
    fun testPhase2And6_datasetBulkInsertionAndPerformance() = runBlocking {
        println("=== PHASE 2 & 6: BULK INSERTION AND PERFORMANCE STRESS TEST ===")

        // 1. Seed initial metadata
        testDb.storeSettingsDao().insertOrUpdateSettings(dataset.storeSettings)
        testDb.itemCategoryDao().insertAll(dataset.categories)
        testDb.itemUnitDao().insertAll(dataset.units)
        testDb.supplierCompanyDao().insertAll(dataset.suppliers)
        testDb.exchangeRateDao().insertAll(dataset.exchangeRates)

        // 2. Measure Customers Insertion (1,000 records)
        val customerInsertTime = measureTimeMillis {
            dataset.customers.chunked(250).forEach { chunk ->
                testDb.clientDao().insertAll(chunk)
            }
        }
        val insertedCustomersCount = testDb.clientDao().getClientsCount()
        assertEquals(SyntheticDataGenerator.TARGET_CUSTOMERS, insertedCustomersCount)
        println("VERIFIED PASS: Inserted $insertedCustomersCount customers in ${customerInsertTime}ms")

        // 3. Measure Products Insertion (500 records)
        val productInsertTime = measureTimeMillis {
            dataset.products.chunked(250).forEach { chunk ->
                testDb.itemDao().insertAll(chunk)
            }
        }
        val insertedProductsCount = testDb.itemDao().getItemsCount()
        assertEquals(SyntheticDataGenerator.TARGET_PRODUCTS, insertedProductsCount)
        println("VERIFIED PASS: Inserted $insertedProductsCount products in ${productInsertTime}ms")

        // 4. Measure Purchases Insertion (2,000 records)
        val purchaseInsertTime = measureTimeMillis {
            dataset.purchases.chunked(500).forEach { chunk ->
                testDb.itemPurchaseHistoryDao().insertAllPurchases(chunk)
            }
        }
        val insertedPurchasesCount = testDb.itemPurchaseHistoryDao().getAllPurchaseHistory().size
        assertEquals(SyntheticDataGenerator.TARGET_PURCHASES, insertedPurchasesCount)
        println("VERIFIED PASS: Inserted $insertedPurchasesCount purchase records in ${purchaseInsertTime}ms")

        // 5. Measure Invoices & Invoice Items Insertion (10,000 invoices + items)
        val invoiceInsertTime = measureTimeMillis {
            dataset.invoices.chunked(1000).forEach { chunk ->
                testDb.invoiceDao().insertAllInvoices(chunk)
            }
            dataset.invoiceItems.chunked(1000).forEach { chunk ->
                testDb.invoiceDao().insertAllInvoiceItems(chunk)
            }
        }
        val insertedInvoicesCount = testDb.invoiceDao().getAllInvoices().size
        val insertedInvoiceItemsCount = testDb.invoiceDao().getAllInvoiceItems().size
        assertEquals(SyntheticDataGenerator.TARGET_INVOICES, insertedInvoicesCount)
        assertTrue(insertedInvoiceItemsCount >= SyntheticDataGenerator.TARGET_INVOICES)
        println("VERIFIED PASS: Inserted $insertedInvoicesCount invoices & $insertedInvoiceItemsCount line items in ${invoiceInsertTime}ms")

        // 6. Measure Payments Insertion (5,000 records)
        val paymentInsertTime = measureTimeMillis {
            dataset.payments.chunked(1000).forEach { chunk ->
                testDb.paymentDao().insertAll(chunk)
            }
        }
        val insertedPaymentsCount = testDb.paymentDao().getAllPayments().size
        assertEquals(SyntheticDataGenerator.TARGET_PAYMENTS, insertedPaymentsCount)
        println("VERIFIED PASS: Inserted $insertedPaymentsCount payments in ${paymentInsertTime}ms")

        // 7. Measure Returns Insertion (500 records)
        val returnsInsertTime = measureTimeMillis {
            dataset.returns.chunked(250).forEach { chunk ->
                testDb.productReturnDao().insertAllReturns(chunk)
            }
            dataset.returnItems.chunked(250).forEach { chunk ->
                testDb.productReturnDao().insertAllReturnItems(chunk)
            }
        }
        val insertedReturnsCount = testDb.productReturnDao().getAllReturns().size
        val insertedReturnItemsCount = testDb.productReturnDao().getAllReturnItems().size
        assertEquals(SyntheticDataGenerator.TARGET_RETURNS, insertedReturnsCount)
        assertTrue(insertedReturnItemsCount >= SyntheticDataGenerator.TARGET_RETURNS)
        println("VERIFIED PASS: Inserted $insertedReturnsCount returns & $insertedReturnItemsCount return items in ${returnsInsertTime}ms")

        // 8. Performance Stress Query: Customer Search by Name
        val customerSearchTime = measureTimeMillis {
            val results = testDb.clientDao().getAllClients().filter { it.name.contains("صيدلية النور") }
            assertTrue(results.isNotEmpty())
        }
        println("VERIFIED PASS: Customer query filtered in ${customerSearchTime}ms")

        // 9. Performance Stress Query: Product Autocomplete & Barcode Lookup
        val productSearchTime = measureTimeMillis {
            val prod = testDb.itemDao().getItemByBarcode("6290000000001")
            assertNotNull(prod)
            val autocomplete = testDb.itemDao().autocompleteItems("%أموكسيسيلين%")
            assertTrue(autocomplete.isNotEmpty())
        }
        println("VERIFIED PASS: Product barcode & name autocomplete in ${productSearchTime}ms")
    }

    // =========================================================================
    // PHASE 4 — FINANCIAL CORRECTNESS ASSERTIONS
    // =========================================================================
    @Test
    fun testPhase4_financialCalculationsAndExchangeRates() = runBlocking {
        println("=== PHASE 4: FINANCIAL CORRECTNESS ASSERTIONS ===")

        // 1. Line Item Math: Quantity * UnitPrice
        for (item in dataset.invoiceItems.take(500)) {
            val expectedLineTotal = BigDecimal.valueOf(item.quantity.toLong())
                .multiply(BigDecimal.valueOf(item.unitPrice))
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()
            assertEquals("Line item total mismatch for item ${item.itemName}", expectedLineTotal, item.totalPrice, 0.01)
        }
        println("VERIFIED PASS: 500 invoice line items Quantity * UnitPrice matches exact arithmetic")

        // 2. Invoice Totals, Discounts, and Remaining Amounts
        val rateMap = dataset.exchangeRates.associate {
            (Currency.fromCodeOrName(it.fromCurrency) to Currency.fromCodeOrName(it.toCurrency)) to BigDecimal.valueOf(it.rate)
        }

        val itemsByInvoice = dataset.invoiceItems.groupBy { it.invoiceId }
        for (inv in dataset.invoices.take(1000)) {
            val lines = itemsByInvoice[inv.id] ?: emptyList()
            val expectation = FinancialTestOracle.calculateInvoiceExpectation(inv, lines)

            assertEquals("Invoice total mismatch for ${inv.invoiceNumber}", expectation.finalTotal.toDouble(), inv.totalAmount, 0.02)
            assertEquals("Invoice remaining mismatch for ${inv.invoiceNumber}", expectation.expectedRemaining.toDouble(), inv.remainingAmount, 0.02)
        }
        println("VERIFIED PASS: 1,000 invoices verified against independent financial oracle (totals, discounts, remaining balances)")

        // 3. Multi-currency and Exchange Rate Precision
        // Fixed benchmark test cases
        // 1 USD -> 1500 YER
        val usdConv = CurrencyConversionService.convert(Money(10.0, Currency.USD), Currency.YER, rateMap)
        assertEquals(15000.0, usdConv.convertedMoney.toDouble(), 0.01)
        assertEquals(1500.0, usdConv.exchangeRate.toDouble(), 0.0001)

        // 1 SAR -> 400 YER
        val sarConv = CurrencyConversionService.convert(Money(50.0, Currency.SAR), Currency.YER, rateMap)
        assertEquals(20000.0, sarConv.convertedMoney.toDouble(), 0.01)
        assertEquals(400.0, sarConv.exchangeRate.toDouble(), 0.0001)

        // Inverse: 400 YER -> 1 SAR
        val yerToSar = CurrencyConversionService.convert(Money(400.0, Currency.YER), Currency.SAR, rateMap)
        assertEquals(1.0, yerToSar.convertedMoney.toDouble(), 0.01)
        assertEquals(0.0025, yerToSar.exchangeRate.toDouble(), 0.0001)

        // Cross currency: 10 USD -> 37.5 SAR
        val usdToSar = CurrencyConversionService.convert(Money(10.0, Currency.USD), Currency.SAR, rateMap)
        assertEquals(37.5, usdToSar.convertedMoney.toDouble(), 0.01)
        assertEquals(3.75, usdToSar.exchangeRate.toDouble(), 0.0001)

        println("VERIFIED PASS: Direct, inverse, and cross-currency conversions verified with exact BigDecimal math")

        // 4. Strict Error Handling: Missing Exchange Rate must throw exception, NEVER silently fallback to 1.0
        val emptyRates = emptyMap<Pair<Currency, Currency>, BigDecimal>()
        try {
            CurrencyConversionService.convert(Money(100.0, Currency.USD), Currency.SAR, emptyRates)
            fail("Expected MissingExchangeRateException when converting without exchange rate")
        } catch (e: MissingExchangeRateException) {
            assertTrue("Exception message must indicate missing exchange rate", e.message!!.contains("لم يتم العثور على سعر صرف"))
        }
        println("VERIFIED PASS: Missing exchange rate strictly rejected (no silent 1.0 fallback)")

        // 5. Database Rollback upon Failure
        testDb.clientDao().insertClient(Client(id = 9999, name = "عميل فحص التراجع", currency = "الريال اليمني", balance = 0.0))
        val testItem = Item(id = 8888, name = "صنف التراجع", quantity = 2, sellingPrice = 100.0, purchaseCurrency = "الريال اليمني", sellingCurrency = "الريال اليمني")
        testDb.itemDao().insertItem(testItem)

        try {
            // Attempt inserting an invoice that requests 10 units when only 2 exist
            repository.insertInvoice(
                invoice = Invoice(id = 9999, invoiceNumber = "INV-FAIL", clientId = 9999, clientName = "عميل فحص التراجع", totalAmount = 1000.0),
                items = listOf(InvoiceItem(invoiceId = 9999, itemId = 8888, itemName = "صنف التراجع", quantity = 10, unitPrice = 100.0, totalPrice = 1000.0))
            )
            fail("Expected IllegalArgumentException due to insufficient stock")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("غير متوفرة في المخزون"))
        }

        // Verify rollback: invoice must NOT exist in DB and stock must remain 2
        val rolledBackInvoice = testDb.invoiceDao().getInvoiceById(9999)
        assertNull("Invoice must not be inserted when transaction fails", rolledBackInvoice)
        val stockAfterRollback = testDb.itemDao().getItemById(8888)!!.quantity
        assertEquals("Stock must be unchanged after transaction rollback", 2, stockAfterRollback)
        println("VERIFIED PASS: Transaction atomic rollback verified on validation failure")
    }

    // =========================================================================
    // PHASE 5 — VERIFY CUSTOMER BALANCES AND LEDGER ACROSS ALL 1,000 CUSTOMERS
    // =========================================================================
    @Test
    fun testPhase5_verifyCustomerBalancesAcrossAll1000Customers() = runBlocking {
        println("=== PHASE 5: VERIFY CUSTOMER BALANCES ACROSS ALL 1,000 CUSTOMERS ===")

        // Seed data into test database
        testDb.exchangeRateDao().insertAll(dataset.exchangeRates)
        dataset.customers.chunked(250).forEach { testDb.clientDao().insertAll(it) }
        dataset.invoices.chunked(1000).forEach { testDb.invoiceDao().insertAllInvoices(it) }
        dataset.invoiceItems.chunked(1000).forEach { testDb.invoiceDao().insertAllInvoiceItems(it) }
        dataset.payments.chunked(1000).forEach { testDb.paymentDao().insertAll(it) }
        dataset.returns.chunked(250).forEach { testDb.productReturnDao().insertAllReturns(it) }
        dataset.returnItems.chunked(250).forEach { testDb.productReturnDao().insertAllReturnItems(it) }

        // Recalculate customer balances through the repository for all 1,000 customers
        var updatedCount = 0
        val recalculationTime = measureTimeMillis {
            for (client in dataset.customers) {
                repository.recalculateClientBalance(client.id)
                updatedCount++
            }
        }
        println("VERIFIED PASS: Recalculated balances for all $updatedCount customers in ${recalculationTime}ms")

        // Now independently verify every single customer
        val invoicesByCustomer = dataset.invoices.groupBy { it.clientId }
        val paymentsByCustomer = dataset.payments.groupBy { it.clientId }
        val returnsByCustomer = dataset.returns.groupBy { it.clientId ?: 0 }

        var zeroActivityCount = 0
        var activeCustomerVerifiedCount = 0

        for (client in dataset.customers) {
            val clientInvoices = invoicesByCustomer[client.id] ?: emptyList()
            val clientPayments = paymentsByCustomer[client.id] ?: emptyList()
            val clientReturns = returnsByCustomer[client.id] ?: emptyList()

            val oracleExpectation = FinancialTestOracle.calculateCustomerBalance(
                client = client,
                invoices = clientInvoices,
                payments = clientPayments,
                returns = clientReturns
            )

            val dbClient = testDb.clientDao().getClientById(client.id)
            assertNotNull("Customer ${client.id} must exist in database", dbClient)

            val expectedBal = oracleExpectation.expectedNetBalance.toDouble()
            val actualBal = dbClient!!.balance

            val diff = Math.abs(expectedBal - actualBal)
            if (diff > 0.05) {
                fail(
                    "Customer balance mismatch! ID: ${client.id}, Name: ${client.name}, Currency: ${client.currency}, " +
                            "Expected: $expectedBal, Actual: $actualBal, Invoices: ${clientInvoices.size}, Payments: ${clientPayments.size}"
                )
            }

            if (clientInvoices.isEmpty() && clientPayments.isEmpty() && clientReturns.isEmpty()) {
                zeroActivityCount++
                val initialExpected = if (client.balanceType == "مدين" || client.balanceType == "عليه لنا") client.initialBalance else -client.initialBalance
                assertEquals("Zero-activity customer must preserve opening balance", initialExpected, actualBal, 0.02)
            } else {
                activeCustomerVerifiedCount++
            }
        }

        println("VERIFIED PASS: 1,000 customers verified (Active: $activeCustomerVerifiedCount, Zero-activity: $zeroActivityCount)")
        assertEquals(SyntheticDataGenerator.TARGET_CUSTOMERS, zeroActivityCount + activeCustomerVerifiedCount)
    }

    // =========================================================================
    // PHASE 7 — BACKUP AND RESTORE VALIDATION
    // =========================================================================
    @Test
    fun testPhase7_backupAndRestoreValidation() = runBlocking {
        println("=== PHASE 7: BACKUP AND RESTORE VALIDATION ===")

        // 1. Seed complete dataset into test database
        testDb.storeSettingsDao().insertOrUpdateSettings(dataset.storeSettings)
        testDb.itemCategoryDao().insertAll(dataset.categories)
        testDb.itemUnitDao().insertAll(dataset.units)
        testDb.supplierCompanyDao().insertAll(dataset.suppliers)
        testDb.exchangeRateDao().insertAll(dataset.exchangeRates)
        dataset.customers.chunked(250).forEach { testDb.clientDao().insertAll(it) }
        dataset.products.chunked(250).forEach { testDb.itemDao().insertAll(it) }
        dataset.invoices.chunked(1000).forEach { testDb.invoiceDao().insertAllInvoices(it) }
        dataset.invoiceItems.chunked(1000).forEach { testDb.invoiceDao().insertAllInvoiceItems(it) }
        dataset.payments.chunked(1000).forEach { testDb.paymentDao().insertAll(it) }
        dataset.returns.chunked(250).forEach { testDb.productReturnDao().insertAllReturns(it) }
        dataset.returnItems.chunked(250).forEach { testDb.productReturnDao().insertAllReturnItems(it) }

        val beforeClientsCount = testDb.clientDao().getClientsCount()
        val beforeItemsCount = testDb.itemDao().getItemsCount()
        val beforeInvoicesCount = testDb.invoiceDao().getAllInvoices().size
        val beforePaymentsCount = testDb.paymentDao().getAllPayments().size
        val beforeReturnsCount = testDb.productReturnDao().getAllReturns().size

        // 2. Create Backup
        val backupResult = BackupEngine.createBackup(
            context = context,
            db = testDb,
            selectedCategories = BackupEngine.ALL_CATEGORIES.toSet(),
            destination = "الهاتف",
            retentionCount = 5
        )

        assertTrue("Backup must succeed", backupResult.isSuccess)
        val backupFile = backupResult.getOrThrow()
        assertTrue("Backup archive must exist", backupFile.exists())
        assertTrue("Backup file size must be > 0 bytes", backupFile.length() > 0)
        println("VERIFIED PASS: Backup created successfully (${backupFile.name}, size: ${backupFile.length()} bytes)")

        // 3. Create a fresh isolated second test database for restore
        val restoreDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            // Restore into fresh database
            val restoreResult = RestoreEngine.restoreFromFile(
                context = context,
                db = restoreDb,
                backupFile = backupFile,
                options = RestoreOptions(mode = RestoreMode.MERGE)
            )

            assertTrue("Restore must succeed", restoreResult.success)
            println("VERIFIED PASS: Restored backup archive into fresh database (${restoreResult.totalRestored} records)")

            val afterClientsCount = restoreDb.clientDao().getClientsCount()
            val afterItemsCount = restoreDb.itemDao().getItemsCount()
            val afterInvoicesCount = restoreDb.invoiceDao().getAllInvoices().size
            val afterPaymentsCount = restoreDb.paymentDao().getAllPayments().size
            val afterReturnsCount = restoreDb.productReturnDao().getAllReturns().size

            assertEquals("Restored clients count must match original", beforeClientsCount, afterClientsCount)
            assertEquals("Restored items count must match original", beforeItemsCount, afterItemsCount)
            assertEquals("Restored invoices count must match original", beforeInvoicesCount, afterInvoicesCount)
            assertEquals("Restored payments count must match original", beforePaymentsCount, afterPaymentsCount)
            assertEquals("Restored returns count must match original", beforeReturnsCount, afterReturnsCount)

            // Idempotency: Restoring same backup twice must not create duplicates
            val doubleRestoreResult = RestoreEngine.restoreFromFile(
                context = context,
                db = restoreDb,
                backupFile = backupFile,
                options = RestoreOptions(mode = RestoreMode.MERGE)
            )
            assertTrue("Second restore must succeed", doubleRestoreResult.success)
            val doubleClientsCount = restoreDb.clientDao().getClientsCount()
            assertEquals("Duplicate restore must not duplicate client records", beforeClientsCount, doubleClientsCount)
            println("VERIFIED PASS: Double-restore idempotency verified (no duplicate records created)")

            // Test corrupted backup handling
            val corruptFile = File(context.cacheDir, "corrupted_backup.zip")
            corruptFile.writeText("THIS IS NOT A VALID ZIP ARCHIVE")
            val corruptRestoreResult = RestoreEngine.restoreFromFile(
                context = context,
                db = restoreDb,
                backupFile = corruptFile
            )
            assertFalse("Corrupted backup file must be rejected", corruptRestoreResult.success)
            println("VERIFIED PASS: Corrupted backup rejected safely with error message: ${corruptRestoreResult.message}")

        } finally {
            restoreDb.close()
            if (backupFile.exists()) backupFile.delete()
        }
    }
}
