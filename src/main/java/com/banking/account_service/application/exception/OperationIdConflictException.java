package com.banking.account_service.application.exception;

import java.util.UUID;

/** The operationId already identifies an operation with different data. */
public class OperationIdConflictException extends RuntimeException {

    public OperationIdConflictException(UUID operationId) {
        super("Operation " + operationId + " was already used with different data.");
    }
}
