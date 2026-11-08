package com.banking.account_service.application.exception;

import java.util.UUID;

/**
 * Another transaction committed the same operationId first. Internal signal: the caller
 * rolls back and answers with the recorded result instead.
 */
public class OperationAlreadyRecordedException extends RuntimeException {

    public OperationAlreadyRecordedException(UUID operationId, Throwable cause) {
        super("Operation " + operationId + " is already recorded.", cause);
    }
}
