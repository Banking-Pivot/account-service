package com.banking.account_service.domain.model;

/** Lifecycle of an account. Closing is logical: the record and its history remain. */
public enum AccountStatus {
    ACTIVE,
    CLOSED
}
