package com.banking.account_service.domain.model;

import java.time.YearMonth;
import java.util.Objects;

/**
 * Movements that used the quota in one business month. The counter is stored with the
 * account and restarts at zero when an operation happens in a later month.
 */
public record MonthlyUsage(YearMonth period, int movements) {

    public MonthlyUsage {
        Objects.requireNonNull(period, "period");
        if (movements < 0) {
            throw new IllegalArgumentException("movements cannot be negative");
        }
    }

    /** Empty usage for the given month. */
    public static MonthlyUsage startOf(YearMonth period) {
        return new MonthlyUsage(period, 0);
    }

    /** Usage as seen from the given month: a later month starts again from zero. */
    public MonthlyUsage asOf(YearMonth current) {
        return current.isAfter(period) ? startOf(current) : this;
    }

    /** Usage after one more counted movement. */
    public MonthlyUsage plusOne() {
        return new MonthlyUsage(period, movements + 1);
    }
}
