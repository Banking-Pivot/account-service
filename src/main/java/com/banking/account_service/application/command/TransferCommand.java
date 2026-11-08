package com.banking.account_service.application.command;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Transfer request identified by the client's idempotency key. */
public record TransferCommand(
        UUID operationId,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String description
) {

    public TransferCommand {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(sourceAccountId, "sourceAccountId");
        Objects.requireNonNull(destinationAccountId, "destinationAccountId");
    }
}
