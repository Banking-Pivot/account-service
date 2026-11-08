package com.banking.account_service.application.query;

import com.banking.account_service.application.exception.InvalidQueryException;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Lists the history of one account, newest first.
 *
 * @param from optional first business date, inclusive
 * @param to   optional last business date, inclusive
 */
public record ListAccountMovementsQuery(UUID accountId, PageRequest pageRequest, LocalDate from, LocalDate to) {

    public ListAccountMovementsQuery {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidQueryException("from must not be after to.");
        }
    }
}
