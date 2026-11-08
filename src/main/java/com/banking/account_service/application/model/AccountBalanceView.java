package com.banking.account_service.application.model;

import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.AccountStatus;
import com.banking.account_service.domain.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Balance with the usage of the current business month.
 *
 * @param operationDayOfMonth fixed term only
 * @param nextOperationDate   fixed term only; next business date that accepts a movement
 */
public record AccountBalanceView(
        UUID accountId,
        AccountProduct product,
        CurrencyCode currency,
        BigDecimal balance,
        AccountStatus status,
        Instant asOf,
        MonthlyUsageView usage,
        BigDecimal monthlyMaintenanceFee,
        Integer operationDayOfMonth,
        LocalDate nextOperationDate
) {
}
