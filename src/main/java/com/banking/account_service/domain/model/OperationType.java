package com.banking.account_service.domain.model;

/** Monetary operation recorded under an operation ID. OPENING_DEPOSIT is created by the service. */
public enum OperationType {
    OPENING_DEPOSIT,
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER
}
