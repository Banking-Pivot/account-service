package com.banking.account_service.domain.model;

/** Account family. Personal customers can hold one ACTIVE savings and one ACTIVE checking account. */
public enum AccountType {
    SAVINGS,
    CHECKING,
    FIXED_TERM
}
