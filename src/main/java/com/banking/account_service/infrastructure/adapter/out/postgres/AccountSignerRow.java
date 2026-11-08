package com.banking.account_service.infrastructure.adapter.out.postgres;

import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/** Row of {@code account_signers}. The key is (account, customer); rows are inserted and deleted, never updated. */
@Table("account_signers")
public record AccountSignerRow(UUID accountId, UUID customerId) {
}
