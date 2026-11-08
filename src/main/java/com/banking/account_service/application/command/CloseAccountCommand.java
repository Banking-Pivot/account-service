package com.banking.account_service.application.command;

import java.util.Objects;
import java.util.UUID;

/** Logical closing, conditioned on the revision the client read. */
public record CloseAccountCommand(UUID accountId, long expectedVersion) {

    public CloseAccountCommand {
        Objects.requireNonNull(accountId, "accountId");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion cannot be negative");
        }
    }
}
