package com.banking.account_service.domain.model;

import com.banking.account_service.domain.exception.AccountRuleViolationException;
import com.banking.account_service.domain.exception.RuleViolation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Money rules shared by the domain: two decimals, never rounded silently.
 * Amounts match the NUMERIC(19, 2) columns of the database.
 */
public final class Amounts {

    /** Decimal places of every amount. */
    public static final int SCALE = 2;

    /** Largest amount a single request can move. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");

    /** Largest balance NUMERIC(19, 2) can hold. */
    public static final BigDecimal MAX_BALANCE = new BigDecimal("99999999999999999.99");

    /** Zero with the money scale. */
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Amounts() {
    }

    /** Validates a strictly positive request amount and returns it with scale 2. */
    public static BigDecimal positive(BigDecimal amount) {
        BigDecimal normalized = nonNegative(amount);
        if (normalized.signum() == 0) {
            throw invalid("The amount must be greater than zero.");
        }
        return normalized;
    }

    /** Validates a zero or positive request amount and returns it with scale 2. */
    public static BigDecimal nonNegative(BigDecimal amount) {
        if (amount == null) {
            throw invalid("The amount is required.");
        }
        if (amount.signum() < 0) {
            throw invalid("The amount cannot be negative.");
        }
        BigDecimal normalized = withScale(amount, "The amount can have at most two decimals.");
        if (normalized.compareTo(MAX_AMOUNT) > 0) {
            throw invalid("The amount exceeds the maximum per operation.");
        }
        return normalized;
    }

    /**
     * Normalizes a configured amount (fee, minimum) to scale 2.
     *
     * @throws IllegalArgumentException when it is negative or has more than two decimals
     */
    public static BigDecimal configured(BigDecimal amount, String name) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException(name + " must be zero or positive.");
        }
        try {
            return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(name + " can have at most two decimals.", exception);
        }
    }

    private static BigDecimal withScale(BigDecimal amount, String message) {
        try {
            return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw invalid(message);
        }
    }

    private static AccountRuleViolationException invalid(String message) {
        return new AccountRuleViolationException(RuleViolation.INVALID_AMOUNT, message);
    }
}
