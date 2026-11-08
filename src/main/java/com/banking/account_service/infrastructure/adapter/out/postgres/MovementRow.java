package com.banking.account_service.infrastructure.adapter.out.postgres;

import com.banking.account_service.domain.model.Movement;
import com.banking.account_service.domain.model.MovementType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Row of {@code account_movements}, the append-only history. */
@Table("account_movements")
public record MovementRow(
        @Id UUID id,
        UUID accountId,
        UUID operationId,
        long sequenceNumber,
        MovementType movementType,
        BigDecimal amount,
        BigDecimal fee,
        BigDecimal balanceAfter,
        UUID counterpartyAccountId,
        String description,
        Instant occurredAt,
        LocalDate businessDate
) {

    static MovementRow from(Movement movement) {
        return new MovementRow(movement.id(), movement.accountId(), movement.operationId(), movement.sequence(),
                movement.type(), movement.amount(), movement.fee(), movement.balanceAfter(),
                movement.counterpartyAccountId(), movement.description(), movement.occurredAt(),
                movement.businessDate());
    }

    Movement toDomain() {
        return new Movement(id, accountId, operationId, sequenceNumber, movementType, amount, fee, balanceAfter,
                counterpartyAccountId, description, occurredAt, businessDate);
    }
}
