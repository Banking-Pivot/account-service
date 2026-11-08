package com.banking.account_service.application.handler.command;

import com.banking.account_service.application.command.CloseAccountCommand;
import com.banking.account_service.application.exception.AccountConcurrentModificationException;
import com.banking.account_service.application.exception.AccountNotFoundException;
import com.banking.account_service.application.port.in.CloseAccountUseCase;
import com.banking.account_service.application.port.out.AccountWritePort;
import com.banking.account_service.application.port.out.TransactionPort;
import com.banking.account_service.application.support.BusinessCalendar;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

/**
 * Closes an account logically: status CLOSED, history kept.
 *
 * <p>The row is locked, so no deposit can arrive between the zero-balance check and the
 * write. Order: 404 missing, 412 stale revision, 422 balance not zero. Closing an account
 * that is already closed, with its current revision, changes nothing.
 */
public class CloseAccountHandler implements CloseAccountUseCase {

    private final AccountWritePort accounts;
    private final TransactionPort transactions;
    private final BusinessCalendar calendar;

    public CloseAccountHandler(AccountWritePort accounts, TransactionPort transactions, BusinessCalendar calendar) {
        this.accounts = Objects.requireNonNull(accounts);
        this.transactions = Objects.requireNonNull(transactions);
        this.calendar = Objects.requireNonNull(calendar);
    }

    @Override
    public Mono<Void> execute(CloseAccountCommand command) {
        UUID accountId = command.accountId();
        return transactions.inTransaction(() -> accounts.lockById(accountId)
                .switchIfEmpty(Mono.error(() -> new AccountNotFoundException(accountId)))
                .flatMap(locked -> {
                    if (locked.getVersion() != command.expectedVersion()) {
                        return Mono.error(new AccountConcurrentModificationException(accountId));
                    }
                    return locked.close(calendar.now())
                            ? accounts.update(locked, command.expectedVersion())
                            : Mono.<Void>empty();
                }));
    }
}
