package com.banking.account_service.infrastructure.adapter.out.postgres;

import com.banking.account_service.domain.model.Account;
import com.banking.account_service.domain.model.AccountParticipants;
import com.banking.account_service.domain.model.MonthlyUsage;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

/**
 * Loads holders and signers for a set of accounts with one query per table and rebuilds
 * the aggregates. Shared by the write and the read adapters.
 */
@Component
public class AccountParticipantsLoader {

    private final R2dbcEntityTemplate template;

    public AccountParticipantsLoader(R2dbcEntityTemplate template) {
        this.template = template;
    }

    /** Aggregates for the rows, in the order of the rows. */
    public Mono<List<Account>> restore(List<AccountRow> rows) {
        if (rows.isEmpty()) {
            return Mono.just(List.of());
        }
        List<UUID> ids = rows.stream().map(AccountRow::id).toList();
        return Mono.zip(customersByAccount(ids, AccountHolderRow.class), customersByAccount(ids, AccountSignerRow.class))
                .map(participants -> rows.stream()
                        .map(row -> toDomain(row,
                                participants.getT1().getOrDefault(row.id(), List.of()),
                                participants.getT2().getOrDefault(row.id(), List.of())))
                        .toList());
    }

    /** Aggregate for one row. */
    public Mono<Account> restore(AccountRow row) {
        return restore(List.of(row)).map(accounts -> accounts.get(0));
    }

    /** Writes the participants of a new account, or of an account whose old ones were deleted. */
    public Mono<Void> insert(Account account) {
        AccountParticipants participants = account.getParticipants();
        Flux<Object> holders = Flux.fromIterable(participants.holderIds())
                .concatMap(customerId -> template.insert(new AccountHolderRow(account.getId(), customerId,
                        customerId.equals(participants.primaryHolderId()))));
        Flux<Object> signers = Flux.fromIterable(participants.signerIds())
                .concatMap(customerId -> template.insert(new AccountSignerRow(account.getId(), customerId)));
        return holders.thenMany(signers).then();
    }

    /** Deletes the participants of an account, inside the transaction that rewrites them. */
    public Mono<Void> delete(UUID accountId) {
        return template.delete(AccountHolderRow.class).matching(query(where("accountId").is(accountId))).all()
                .then(template.delete(AccountSignerRow.class).matching(query(where("accountId").is(accountId))).all())
                .then();
    }

    private <R> Mono<Map<UUID, List<UUID>>> customersByAccount(Collection<UUID> accountIds, Class<R> rowType) {
        return template.select(rowType)
                .matching(query(where("accountId").in(accountIds)))
                .all()
                .collect(Collectors.groupingBy(AccountParticipantsLoader::accountIdOf,
                        Collectors.mapping(AccountParticipantsLoader::customerIdOf, Collectors.toList())));
    }

    private static UUID accountIdOf(Object row) {
        return row instanceof AccountHolderRow holder ? holder.accountId() : ((AccountSignerRow) row).accountId();
    }

    private static UUID customerIdOf(Object row) {
        return row instanceof AccountHolderRow holder ? holder.customerId() : ((AccountSignerRow) row).customerId();
    }

    private static Account toDomain(AccountRow row, List<UUID> holderIds, List<UUID> signerIds) {
        AccountParticipants participants = new AccountParticipants(row.primaryHolderId(), holderIds, signerIds);
        return Account.restore(row.id(), row.product(), row.currency(), row.customerType(), row.customerProfile(),
                participants, row.balance(), row.status(),
                new MonthlyUsage(YearMonth.from(row.usageMonth()), row.usageMovements()), row.movementSequence(),
                row.openedAt(), row.updatedAt(), row.closedAt(), row.version());
    }

    /** Row for a new account. */
    static AccountRow toRow(Account account) {
        return new AccountRow(account.getId(), account.primaryHolderId(), account.getCustomerType(),
                account.getCustomerProfile(), account.getProduct().type(), account.getProduct(),
                account.getCurrency(), account.getBalance(), account.getStatus(),
                account.getUsage().period().atDay(1), account.getUsage().movements(), account.getMovementSequence(),
                account.getOpenedAt(), account.getUpdatedAt(), account.getClosedAt(), account.getVersion());
    }
}
