package com.banking.account_service.application.query;

import com.banking.account_service.application.exception.InvalidQueryException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Report by product for a range of business dates, both inclusive. */
public record GenerateProductReportQuery(LocalDate from, LocalDate to) {

    /** Longest range accepted, in days. */
    public static final int MAX_DAYS = 366;

    public GenerateProductReportQuery {
        if (from == null || to == null) {
            throw new InvalidQueryException("from and to are required.");
        }
        if (from.isAfter(to)) {
            throw new InvalidQueryException("from must not be after to.");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw new InvalidQueryException("The range cannot exceed " + MAX_DAYS + " days.");
        }
    }
}
