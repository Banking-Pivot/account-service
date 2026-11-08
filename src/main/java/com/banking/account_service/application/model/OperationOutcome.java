package com.banking.account_service.application.model;

import com.banking.account_service.domain.model.CurrencyCode;
import com.banking.account_service.domain.model.Movement;
import com.banking.account_service.domain.model.TransferKind;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Result of an operation for the account in the request path, stored with the operation.
 *
 * @param transferKind {@code null} unless the operation is a transfer
 */
public record OperationOutcome(CurrencyCode currency, TransferKind transferKind, BigDecimal fee, BigDecimal balanceAfter) {

    public OperationOutcome {
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(fee, "fee");
        Objects.requireNonNull(balanceAfter, "balanceAfter");
    }

    /** Outcome taken from the movement of the account in the path. */
    public static OperationOutcome of(CurrencyCode currency, TransferKind transferKind, Movement movement) {
        return new OperationOutcome(currency, transferKind, movement.fee(), movement.balanceAfter());
    }
}
