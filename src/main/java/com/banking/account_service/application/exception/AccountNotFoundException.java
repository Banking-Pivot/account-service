package com.banking.account_service.application.exception;

import java.util.UUID;

/** The account in the request path does not exist. */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID accountId) {
        super("Account " + accountId + " was not found.");
    }
}
