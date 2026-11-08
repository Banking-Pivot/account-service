package com.banking.account_service.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * When and under which operation movements are posted. Both legs of a transfer share
 * the same context, so they have the same instant and business date.
 */
public record PostingContext(UUID operationId, Instant occurredAt, LocalDate businessDate, String description) {

    public PostingContext {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(businessDate, "businessDate");
    }
}
