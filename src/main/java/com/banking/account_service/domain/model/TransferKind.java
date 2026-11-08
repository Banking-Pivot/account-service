package com.banking.account_service.domain.model;

/** Whether a transfer stays between accounts of the same primary holder. */
public enum TransferKind {
    OWN_ACCOUNTS,
    THIRD_PARTY
}
