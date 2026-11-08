package com.banking.account_service.application.model;

import java.util.Objects;

/**
 * What a deposit, withdrawal or transfer returns.
 *
 * @param replayed {@code true} when the request repeated a recorded operation and no
 *                 money moved; the operation is then the original one
 */
public record OperationResult(RecordedOperation operation, boolean replayed) {

    public OperationResult {
        Objects.requireNonNull(operation, "operation");
    }
}
