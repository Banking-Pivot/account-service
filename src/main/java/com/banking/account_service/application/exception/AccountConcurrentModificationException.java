package com.banking.account_service.application.exception;

import java.util.UUID;

/** The revision sent in If-Match is not the current revision of the account. */
public class AccountConcurrentModificationException extends RuntimeException {

    public AccountConcurrentModificationException(UUID accountId) {
        super("Account " + accountId + " changed since it was read.");
    }
}
