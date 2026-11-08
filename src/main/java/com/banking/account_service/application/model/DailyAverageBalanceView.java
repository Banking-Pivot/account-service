package com.banking.account_service.application.model;

import com.banking.account_service.domain.model.CurrencyCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/**
 * Average daily balance of a month.
 *
 * @param requiredMinimum  VIP savings only
 * @param meetsRequirement VIP savings only
 */
public record DailyAverageBalanceView(
        UUID accountId,
        CurrencyCode currency,
        YearMonth period,
        LocalDate fromDate,
        LocalDate toDate,
        int daysCounted,
        BigDecimal average,
        BigDecimal requiredMinimum,
        Boolean meetsRequirement
) {
}
