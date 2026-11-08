package com.banking.account_service.application.query;

import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Average daily balance of one business month.
 *
 * @param period optional; the current business month when absent
 */
public record GetDailyAverageBalanceQuery(UUID accountId, YearMonth period) {

    public GetDailyAverageBalanceQuery {
        Objects.requireNonNull(accountId, "accountId");
    }
}
