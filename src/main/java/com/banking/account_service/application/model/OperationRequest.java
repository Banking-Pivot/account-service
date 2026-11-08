package com.banking.account_service.application.model;

import com.banking.account_service.domain.model.Amounts;
import com.banking.account_service.domain.model.OperationType;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * The data an operationId stands for. A repeated request is recognized only when every
 * field matches the recorded operation.
 *
 * @param accountId             account in the request path
 * @param counterpartyAccountId transfer destination, {@code null} otherwise
 * @param description           stripped; blank becomes {@code null}
 */
public record OperationRequest(
        UUID operationId,
        OperationType type,
        UUID accountId,
        UUID counterpartyAccountId,
        BigDecimal amount,
        String description
) {

    public OperationRequest {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(accountId, "accountId");
        amount = Amounts.positive(amount);
        description = description == null || description.isBlank() ? null : description.strip();
    }

    /** Whether the recorded operation was created by exactly this request. */
    public boolean matches(RecordedOperation recorded) {
        return type == recorded.type()
                && accountId.equals(recorded.accountId())
                && Objects.equals(counterpartyAccountId, recorded.counterpartyAccountId())
                && amount.compareTo(recorded.amount()) == 0
                && Objects.equals(description, recorded.description());
    }
}
