package com.example.synthetic

import com.example.data.model.*
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Independent financial calculation oracle.
 * MUST NOT call any production calculation classes (e.g., FinancialCalculator, CurrencyConversionService)
 * to ensure that identical bugs are not masked.
 */
object FinancialTestOracle {

    private const val SCALE = 8
    private const val MONETARY_SCALE = 2

    // Fixed canonical benchmark rates for YER, SAR, USD
    val BENCHMARK_RATES: Map<Pair<String, String>, BigDecimal> = mapOf(
        ("USD" to "YER") to BigDecimal("1500.00"),
        ("SAR" to "YER") to BigDecimal("400.00"),
        ("USD" to "SAR") to BigDecimal("3.75")
    )

    /**
     * Resolves exchange rate independently using direct lookup, inverse inversion, or triangulation.
     */
    fun resolveRate(
        fromCurrencyRaw: String,
        toCurrencyRaw: String,
        customRates: Map<Pair<String, String>, BigDecimal> = BENCHMARK_RATES
    ): BigDecimal {
        val from = normalizeCurrencyCode(fromCurrencyRaw)
        val to = normalizeCurrencyCode(toCurrencyRaw)

        if (from == to) return BigDecimal.ONE.setScale(SCALE, RoundingMode.HALF_UP)

        // 1. Direct
        customRates[from to to]?.let {
            if (it.compareTo(BigDecimal.ZERO) > 0) return it.setScale(SCALE, RoundingMode.HALF_UP)
        }

        // 2. Inverse
        customRates[to to from]?.let { inverse ->
            if (inverse.compareTo(BigDecimal.ZERO) > 0) {
                return BigDecimal.ONE.divide(inverse, SCALE, RoundingMode.HALF_UP)
            }
        }

        // 3. Triangular via YER
        val fromToYer = if (from == "YER") BigDecimal.ONE else {
            customRates[from to "YER"] ?: customRates["YER" to from]?.let {
                if (it.compareTo(BigDecimal.ZERO) > 0) BigDecimal.ONE.divide(it, SCALE, RoundingMode.HALF_UP) else null
            }
        }

        val toToYer = if (to == "YER") BigDecimal.ONE else {
            customRates[to to "YER"] ?: customRates["YER" to to]?.let {
                if (it.compareTo(BigDecimal.ZERO) > 0) BigDecimal.ONE.divide(it, SCALE, RoundingMode.HALF_UP) else null
            }
        }

        if (fromToYer != null && toToYer != null && toToYer.compareTo(BigDecimal.ZERO) > 0) {
            return fromToYer.divide(toToYer, SCALE, RoundingMode.HALF_UP)
        }

        throw IllegalArgumentException("Oracle: Missing exchange rate between '$from' and '$to'")
    }

    /**
     * Converts an amount using the independent oracle resolver.
     */
    fun convertAmount(
        amount: BigDecimal,
        fromCurrency: String,
        toCurrency: String,
        customRates: Map<Pair<String, String>, BigDecimal> = BENCHMARK_RATES
    ): BigDecimal {
        val from = normalizeCurrencyCode(fromCurrency)
        val to = normalizeCurrencyCode(toCurrency)
        if (from == to) return amount.setScale(MONETARY_SCALE, RoundingMode.HALF_UP)

        val rate = resolveRate(from, to, customRates)
        return amount.multiply(rate).setScale(MONETARY_SCALE, RoundingMode.HALF_UP)
    }

    fun normalizeCurrencyCode(currency: String): String {
        val trimmed = currency.trim()
        return when {
            trimmed.equals("YER", ignoreCase = true) || trimmed.contains("يمن") -> "YER"
            trimmed.equals("SAR", ignoreCase = true) || trimmed.contains("سعود") -> "SAR"
            trimmed.equals("USD", ignoreCase = true) || trimmed.contains("دولار") || trimmed == "$" -> "USD"
            else -> trimmed
        }
    }

    /**
     * Independently evaluates an invoice and its line items.
     */
    fun calculateInvoiceExpectation(
        invoice: Invoice,
        items: List<InvoiceItem>,
        rates: Map<Pair<String, String>, BigDecimal> = BENCHMARK_RATES
    ): InvoiceFinancialExpectation {
        val invoiceCurr = normalizeCurrencyCode(invoice.currency)
        var rawSubtotal = BigDecimal.ZERO

        for (item in items) {
            val itemLineTotal = BigDecimal.valueOf(item.quantity.toLong())
                .multiply(BigDecimal.valueOf(item.unitPrice))
                .setScale(MONETARY_SCALE, RoundingMode.HALF_UP)

            val itemCurr = normalizeCurrencyCode(item.currency)
            val convertedLineTotal = if (itemCurr == invoiceCurr) {
                itemLineTotal
            } else {
                convertAmount(itemLineTotal, itemCurr, invoiceCurr, rates)
            }
            rawSubtotal = rawSubtotal.add(convertedLineTotal)
        }

        val discount = BigDecimal.valueOf(invoice.discount).setScale(MONETARY_SCALE, RoundingMode.HALF_UP)
        val subtotalAfterDiscount = rawSubtotal.subtract(discount).max(BigDecimal.ZERO)

        val taxAmount = if (invoice.taxRate > 0.0) {
            subtotalAfterDiscount.multiply(BigDecimal.valueOf(invoice.taxRate / 100.0))
                .setScale(MONETARY_SCALE, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val finalTotal = subtotalAfterDiscount.add(taxAmount).setScale(MONETARY_SCALE, RoundingMode.HALF_UP)
        val rateToBase = resolveRate(invoiceCurr, normalizeCurrencyCode(invoice.baseCurrency), rates)
        val convertedTotal = finalTotal.multiply(rateToBase).setScale(MONETARY_SCALE, RoundingMode.HALF_UP)

        val paid = BigDecimal.valueOf(invoice.paidAmount).setScale(MONETARY_SCALE, RoundingMode.HALF_UP)
        val expectedRemaining = finalTotal.subtract(paid).max(BigDecimal.ZERO).setScale(MONETARY_SCALE, RoundingMode.HALF_UP)

        return InvoiceFinancialExpectation(
            invoiceId = invoice.id,
            invoiceNumber = invoice.invoiceNumber,
            rawSubtotal = rawSubtotal,
            discountAmount = discount,
            subtotalAfterDiscount = subtotalAfterDiscount,
            taxAmount = taxAmount,
            finalTotal = finalTotal,
            currency = invoiceCurr,
            exchangeRate = rateToBase,
            convertedTotal = convertedTotal,
            totalPaid = paid,
            expectedRemaining = expectedRemaining
        )
    }

    /**
     * Independently recalculates the customer balance across all customer transactions.
     */
    fun calculateCustomerBalance(
        client: Client,
        invoices: List<Invoice>,
        payments: List<Payment>,
        returns: List<ProductReturn>,
        rates: Map<Pair<String, String>, BigDecimal> = BENCHMARK_RATES
    ): CustomerFinancialExpectation {
        val clientBaseCurr = normalizeCurrencyCode(client.currency)

        // 1. Opening balance
        val rawInitial = BigDecimal.valueOf(client.initialBalance).setScale(MONETARY_SCALE, RoundingMode.HALF_UP)
        val initialCurr = normalizeCurrencyCode(client.initialBalanceCurrency)
        val convertedInitial = convertAmount(rawInitial, initialCurr, clientBaseCurr, rates)

        val isDebit = client.balanceType == "مدين" || client.balanceType == "عليه لنا"
        val openingSigned = if (isDebit) convertedInitial else convertedInitial.negate()

        // 2. Invoices (active, non-draft)
        var totalInvoicedInBase = BigDecimal.ZERO
        val validInvoices = invoices.filter { !it.isDraft }
        for (inv in validInvoices) {
            val invCurr = normalizeCurrencyCode(inv.currency)
            val invTotal = BigDecimal.valueOf(inv.totalAmount).setScale(MONETARY_SCALE, RoundingMode.HALF_UP)
            val convertedInv = if (invCurr == clientBaseCurr) {
                invTotal
            } else if (inv.convertedTotalAmount > 0.0) {
                BigDecimal.valueOf(inv.convertedTotalAmount).setScale(MONETARY_SCALE, RoundingMode.HALF_UP)
            } else {
                convertAmount(invTotal, invCurr, clientBaseCurr, rates)
            }
            totalInvoicedInBase = totalInvoicedInBase.add(convertedInv)
        }

        // 3. Payments
        var totalPaidInBase = BigDecimal.ZERO
        for (pay in payments) {
            val payCurr = normalizeCurrencyCode(pay.currency)
            val payAmount = BigDecimal.valueOf(pay.amount).setScale(MONETARY_SCALE, RoundingMode.HALF_UP)
            val convertedPay = if (payCurr == clientBaseCurr) {
                payAmount
            } else if (pay.convertedAmount > 0.0) {
                BigDecimal.valueOf(pay.convertedAmount).setScale(MONETARY_SCALE, RoundingMode.HALF_UP)
            } else {
                convertAmount(payAmount, payCurr, clientBaseCurr, rates)
            }
            totalPaidInBase = totalPaidInBase.add(convertedPay)
        }

        // 4. Customer returns (credited to balance, excluding cash refunds)
        var totalReturnsCreditedInBase = BigDecimal.ZERO
        val validReturns = returns.filter { it.type == "CUSTOMER" && it.settlementType != "استرداد نقدي" }
        for (ret in validReturns) {
            val retCurr = normalizeCurrencyCode(ret.currency)
            val retTotal = BigDecimal.valueOf(ret.totalAmount).setScale(MONETARY_SCALE, RoundingMode.HALF_UP)
            val convertedRet = if (retCurr == clientBaseCurr) {
                retTotal
            } else if (ret.convertedTotalAmount > 0.0) {
                BigDecimal.valueOf(ret.convertedTotalAmount).setScale(MONETARY_SCALE, RoundingMode.HALF_UP)
            } else {
                convertAmount(retTotal, retCurr, clientBaseCurr, rates)
            }
            totalReturnsCreditedInBase = totalReturnsCreditedInBase.add(convertedRet)
        }

        // Net Balance = Opening + Invoiced - Paid - Credited Returns
        val netBalance = openingSigned
            .add(totalInvoicedInBase)
            .subtract(totalPaidInBase)
            .subtract(totalReturnsCreditedInBase)
            .setScale(MONETARY_SCALE, RoundingMode.HALF_UP)

        return CustomerFinancialExpectation(
            customerId = client.id,
            customerName = client.name,
            currency = clientBaseCurr,
            openingBalance = openingSigned,
            totalInvoicedBase = totalInvoicedInBase,
            totalPaidBase = totalPaidInBase,
            totalReturnsCreditedBase = totalReturnsCreditedInBase,
            expectedNetBalance = netBalance,
            invoiceCount = validInvoices.size,
            paymentCount = payments.size,
            returnCount = validReturns.size
        )
    }
}
