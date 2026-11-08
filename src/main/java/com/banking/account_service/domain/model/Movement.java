package com.banking.account_service.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One entry of the account history. It keeps the amount, the fee charged with it, the
 * balance right after it and the operation that produced it.
 *
 * @param sequence     position in the account history, starting at 1
 * @param businessDate date in the business time zone, used for monthly rules and reports
 */
public record Movement(
        UUID id,
        UUID accountId,
        UUID operationId,
        long sequence,
        MovementType type,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal balanceAfter,
        UUID counterpartyAccountId,
        String description,
        Instant occurredAt,
        LocalDate businessDate
) {
}
