package com.banking.account_service.domain.exception;

import java.util.UUID;

/** The account is CLOSED and accepts no more changes. */
public class AccountClosedException extends RuntimeException {

    public AccountClosedException(UUID accountId) {
        super("Account " + accountId + " is closed.");
    }
}
