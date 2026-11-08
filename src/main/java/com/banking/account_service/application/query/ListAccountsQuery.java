package com.banking.account_service.application.query;

import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.AccountStatus;

import java.util.Objects;
import java.util.UUID;

/**
 * Lists accounts ordered by ID.
 *
 * @param customerId optional; matches any holder of the account
 * @param product    optional filter
 * @param status     optional filter
 */
public record ListAccountsQuery(PageRequest pageRequest, UUID customerId, AccountProduct product, AccountStatus status) {

    public ListAccountsQuery {
        Objects.requireNonNull(pageRequest, "pageRequest");
    }
}
