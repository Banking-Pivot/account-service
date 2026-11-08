package com.banking.account_service.application.query;

import java.util.Objects;
import java.util.UUID;

/** Reads one account, ACTIVE or CLOSED. */
public record GetAccountQuery(UUID accountId) {

    public GetAccountQuery {
        Objects.requireNonNull(accountId, "accountId");
    }
}
