package com.banking.account_service.application.model;

import com.banking.account_service.domain.model.Account;
import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.AccountStatus;
import com.banking.account_service.domain.model.AccountType;
import com.banking.account_service.domain.model.CurrencyCode;
import com.banking.account_service.domain.model.CustomerProfile;
import com.banking.account_service.domain.model.CustomerType;
import com.banking.account_service.domain.model.MonthlyUsage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read model of an account. {@code version} is the revision exposed as ETag. */
public record AccountView(
        UUID id,
        AccountProduct product,
        CurrencyCode currency,
        CustomerType customerType,
        CustomerProfile customerProfile,
        UUID primaryHolderId,
        List<UUID> holderIds,
        List<UUID> signerIds,
        BigDecimal balance,
        AccountStatus status,
        MonthlyUsage usage,
        Instant openedAt,
        Instant updatedAt,
        Instant closedAt,
        long version
) {

    public AccountView {
        holderIds = List.copyOf(holderIds);
        signerIds = List.copyOf(signerIds);
    }

    /** Projects the aggregate after a command. */
    public static AccountView from(Account account) {
        return new AccountView(account.getId(), account.getProduct(), account.getCurrency(),
                account.getCustomerType(), account.getCustomerProfile(), account.primaryHolderId(),
                account.getParticipants().holderIds(), account.getParticipants().signerIds(),
                account.getBalance(), account.getStatus(), account.getUsage(), account.getOpenedAt(),
                account.getUpdatedAt(), account.getClosedAt(), account.getVersion());
    }

    /** Account family of the product. */
    public AccountType type() {
        return product.type();
    }
}
