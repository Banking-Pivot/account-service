package com.banking.account_service.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Terms of one product in one currency, loaded from the Config Server.
 *
 * <p>The values are configurable; the shape of each product is not. The constructor
 * rejects terms that contradict the product: savings has a monthly limit and no
 * maintenance fee, checking has no limit, SME checking is exempt from maintenance,
 * fixed term allows one movement per month on its operation day and only VIP savings
 * requires an average daily balance.
 *
 * @param openingMinimum             minimum initial deposit, can be zero
 * @param freeMonthlyMovements       movements per month without fee (quota)
 * @param movementFee                fee for each movement beyond the quota
 * @param monthlyMovementLimit       hard monthly limit, {@code null} when there is none
 * @param monthlyMaintenanceFee      monthly maintenance fee, zero when exempt
 * @param minimumDailyAverageBalance VIP savings only, otherwise {@code null}
 * @param operationDayOfMonth        fixed term only, otherwise {@code null}
 */
public record ProductPolicy(
        AccountProduct product,
        CurrencyCode currency,
        BigDecimal openingMinimum,
        int freeMonthlyMovements,
        BigDecimal movementFee,
        Integer monthlyMovementLimit,
        BigDecimal monthlyMaintenanceFee,
        BigDecimal minimumDailyAverageBalance,
        Integer operationDayOfMonth
) {

    /** Fixed-term accounts allow a single deposit or withdrawal per month. */
    public static final int FIXED_TERM_MONTHLY_LIMIT = 1;

    public ProductPolicy {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(currency, "currency");
        String prefix = product + "/" + currency + " ";
        openingMinimum = Amounts.configured(openingMinimum, prefix + "opening minimum");
        movementFee = Amounts.configured(movementFee, prefix + "movement fee");
        monthlyMaintenanceFee = Amounts.configured(monthlyMaintenanceFee, prefix + "maintenance fee");
        if (minimumDailyAverageBalance != null) {
            minimumDailyAverageBalance = Amounts.configured(
                    minimumDailyAverageBalance, prefix + "minimum daily average balance");
        }
        if (freeMonthlyMovements < 0) {
            throw new IllegalArgumentException(prefix + "free monthly movements cannot be negative.");
        }
        if (monthlyMovementLimit != null && monthlyMovementLimit < 1) {
            throw new IllegalArgumentException(prefix + "monthly movement limit must be at least 1.");
        }
        validateShape(prefix, product, monthlyMovementLimit, monthlyMaintenanceFee,
                minimumDailyAverageBalance, operationDayOfMonth);
    }

    private static void validateShape(
            String prefix,
            AccountProduct product,
            Integer limit,
            BigDecimal maintenance,
            BigDecimal minimumAverage,
            Integer operationDay
    ) {
        boolean savings = product.type() == AccountType.SAVINGS;
        boolean fixedTerm = product == AccountProduct.FIXED_TERM;
        require(savings == (limit != null) || fixedTerm,
                prefix + (savings ? "requires a monthly movement limit." : "cannot have a monthly movement limit."));
        require(!fixedTerm || Objects.equals(limit, FIXED_TERM_MONTHLY_LIMIT),
                prefix + "allows exactly one movement per month.");
        require(product == AccountProduct.CHECKING || maintenance.signum() == 0,
                prefix + "is exempt from the maintenance fee.");
        require((product == AccountProduct.VIP_SAVINGS) == (minimumAverage != null),
                prefix + (product == AccountProduct.VIP_SAVINGS
                        ? "requires a minimum daily average balance."
                        : "cannot have a minimum daily average balance."));
        require(fixedTerm == (operationDay != null),
                prefix + (fixedTerm ? "requires an operation day of month." : "cannot have an operation day."));
        require(operationDay == null || (operationDay >= 1 && operationDay <= 31),
                prefix + "operation day must be between 1 and 31.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    /** Hard monthly limit, empty when the product has none. */
    public OptionalInt movementLimit() {
        return monthlyMovementLimit == null ? OptionalInt.empty() : OptionalInt.of(monthlyMovementLimit);
    }

    /** Whether the limit forbids another movement after {@code used} movements this month. */
    public boolean limitReached(int used) {
        return monthlyMovementLimit != null && used >= monthlyMovementLimit;
    }

    /** Fee for the next movement after {@code used} movements this month. */
    public BigDecimal feeAfter(int used) {
        return used >= freeMonthlyMovements ? movementFee : Amounts.ZERO;
    }

    /**
     * Fixed term only: the date in the given month that accepts the movement. Days beyond
     * the length of the month fall on its last day (31 means the last day of every month).
     */
    public LocalDate operationDateIn(YearMonth month) {
        if (operationDayOfMonth == null) {
            throw new IllegalStateException(product + " has no operation day.");
        }
        return month.atDay(Math.min(operationDayOfMonth, month.lengthOfMonth()));
    }
}
