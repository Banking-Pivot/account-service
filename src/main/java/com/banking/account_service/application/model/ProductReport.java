package com.banking.account_service.application.model;

import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.CurrencyCode;
import com.banking.account_service.domain.model.MovementType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Report by product for a range of business dates. It has one line for every product
 * and currency, with zeros where nothing happened.
 */
public record ProductReport(LocalDate from, LocalDate to, ZoneId businessZone, Instant generatedAt, List<Line> lines) {

    public ProductReport {
        lines = List.copyOf(lines);
    }

    /** Totals of one product in one currency. */
    public record Line(
            AccountProduct product,
            CurrencyCode currency,
            long accountsOpened,
            long accountsClosed,
            long activeAccountsAtEnd,
            BigDecimal closingBalanceTotal,
            BigDecimal totalFees,
            List<MovementTotals> movements
    ) {

        public Line {
            movements = List.copyOf(movements);
        }
    }

    /** Count, amount and fees of one movement type. */
    public record MovementTotals(MovementType type, long count, BigDecimal totalAmount, BigDecimal totalFees) {
    }
}
