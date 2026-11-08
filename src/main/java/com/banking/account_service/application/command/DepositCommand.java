package com.banking.account_service.application.command;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Deposit request identified by the client's idempotency key. */
public record DepositCommand(UUID operationId, UUID accountId, BigDecimal amount, String description) {

    public DepositCommand {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(accountId, "accountId");
    }
}
