package com.banking.account_service.infrastructure.adapter.out.postgres;

import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/** Row of {@code account_holders}. The key is (account, customer); rows are inserted and deleted, never updated. */
@Table("account_holders")
public record AccountHolderRow(UUID accountId, UUID customerId, boolean isPrimary) {
}
