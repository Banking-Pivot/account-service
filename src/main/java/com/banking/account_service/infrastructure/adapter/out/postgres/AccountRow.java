package com.banking.account_service.infrastructure.adapter.out.postgres;

import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.AccountStatus;
import com.banking.account_service.domain.model.AccountType;
import com.banking.account_service.domain.model.CurrencyCode;
import com.banking.account_service.domain.model.CustomerProfile;
import com.banking.account_service.domain.model.CustomerType;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Row of {@code accounts}. Enums are stored by name. {@code version} is managed by the
 * domain and written with a conditional UPDATE, not by Spring Data's optimistic locking.
 *
 * @param usageMonth first day of the business month of the monthly counter
 */
@Table("accounts")
public record AccountRow(
        @Id UUID id,
        UUID primaryHolderId,
        CustomerType customerType,
        CustomerProfile customerProfile,
        AccountType accountType,
        AccountProduct product,
        CurrencyCode currency,
        BigDecimal balance,
        AccountStatus status,
        LocalDate usageMonth,
        int usageMovements,
        long movementSequence,
        Instant openedAt,
        Instant updatedAt,
        Instant closedAt,
        long version
) {
}
