package com.banking.account_service.infrastructure.adapter.out.postgres;

import com.banking.account_service.application.model.OperationRequest;
import com.banking.account_service.application.model.RecordedOperation;
import com.banking.account_service.domain.model.CurrencyCode;
import com.banking.account_service.domain.model.OperationType;
import com.banking.account_service.domain.model.TransferKind;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Row of {@code operations}. The result columns are empty while the operation is
 * PROCESSING, a state only its own transaction can see.
 */
@Table("operations")
public record OperationRow(
        @Id UUID id,
        OperationType operationType,
        UUID accountId,
        UUID counterpartyAccountId,
        BigDecimal amount,
        String description,
        String status,
        CurrencyCode currency,
        TransferKind transferKind,
        BigDecimal fee,
        BigDecimal balanceAfter,
        Instant occurredAt,
        LocalDate businessDate
) {

    static final String PROCESSING = "PROCESSING";
    static final String COMPLETED = "COMPLETED";

    /** The claim of a key: request data only. */
    static OperationRow claimed(OperationRequest request) {
        return new OperationRow(request.operationId(), request.type(), request.accountId(),
                request.counterpartyAccountId(), request.amount(), request.description(), PROCESSING,
                null, null, null, null, null, null);
    }

    RecordedOperation toRecorded() {
        return new RecordedOperation(id, operationType, accountId, counterpartyAccountId, amount, description,
                currency, transferKind, fee, balanceAfter, occurredAt, businessDate);
    }
}
