package com.banking.account_service.application.model;

import com.banking.account_service.domain.model.MonthlyUsage;
import com.banking.account_service.domain.model.ProductPolicy;

import java.math.BigDecimal;
import java.time.YearMonth;

/**
 * Usage of the monthly quota and limit.
 *
 * @param movementLimit      {@code null} when the product has no hard limit
 * @param movementsRemaining {@code null} when the product has no hard limit
 * @param nextMovementFee    fee the next counted movement would pay
 */
public record MonthlyUsageView(
        YearMonth period,
        int movementsUsed,
        int freeMovements,
        int freeMovementsRemaining,
        Integer movementLimit,
        Integer movementsRemaining,
        BigDecimal nextMovementFee
) {

    /** Combines the stored counter, already moved to the current month, with the product terms. */
    public static MonthlyUsageView of(MonthlyUsage usage, ProductPolicy policy) {
        int used = usage.movements();
        Integer limit = policy.monthlyMovementLimit();
        return new MonthlyUsageView(
                usage.period(),
                used,
                policy.freeMonthlyMovements(),
                Math.max(0, policy.freeMonthlyMovements() - used),
                limit,
                limit == null ? null : Math.max(0, limit - used),
                policy.feeAfter(used));
    }
}
