package com.banking.account_service.application.query;

import java.util.Objects;
import java.util.UUID;

/** Reads the balance and the usage of the current business month. */
public record GetAccountBalanceQuery(UUID accountId) {

    public GetAccountBalanceQuery {
        Objects.requireNonNull(accountId, "accountId");
    }
}
