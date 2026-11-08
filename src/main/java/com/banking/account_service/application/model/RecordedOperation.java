package com.banking.account_service.application.model;

import com.banking.account_service.domain.model.CurrencyCode;
import com.banking.account_service.domain.model.OperationType;
import com.banking.account_service.domain.model.PostingContext;
import com.banking.account_service.domain.model.TransferKind;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A completed operation as stored under its operationId: the request data used to
 * recognize repeats and the result returned to the client.
 */
public record RecordedOperation(
        UUID operationId,
        OperationType type,
        UUID accountId,
        UUID counterpartyAccountId,
        BigDecimal amount,
        String description,
        CurrencyCode currency,
        TransferKind transferKind,
        BigDecimal fee,
        BigDecimal balanceAfter,
        Instant occurredAt,
        LocalDate businessDate
) {

    /** The record a new operation leaves once it commits. */
    public static RecordedOperation of(OperationRequest request, PostingContext context, OperationOutcome outcome) {
        return new RecordedOperation(request.operationId(), request.type(), request.accountId(),
                request.counterpartyAccountId(), request.amount(), request.description(), outcome.currency(),
                outcome.transferKind(), outcome.fee(), outcome.balanceAfter(), context.occurredAt(),
                context.businessDate());
    }
}
