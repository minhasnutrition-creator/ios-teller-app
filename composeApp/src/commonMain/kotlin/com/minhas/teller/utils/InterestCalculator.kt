package com.minhas.teller.utils

import java.util.Date

/**
 * Simple interest on overdue bill amounts.
 *
 * The interest controls live on [com.minhas.teller.data.entities.Voucher]
 * (`chargeInterest` / `interestRate` / `interestDays`):
 *  - `interestRate`  = annual percentage rate (e.g. 12 means 12% per annum),
 *  - `interestDays`  = interest-free grace period in days from the bill date,
 * after which simple interest accrues on the still-unpaid amount.
 *
 * Pure and deterministic — no DB, context or coroutines — so it's unit-testable.
 */
object InterestCalculator {

    private const val MILLIS_PER_DAY = 86_400_000L

    /** Whole days overdue: bill date + grace days → today (0 when not yet due). */
    fun daysOverdue(billDate: Date, graceDays: Int, today: Date = Date()): Int {
        val due = billDate.time + graceDays.toLong() * MILLIS_PER_DAY
        val elapsed = today.time - due
        return if (elapsed <= 0L) 0 else (elapsed / MILLIS_PER_DAY).toInt()
    }

    /**
     * Simple interest = principal × annualRate% × days / 365, rounded to paise.
     * Returns 0 when the principal, rate or overdue days are non-positive.
     */
    fun simpleInterest(principal: Double, annualRatePercent: Double, daysOverdue: Int): Double {
        if (principal <= 0.0 || annualRatePercent <= 0.0 || daysOverdue <= 0) return 0.0
        return GstComplianceRules.round2(principal * annualRatePercent / 100.0 * daysOverdue / 365.0)
    }
}
