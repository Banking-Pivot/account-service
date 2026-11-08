package com.banking.account_service.application.handler.query;

import com.banking.account_service.application.model.ProductReport;
import com.banking.account_service.application.port.in.GenerateProductReportUseCase;
import com.banking.account_service.application.port.out.ProductReportPort;
import com.banking.account_service.application.query.GenerateProductReportQuery;
import com.banking.account_service.application.support.BusinessCalendar;
import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.Amounts;
import com.banking.account_service.domain.model.CurrencyCode;
import com.banking.account_service.domain.model.MovementType;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Report by product for a range of business dates.
 *
 * <p>The port returns the totals that have data; this handler fills the full product by
 * currency grid so every line and every movement type appears, with zeros where nothing
 * happened.
 * Openings and closings count by the business date of their instant; the closing balance
 * of an account is the balance after its last movement up to the end of the range.
 */
public class GenerateProductReportHandler implements GenerateProductReportUseCase {

    private final ProductReportPort reports;
    private final BusinessCalendar calendar;

    public GenerateProductReportHandler(ProductReportPort reports, BusinessCalendar calendar) {
        this.reports = Objects.requireNonNull(reports);
        this.calendar = Objects.requireNonNull(calendar);
    }

    @Override
    public Mono<ProductReport> execute(GenerateProductReportQuery query) {
        return reports.totals(calendar.startOf(query.from()), calendar.startOf(query.to().plusDays(1)),
                        query.from(), query.to())
                .map(totals -> new ProductReport(query.from(), query.to(), calendar.zone(), calendar.now(),
                        lines(totals.accounts(), totals.movements())));
    }

    private static List<ProductReport.Line> lines(
            List<ProductReportPort.AccountTotals> accountTotals,
            List<ProductReportPort.MovementTotals> movementTotals
    ) {
        Map<Key, ProductReportPort.AccountTotals> accountsByKey = accountTotals.stream()
                .collect(Collectors.toMap(t -> new Key(t.product(), t.currency()), Function.identity()));
        Map<Key, Map<MovementType, ProductReportPort.MovementTotals>> movementsByKey = movementTotals.stream()
                .collect(Collectors.groupingBy(t -> new Key(t.product(), t.currency()),
                        Collectors.toMap(ProductReportPort.MovementTotals::type, Function.identity())));

        List<ProductReport.Line> lines = new ArrayList<>();
        for (AccountProduct product : AccountProduct.values()) {
            for (CurrencyCode currency : CurrencyCode.values()) {
                Key key = new Key(product, currency);
                lines.add(line(key, accountsByKey.get(key), movementsByKey.getOrDefault(key, Map.of())));
            }
        }
        return lines;
    }

    private static ProductReport.Line line(
            Key key,
            ProductReportPort.AccountTotals accounts,
            Map<MovementType, ProductReportPort.MovementTotals> movementsByType
    ) {
        List<ProductReport.MovementTotals> movements = Arrays.stream(MovementType.values())
                .map(type -> {
                    ProductReportPort.MovementTotals totals = movementsByType.get(type);
                    return totals == null
                            ? new ProductReport.MovementTotals(type, 0, Amounts.ZERO, Amounts.ZERO)
                            : new ProductReport.MovementTotals(type, totals.count(), totals.totalAmount(),
                            totals.totalFees());
                })
                .toList();
        BigDecimal totalFees = movements.stream()
                .map(ProductReport.MovementTotals::totalFees)
                .reduce(Amounts.ZERO, BigDecimal::add);
        return accounts == null
                ? new ProductReport.Line(key.product(), key.currency(), 0, 0, 0, Amounts.ZERO, totalFees, movements)
                : new ProductReport.Line(key.product(), key.currency(), accounts.opened(), accounts.closed(),
                accounts.activeAtEnd(), accounts.closingBalanceTotal(), totalFees, movements);
    }

    private record Key(AccountProduct product, CurrencyCode currency) {
    }
}
