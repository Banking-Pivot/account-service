package com.banking.account_service.infrastructure.adapter.out.postgres;

import com.banking.account_service.application.exception.AccountConcurrentModificationException;
import com.banking.account_service.application.exception.DuplicatePersonalAccountException;
import com.banking.account_service.application.port.out.AccountWritePort;
import com.banking.account_service.domain.model.Account;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Update;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

/** Write side of accounts on PostgreSQL. */
@Component
public class PostgresAccountWriteAdapter implements AccountWritePort {

    static final String PERSONAL_UNIQUENESS_INDEX = "uk_accounts_personal_active_type";

    private final R2dbcEntityTemplate template;
    private final AccountRowRepository repository;
    private final AccountParticipantsLoader participants;

    public PostgresAccountWriteAdapter(
            R2dbcEntityTemplate template,
            AccountRowRepository repository,
            AccountParticipantsLoader participants
    ) {
        this.template = template;
        this.repository = repository;
        this.participants = participants;
    }

    @Override
    public Mono<Void> insert(Account account) {
        return template.insert(AccountParticipantsLoader.toRow(account))
                .then(participants.insert(account))
                .onErrorMap(error -> PostgresErrors.isUniqueViolation(error, PERSONAL_UNIQUENESS_INDEX),
                        DuplicatePersonalAccountException::new);
    }

    @Override
    public Mono<Account> findById(UUID accountId) {
        return repository.findById(accountId).flatMap(participants::restore);
    }

    @Override
    public Mono<Account> lockById(UUID accountId) {
        return repository.findForUpdateById(accountId).flatMap(participants::restore);
    }

    @Override
    public Mono<Void> update(Account account, long expectedVersion) {
        Update update = Update.update("balance", account.getBalance())
                .set("status", account.getStatus().name())
                .set("usageMonth", account.getUsage().period().atDay(1))
                .set("usageMovements", account.getUsage().movements())
                .set("movementSequence", account.getMovementSequence())
                .set("updatedAt", account.getUpdatedAt())
                .set("version", account.getVersion());
        if (account.getClosedAt() != null) {
            // An account never reopens, so closedAt only goes from empty to set.
            update = update.set("closedAt", account.getClosedAt());
        }
        return template.update(AccountRow.class)
                .matching(query(where("id").is(account.getId()).and("version").is(expectedVersion)))
                .apply(update)
                .flatMap(rows -> rows == 1
                        ? Mono.<Void>empty()
                        : Mono.error(new AccountConcurrentModificationException(account.getId())));
    }

    @Override
    public Mono<Void> replaceParticipants(Account account) {
        return participants.delete(account.getId()).then(participants.insert(account));
    }
}
