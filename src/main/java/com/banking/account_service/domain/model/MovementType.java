package com.banking.account_service.domain.model;

/**
 * Entry in the account history.
 *
 * <p>Credits increase the balance and debits decrease it. Only the opening deposit
 * stays outside the monthly quota and limit.
 */
public enum MovementType {
    OPENING_DEPOSIT(true, false),
    DEPOSIT(true, true),
    WITHDRAWAL(false, true),
    TRANSFER_IN(true, true),
    TRANSFER_OUT(false, true);

    private final boolean credit;
    private final boolean countsTowardMonthlyUsage;

    MovementType(boolean credit, boolean countsTowardMonthlyUsage) {
        this.credit = credit;
        this.countsTowardMonthlyUsage = countsTowardMonthlyUsage;
    }

    /** Whether the movement adds the amount to the balance. */
    public boolean isCredit() {
        return credit;
    }

    /** Whether the movement uses the monthly free quota and the monthly limit. */
    public boolean countsTowardMonthlyUsage() {
        return countsTowardMonthlyUsage;
    }
}
