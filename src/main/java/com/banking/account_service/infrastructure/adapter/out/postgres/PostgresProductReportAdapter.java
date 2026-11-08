package com.banking.account_service.infrastructure.adapter.out.postgres;

import com.banking.account_service.application.port.out.ProductReportPort;
import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.Amounts;
import com.banking.account_service.domain.model.CurrencyCode;
import com.banking.account_service.domain.model.MovementType;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.reactive.TransactionalOperator;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

/**
 * Product report with Spring Data R2DBC criteria only: no SQL written by hand and no
 * {@code @Query}, as the project rules require.
 *
 * <p>Three selects run in one read-only REPEATABLE READ transaction, so balances and
 * movements come from the same snapshot while operations keep committing: the accounts
 * opened before the end of the range, the movements in the range and the movements after
 * it. The closing balance of an account at the end of the range is its current balance
 * minus what the later movements changed. Movements are aggregated as they stream from
 * the database; only the totals and the accounts stay in memory.
 */
@Component
public class PostgresProductReportAdapter implements ProductReportPort {

    private final R2dbcEntityTemplate template;
    private final TransactionalOperator snapshot;

    public PostgresProductReportAdapter(R2dbcEntityTemplate template, ReactiveTransactionManager transactionManager) {
        DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
        definition.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        definition.setReadOnly(true);
        this.template = template;
        this.snapshot = TransactionalOperator.create(transactionManager, definition);
    }

    @Override
    public Mono<ReportTotals> totals(Instant start, Instant end, LocalDate from, LocalDate to) {
        return snapshot.transactional(Mono.defer(() -> accountsOpenedBefore(end)
                .flatMap(accounts -> balanceChangesAfter(to)
                        .flatMap(laterChanges -> movementTotals(accounts, from, to)
                                .map(movements -> new ReportTotals(
                                        accountTotals(accounts.values(), start, end, laterChanges), movements))))));
    }

    private Mono<Map<UUID, AccountRow>> accountsOpenedBefore(Instant end) {
        return template.select(AccountRow.class)
                .matching(query(where("openedAt").lessThan(end)))
                .all()
                .collectMap(AccountRow::id);
    }

    /** Net balance change of each account caused by movements after the range. */
    private Mono<Map<UUID, BigDecimal>> balanceChangesAfter(LocalDate to) {
        return template.select(MovementRow.class)
                .matching(query(where("businessDate").greaterThan(to)))
                .all()
                .collect(Collectors.groupingBy(MovementRow::accountId,
                        Collectors.reducing(Amounts.ZERO, PostgresProductReportAdapter::balanceChange, BigDecimal::add)));
    }

    /**
     * Movement totals in the range. Every movement belongs to an account opened before the
     * end of the range, because no movement is dated before the opening of its account.
     */
    private Mono<List<MovementTotals>> movementTotals(Map<UUID, AccountRow> accounts, LocalDate from, LocalDate to) {
        return template.select(MovementRow.class)
                .matching(query(where("businessDate").between(from, to)))
                .all()
                .collect(Collectors.groupingBy(
                        movement -> MovementKey.of(Objects.requireNonNull(accounts.get(movement.accountId()),
                                "movement dated before the opening of its account"), movement.movementType()),
                        Collectors.reducing(MovementSums.NONE, MovementSums::of, MovementSums::plus)))
                .map(totals -> totals.entrySet().stream()
                        .map(entry -> new MovementTotals(entry.getKey().product(), entry.getKey().currency(),
                                entry.getKey().type(), entry.getValue().count(), entry.getValue().amount(),
                                entry.getValue().fees()))
                        .toList());
    }

    private static List<AccountTotals> accountTotals(
            Collection<AccountRow> accounts,
            Instant start,
            Instant end,
            Map<UUID, BigDecimal> laterChanges
    ) {
        return accounts.stream()
                .collect(Collectors.groupingBy(account -> new ProductKey(account.product(), account.currency()),
                        Collectors.reducing(AccountSums.NONE,
                                account -> AccountSums.of(account, start, end, laterChanges), AccountSums::plus)))
                .entrySet().stream()
                .map(entry -> new AccountTotals(entry.getKey().product(), entry.getKey().currency(),
                        entry.getValue().opened(), entry.getValue().closed(), entry.getValue().activeAtEnd(),
                        entry.getValue().closingBalance()))
                .toList();
    }

    private static BigDecimal balanceChange(MovementRow movement) {
        return movement.movementType().isCredit()
                ? movement.amount().subtract(movement.fee())
                : movement.amount().add(movement.fee()).negate();
    }

    private record ProductKey(AccountProduct product, CurrencyCode currency) {
    }

    private record MovementKey(AccountProduct product, CurrencyCode currency, MovementType type) {

        static MovementKey of(AccountRow account, MovementType type) {
            return new MovementKey(account.product(), account.currency(), type);
        }
    }

    private record MovementSums(long count, BigDecimal amount, BigDecimal fees) {

        static final MovementSums NONE = new MovementSums(0, Amounts.ZERO, Amounts.ZERO);

        static MovementSums of(MovementRow movement) {
            return new MovementSums(1, movement.amount(), movement.fee());
        }

        MovementSums plus(MovementSums other) {
            return new MovementSums(count + other.count, amount.add(other.amount), fees.add(other.fees));
        }
    }

    private record AccountSums(long opened, long closed, long activeAtEnd, BigDecimal closingBalance) {

        static final AccountSums NONE = new AccountSums(0, 0, 0, Amounts.ZERO);

        static AccountSums of(AccountRow account, Instant start, Instant end, Map<UUID, BigDecimal> laterChanges) {
            Instant closedAt = account.closedAt();
            boolean closedInRange = closedAt != null && !closedAt.isBefore(start) && closedAt.isBefore(end);
            boolean activeAtEnd = closedAt == null || !closedAt.isBefore(end);
            return new AccountSums(
                    account.openedAt().isBefore(start) ? 0 : 1,
                    closedInRange ? 1 : 0,
                    activeAtEnd ? 1 : 0,
                    account.balance().subtract(laterChanges.getOrDefault(account.id(), Amounts.ZERO)));
        }

        AccountSums plus(AccountSums other) {
            return new AccountSums(opened + other.opened, closed + other.closed, activeAtEnd + other.activeAtEnd,
                    closingBalance.add(other.closingBalance));
        }
    }
}
