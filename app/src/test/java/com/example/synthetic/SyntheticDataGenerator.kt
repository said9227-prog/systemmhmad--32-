package com.example.synthetic

import com.example.data.model.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Random
import java.util.UUID

/**
 * Deterministic synthetic data generator for stress testing and financial validation.
 * Uses fixed random seeds so that identical datasets can be regenerated deterministically.
 */
class SyntheticDataGenerator(
    private val masterSeed: Long = 20261011L
) {

    private val random = Random(masterSeed)

    companion object {
        const val TARGET_CUSTOMERS = 1000
        const val TARGET_PRODUCTS = 500
        const val TARGET_INVOICES = 10000
        const val TARGET_PURCHASES = 2000
        const val TARGET_PAYMENTS = 5000
        const val TARGET_RETURNS = 500

        // Canonical currencies
        val CURRENCIES = listOf("الريال اليمني", "الريال السعودي", "الدولار الأمريكي")

        val PHARMA_PREFIXES = listOf(
            "أقراص", "كبسولات", "شراب", "حقن", "مرهم", "قطرة", "محلول وريدي", "بخاخ", "شاش طبي", "شريط كاشف", "سرنجات"
        )
        val PHARMA_NAMES = listOf(
            "أموكسيسيلين", "باراسيتامول", "أوجمنتين", "إيبوبروفين", "أوميبرازول", "أزيثروميسين",
            "سيفيكسيم", "ميتفورمين", "أملوديبين", "فيتامين سي", "فيتامين د3", "زنك بلس",
            "بانتوبرازول", "ديكلوفيناك الصوديوم", "سيفترياكسون", "سبروفلوكساسين", "مترونيدازول",
            "أنسولين لانتوس", "سالبوتامول", "لوراتادين", "كانديسارتان", "أتورفاستاتين"
        )
        val MEDICAL_SUPPLIES = listOf(
            "سرنجات 5 مل معقمة", "شاش طبي معقم 10×10", "قطن طبي ماص 500 جم", "كحول طبي 70% 1 لتر",
            "كانيولا وريدية مقاس 20", "كانيولا وريدية مقاس 22", "جهاز إعطاء محاليل وريدية",
            "قفازات فحص طبية لاتكس", "كمامات طبية 3 طبقات", "مقياس ضغط إلكتروني",
            "مقياس حرارة رقمي", "شرائط قياس السكر بالدم", "شريط لاصق طبي هيبوالرجينيك"
        )

        val CUSTOMER_TYPES = listOf("صيدلية", "مستشفى", "مركز صحي", "عيادة خاصة", "فرد", "مستوصف")
        val CUSTOMER_FIRST_NAMES = listOf("النور", "الأمل", "الشفاء", "الحياة", "السلام", "الرحمة", "البركة", "ابن سينا", "الرازي", "طيبة", "اليمن السعيد", "عدن", "صنعاء", "تعز", "المجد")
        val YEMEN_CITIES = listOf("صنعاء", "عدن", "تعز", "إب", "الحديدة", "المكلا", "ذمار", "سيئون")
    }

    fun generateFullDataset(): SyntheticDataset {
        val now = System.currentTimeMillis()
        val oneYearAgo = now - 365L * 24 * 60 * 60 * 1000

        // 1. Exchange Rates
        val exchangeRates = listOf(
            ExchangeRate(id = 1, fromCurrency = "USD", toCurrency = "YER", rate = 1500.0, lastUpdated = now),
            ExchangeRate(id = 2, fromCurrency = "SAR", toCurrency = "YER", rate = 400.0, lastUpdated = now),
            ExchangeRate(id = 3, fromCurrency = "USD", toCurrency = "SAR", rate = 3.75, lastUpdated = now)
        )

        // 2. Store Settings
        val storeSettings = StoreSettings(
            id = 1,
            storeName = "الحكيمي للأدوية والمستلزمات الطبية",
            storePhone = "770123456",
            storeAddress = "صنعاء - شارع التحرير",
            currency = "الريال اليمني",
            showVatAndSubtotal = false
        )

        // 3. Categories & Units
        val categories = listOf(
            ItemCategory(id = 1, name = "أدوية", isPermanent = true),
            ItemCategory(id = 2, name = "مستلزمات طبية", isPermanent = true),
            ItemCategory(id = 3, name = "كواشف مخبرية", isPermanent = true),
            ItemCategory(id = 4, name = "أدوات جراحية", isPermanent = true),
            ItemCategory(id = 5, name = "محاليل وريدية", isPermanent = true)
        )
        val units = listOf(
            ItemUnit(id = 1, name = "قطعة"),
            ItemUnit(id = 2, name = "علبة"),
            ItemUnit(id = 3, name = "شريط"),
            ItemUnit(id = 4, name = "كرتون"),
            ItemUnit(id = 5, name = "أمبولة"),
            ItemUnit(id = 6, name = "زجاجة")
        )

        // 4. Supplier Companies
        val suppliers = listOf(
            SupplierCompany(id = 1, name = "شركة سبأ فارما الدولية", phone = "771000001", address = "صنعاء"),
            SupplierCompany(id = 2, name = "شركة الحياة للمستلزمات الطبية", phone = "771000002", address = "عدن"),
            SupplierCompany(id = 3, name = "مؤسسة ابن حيان الدوائية", phone = "771000003", address = "تعز"),
            SupplierCompany(id = 4, name = "شركة النجم الساطع للتوكيلات الطبية", phone = "771000004", address = "الحديدة")
        )

        // 5. 1,000 Customers
        val customers = mutableListOf<Client>()
        for (i in 1..TARGET_CUSTOMERS) {
            val type = CUSTOMER_TYPES[random.nextInt(CUSTOMER_TYPES.size)]
            val nameSuffix = CUSTOMER_FIRST_NAMES[random.nextInt(CUSTOMER_FIRST_NAMES.size)]
            val city = YEMEN_CITIES[random.nextInt(YEMEN_CITIES.size)]
            val customerName = "$type $nameSuffix #$i"

            // Currency distribution: 65% YER, 20% SAR, 15% USD
            val roll = random.nextDouble()
            val currency = when {
                roll < 0.65 -> "الريال اليمني"
                roll < 0.85 -> "الريال السعودي"
                else -> "الدولار الأمريكي"
            }

            // Opening balance: 40% zero, 40% debit (owe money), 20% credit (in advance)
            val balanceRoll = random.nextDouble()
            val (initialBalance, balanceType) = when {
                balanceRoll < 0.40 -> Pair(0.0, "عليه لنا")
                balanceRoll < 0.80 -> {
                    val amount = when (currency) {
                        "الريال اليمني" -> (random.nextInt(500) + 1) * 1000.0 // 1,000 to 500,000 YER
                        "الريال السعودي" -> (random.nextInt(100) + 1) * 50.0   // 50 to 5,000 SAR
                        else -> (random.nextInt(50) + 1) * 20.0               // 20 to 1,000 USD
                    }
                    Pair(amount, "عليه لنا")
                }
                else -> {
                    val amount = when (currency) {
                        "الريال اليمني" -> (random.nextInt(200) + 1) * 1000.0
                        "الريال السعودي" -> (random.nextInt(40) + 1) * 50.0
                        else -> (random.nextInt(25) + 1) * 20.0
                    }
                    Pair(amount, "له عندنا")
                }
            }

            val phone = "77%07d".format(i)
            val client = Client(
                id = i,
                customerId = "CUST-%04d".format(i),
                name = customerName,
                phone = phone,
                address = "$city - الحي التجاري",
                city = city,
                clientType = if (type == "فرد") "فرد" else "شركة",
                classification = if (i % 10 == 0) "VIP" else "عادي",
                currency = currency,
                initialBalance = initialBalance,
                balanceType = balanceType,
                initialBalanceCurrency = currency,
                balance = 0.0, // Initial state before transaction calculation
                creditLimit = if (i % 5 == 0) 500000.0 else 0.0,
                createdAt = oneYearAgo + (i * 10000L),
                uuid = UUID.randomUUID().toString()
            )
            customers.add(client)
        }

        // 6. 500 Products
        val products = mutableListOf<Item>()
        for (i in 1..TARGET_PRODUCTS) {
            val isMedical = i > 350
            val name = if (isMedical) {
                "${MEDICAL_SUPPLIES[(i - 351) % MEDICAL_SUPPLIES.size]} (موديل $i)"
            } else {
                val prefix = PHARMA_PREFIXES[random.nextInt(PHARMA_PREFIXES.size)]
                val drug = PHARMA_NAMES[random.nextInt(PHARMA_NAMES.size)]
                val dosage = listOf("250 ملجم", "500 ملجم", "1 جم", "100 مل", "5 مل")[random.nextInt(5)]
                "$prefix $drug $dosage #$i"
            }

            val category = if (isMedical) "مستلزمات طبية" else "أدوية"
            val unit = if (isMedical) "قطعة" else listOf("علبة", "شريط", "زجاجة")[random.nextInt(3)]
            val supplier = suppliers[random.nextInt(suppliers.size)]

            // Product currency: 80% YER, 10% SAR, 10% USD
            val roll = random.nextDouble()
            val prodCurrency = when {
                roll < 0.80 -> "الريال اليمني"
                roll < 0.90 -> "الريال السعودي"
                else -> "الدولار الأمريكي"
            }

            val purchasePrice = when (prodCurrency) {
                "الريال اليمني" -> (random.nextInt(90) + 10) * 100.0 // 1,000 to 10,000 YER
                "الريال السعودي" -> (random.nextInt(50) + 5) * 2.0    // 10 to 100 SAR
                else -> (random.nextInt(40) + 2) * 1.5               // 3 to 60 USD
            }
            val markup = 1.25 + (random.nextDouble() * 0.30) // 25% to 55% profit margin
            val sellingPrice = BigDecimal.valueOf(purchasePrice * markup).setScale(2, RoundingMode.HALF_UP).toDouble()

            // Large inventory so stress transactions never fail stock checks
            val initialStock = 50000 + random.nextInt(50000)

            val item = Item(
                id = i,
                name = name,
                barcode = "629%010d".format(i),
                category = category,
                unit = unit,
                supplierType = "شركة",
                supplierCompanyId = supplier.id,
                supplierCompanyName = supplier.name,
                purchaseDate = oneYearAgo + (i * 20000L),
                purchasePrice = purchasePrice,
                sellingPrice = sellingPrice,
                quantity = initialStock,
                minQuantityAlert = 10,
                purchaseCurrency = prodCurrency,
                sellingCurrency = prodCurrency,
                uuid = UUID.randomUUID().toString()
            )
            products.add(item)
        }

        // 7. 2,000 Purchase History records
        val purchases = mutableListOf<ItemPurchaseHistory>()
        for (i in 1..TARGET_PURCHASES) {
            val item = products[random.nextInt(products.size)]
            val supplier = suppliers[random.nextInt(suppliers.size)]
            val purchaseDate = oneYearAgo + (i * 15000L)
            val qty = 50 + random.nextInt(200)

            purchases.add(
                ItemPurchaseHistory(
                    id = i,
                    itemId = item.id,
                    supplierCompanyId = supplier.id,
                    supplierCompanyName = supplier.name,
                    supplierType = "شركة",
                    purchaseDate = purchaseDate,
                    purchasePrice = item.purchasePrice,
                    quantity = qty,
                    purchaseCurrency = item.purchaseCurrency,
                    exchangeRate = 1.0
                )
            )
        }

        // 8. 10,000 Sales Invoices with InvoiceItems
        // 150 customers will have 0 transactions (Phase 2 requirement: "Customers with no transactions")
        val activeCustomerPool = customers.drop(150)
        val invoices = mutableListOf<Invoice>()
        val invoiceItems = mutableListOf<InvoiceItem>()
        var invoiceItemGlobalId = 1

        for (i in 1..TARGET_INVOICES) {
            // Skew distribution: some customers have 1-5, others have 20-40 transactions
            val customer = if (i % 4 == 0) {
                // High activity cluster (customers 151..200)
                activeCustomerPool[random.nextInt(50)]
            } else {
                activeCustomerPool[random.nextInt(activeCustomerPool.size)]
            }

            val invoiceDate = oneYearAgo + (random.nextDouble() * 365L * 24 * 3600 * 1000).toLong()
            val invoiceNumber = "INV-%06d".format(i)

            // Invoice currency usually matches customer currency (90%), occasionally cross-currency (10%)
            val invoiceCurrency = if (random.nextDouble() < 0.90) {
                customer.currency
            } else {
                CURRENCIES[random.nextInt(CURRENCIES.size)]
            }

            val itemsCount = 1 + random.nextInt(4) // 1 to 4 line items
            var subtotalInInvoiceCurr = BigDecimal.ZERO

            val currentInvoiceItems = mutableListOf<InvoiceItem>()
            for (line in 1..itemsCount) {
                val prod = products[random.nextInt(products.size)]
                val qty = 1 + random.nextInt(10)

                // Line unit price in product currency
                val unitPrice = prod.sellingPrice
                val lineTotalRaw = BigDecimal.valueOf(qty.toLong()).multiply(BigDecimal.valueOf(unitPrice))

                // Convert line total to invoice currency if different
                val convertedLineTotal = FinancialTestOracle.convertAmount(
                    amount = lineTotalRaw,
                    fromCurrency = prod.sellingCurrency,
                    toCurrency = invoiceCurrency
                )

                subtotalInInvoiceCurr = subtotalInInvoiceCurr.add(convertedLineTotal)

                val invItem = InvoiceItem(
                    id = invoiceItemGlobalId++,
                    invoiceId = i,
                    itemId = prod.id,
                    itemName = prod.name,
                    quantity = qty,
                    unitPrice = unitPrice,
                    totalPrice = lineTotalRaw.setScale(2, RoundingMode.HALF_UP).toDouble(),
                    currency = prod.sellingCurrency,
                    exchangeRate = 1.0,
                    convertedTotalPrice = convertedLineTotal.toDouble(),
                    originalProductPrice = prod.sellingPrice,
                    originalProductCurrency = prod.sellingCurrency,
                    uuid = UUID.randomUUID().toString()
                )
                currentInvoiceItems.add(invItem)
            }
            invoiceItems.addAll(currentInvoiceItems)

            // Discount: 80% no discount, 20% small discount
            val discountAmount = if (random.nextDouble() < 0.20) {
                subtotalInInvoiceCurr.multiply(BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
            val finalTotal = subtotalInInvoiceCurr.subtract(discountAmount).max(BigDecimal.ZERO)

            // Payment status:
            // 40% fully paid, 30% partially paid, 30% unpaid
            val statusRoll = random.nextDouble()
            val (paidAmount, remainingAmount) = when {
                statusRoll < 0.40 -> Pair(finalTotal.toDouble(), 0.0)
                statusRoll < 0.70 -> {
                    val halfPaid = finalTotal.multiply(BigDecimal("0.50")).setScale(2, RoundingMode.HALF_UP)
                    val rem = finalTotal.subtract(halfPaid).setScale(2, RoundingMode.HALF_UP)
                    Pair(halfPaid.toDouble(), rem.toDouble())
                }
                else -> Pair(0.0, finalTotal.toDouble())
            }

            // Converted total to client account base currency
            val convertedTotalInClientBase = FinancialTestOracle.convertAmount(
                amount = finalTotal,
                fromCurrency = invoiceCurrency,
                toCurrency = customer.currency
            ).toDouble()

            val rateToBase = FinancialTestOracle.resolveRate(invoiceCurrency, customer.currency).toDouble()

            val invoice = Invoice(
                id = i,
                invoiceNumber = invoiceNumber,
                date = invoiceDate,
                clientId = customer.id,
                clientName = customer.name,
                isQuickInvoice = false,
                discount = discountAmount.toDouble(),
                taxRate = 0.0,
                isDraft = false,
                totalAmount = finalTotal.toDouble(),
                paidAmount = paidAmount,
                remainingAmount = remainingAmount,
                currency = invoiceCurrency,
                exchangeRate = rateToBase,
                baseCurrency = customer.currency,
                convertedTotalAmount = convertedTotalInClientBase,
                uuid = UUID.randomUUID().toString()
            )
            invoices.add(invoice)
        }

        // 9. 5,000 Payments
        val payments = mutableListOf<Payment>()
        val paidOrPartialInvoices = invoices.filter { it.paidAmount > 0.01 }

        for (i in 1..TARGET_PAYMENTS) {
            // 70% attached to an invoice, 30% general on-account payment
            if (i <= paidOrPartialInvoices.size) {
                val inv = paidOrPartialInvoices[i - 1]
                val paymentAmount = inv.paidAmount
                val client = customers.first { it.id == inv.clientId }

                val convertedAmount = FinancialTestOracle.convertAmount(
                    amount = BigDecimal.valueOf(paymentAmount),
                    fromCurrency = inv.currency,
                    toCurrency = client.currency
                ).toDouble()

                payments.add(
                    Payment(
                        id = i,
                        clientId = inv.clientId,
                        invoiceId = inv.id,
                        amount = paymentAmount,
                        date = inv.date + 1000L,
                        paymentMethod = listOf("نقدي", "تحويل بنكي", "شيك", "إيداع")[random.nextInt(4)],
                        notes = "سداد الفاتورة ${inv.invoiceNumber}",
                        currency = inv.currency,
                        exchangeRate = inv.exchangeRate,
                        baseCurrency = client.currency,
                        convertedAmount = convertedAmount,
                        voucherNumber = "PAY-%05d".format(i),
                        uuid = UUID.randomUUID().toString()
                    )
                )
            } else {
                // General payment on account
                val customer = activeCustomerPool[random.nextInt(activeCustomerPool.size)]
                val payCurr = customer.currency
                val amount = when (payCurr) {
                    "الريال اليمني" -> (random.nextInt(50) + 5) * 1000.0
                    "الريال السعودي" -> (random.nextInt(30) + 2) * 50.0
                    else -> (random.nextInt(20) + 1) * 20.0
                }

                payments.add(
                    Payment(
                        id = i,
                        clientId = customer.id,
                        invoiceId = null,
                        amount = amount,
                        date = oneYearAgo + (random.nextDouble() * 365L * 24 * 3600 * 1000).toLong(),
                        paymentMethod = "نقدي",
                        notes = "سداد دفعة على الحساب",
                        currency = payCurr,
                        exchangeRate = 1.0,
                        baseCurrency = customer.currency,
                        convertedAmount = amount,
                        voucherNumber = "PAY-%05d".format(i),
                        uuid = UUID.randomUUID().toString()
                    )
                )
            }
        }

        // 10. 500 Product Returns (350 Customer Returns, 150 Purchase Returns)
        val returns = mutableListOf<ProductReturn>()
        val returnItems = mutableListOf<ProductReturnItem>()
        var returnItemGlobalId = 1

        for (i in 1..TARGET_RETURNS) {
            val isCustomerReturn = i <= 350
            val date = oneYearAgo + (random.nextDouble() * 365L * 24 * 3600 * 1000).toLong()

            if (isCustomerReturn) {
                val invoice = invoices[random.nextInt(invoices.size)]
                val customer = customers.first { it.id == invoice.clientId }
                val prod = products[random.nextInt(products.size)]
                val qty = 1 + random.nextInt(3)
                val unitPrice = prod.sellingPrice
                val totalRaw = BigDecimal.valueOf(qty.toLong()).multiply(BigDecimal.valueOf(unitPrice))

                val convertedTotal = FinancialTestOracle.convertAmount(
                    amount = totalRaw,
                    fromCurrency = prod.sellingCurrency,
                    toCurrency = customer.currency
                ).toDouble()

                val productReturn = ProductReturn(
                    id = i,
                    returnNumber = "RET-%04d".format(i),
                    type = "CUSTOMER",
                    date = date,
                    invoiceId = invoice.id,
                    invoiceNumber = invoice.invoiceNumber,
                    clientId = customer.id,
                    clientName = customer.name,
                    totalAmount = totalRaw.setScale(2, RoundingMode.HALF_UP).toDouble(),
                    settlementType = if (i % 4 == 0) "استرداد نقدي" else "خصم من الرصيد",
                    reason = listOf("طلب العميل", "خطأ في الطلب", "منتهي الصلاحية", "تالف")[random.nextInt(4)],
                    currency = prod.sellingCurrency,
                    exchangeRate = 1.0,
                    baseCurrency = customer.currency,
                    convertedTotalAmount = convertedTotal,
                    uuid = UUID.randomUUID().toString()
                )
                returns.add(productReturn)

                returnItems.add(
                    ProductReturnItem(
                        id = returnItemGlobalId++,
                        returnId = i,
                        itemId = prod.id,
                        itemName = prod.name,
                        unit = prod.unit,
                        quantity = qty,
                        unitPrice = unitPrice,
                        totalPrice = totalRaw.setScale(2, RoundingMode.HALF_UP).toDouble(),
                        itemCondition = if (i % 5 == 0) "تالف" else "صالح للبيع",
                        currency = prod.sellingCurrency,
                        exchangeRate = 1.0,
                        convertedTotalPrice = convertedTotal,
                        originalProductPrice = prod.sellingPrice,
                        originalProductCurrency = prod.sellingCurrency,
                        uuid = UUID.randomUUID().toString()
                    )
                )
            } else {
                // Purchase return to supplier
                val supplier = suppliers[random.nextInt(suppliers.size)]
                val prod = products[random.nextInt(products.size)]
                val qty = 2 + random.nextInt(5)
                val unitPrice = prod.purchasePrice
                val totalRaw = BigDecimal.valueOf(qty.toLong()).multiply(BigDecimal.valueOf(unitPrice))

                val productReturn = ProductReturn(
                    id = i,
                    returnNumber = "PRET-%04d".format(i),
                    type = "PURCHASE",
                    date = date,
                    supplierCompanyId = supplier.id,
                    supplierType = "شركة",
                    supplierName = supplier.name,
                    totalAmount = totalRaw.setScale(2, RoundingMode.HALF_UP).toDouble(),
                    settlementType = "استرداد نقدي",
                    reason = "غير مطابق للمواصفات",
                    currency = prod.purchaseCurrency,
                    exchangeRate = 1.0,
                    baseCurrency = prod.purchaseCurrency,
                    convertedTotalAmount = totalRaw.setScale(2, RoundingMode.HALF_UP).toDouble(),
                    uuid = UUID.randomUUID().toString()
                )
                returns.add(productReturn)

                returnItems.add(
                    ProductReturnItem(
                        id = returnItemGlobalId++,
                        returnId = i,
                        itemId = prod.id,
                        itemName = prod.name,
                        unit = prod.unit,
                        quantity = qty,
                        unitPrice = unitPrice,
                        totalPrice = totalRaw.setScale(2, RoundingMode.HALF_UP).toDouble(),
                        itemCondition = "تالف",
                        currency = prod.purchaseCurrency,
                        exchangeRate = 1.0,
                        convertedTotalPrice = totalRaw.setScale(2, RoundingMode.HALF_UP).toDouble(),
                        originalProductPrice = prod.purchasePrice,
                        originalProductCurrency = prod.purchaseCurrency,
                        uuid = UUID.randomUUID().toString()
                    )
                )
            }
        }

        return SyntheticDataset(
            customers = customers,
            products = products,
            categories = categories,
            units = units,
            suppliers = suppliers,
            invoices = invoices,
            invoiceItems = invoiceItems,
            purchases = purchases,
            payments = payments,
            returns = returns,
            returnItems = returnItems,
            exchangeRates = exchangeRates,
            storeSettings = storeSettings
        )
    }
}
