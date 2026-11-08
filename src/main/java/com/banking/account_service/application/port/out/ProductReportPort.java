package com.banking.account_service.application.port.out;

import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.CurrencyCode;
import com.banking.account_service.domain.model.MovementType;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Totals for the product report, read from one consistent view of the data. */
public interface ProductReportPort {

    /**
     * Account and movement totals by product and currency.
     *
     * @param start first instant of the range
     * @param end   first instant after the range
     * @param from  first business date of the range
     * @param to    last business date of the range, for closing balances
     */
    Mono<ReportTotals> totals(Instant start, Instant end, LocalDate from, LocalDate to);

    /** Only the product and currency combinations with data; the caller fills the rest with zeros. */
    record ReportTotals(List<AccountTotals> accounts, List<MovementTotals> movements) {

        public ReportTotals {
            accounts = List.copyOf(accounts);
            movements = List.copyOf(movements);
        }
    }

    /** Accounts of one product and currency. */
    record AccountTotals(
            AccountProduct product,
            CurrencyCode currency,
            long opened,
            long closed,
            long activeAtEnd,
            BigDecimal closingBalanceTotal
    ) {
    }

    /** Movements of one type for one product and currency. */
    record MovementTotals(
            AccountProduct product,
            CurrencyCode currency,
            MovementType type,
            long count,
            BigDecimal totalAmount,
            BigDecimal totalFees
    ) {
    }
}
