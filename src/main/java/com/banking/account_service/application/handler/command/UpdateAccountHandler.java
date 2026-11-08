package com.banking.account_service.application.handler.command;

import com.banking.account_service.application.command.UpdateAccountCommand;
import com.banking.account_service.application.exception.AccountConcurrentModificationException;
import com.banking.account_service.application.exception.AccountNotFoundException;
import com.banking.account_service.application.model.AccountView;
import com.banking.account_service.application.port.in.UpdateAccountUseCase;
import com.banking.account_service.application.port.out.AccountWritePort;
import com.banking.account_service.application.port.out.TransactionPort;
import com.banking.account_service.application.support.BusinessCalendar;
import com.banking.account_service.domain.exception.AccountClosedException;
import com.banking.account_service.domain.model.Account;
import com.banking.account_service.domain.model.AccountParticipants;
import reactor.core.publisher.Mono;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Replaces holders and authorized signers.
 *
 * <p>Order: 404 missing, 412 stale revision, 409 closed, 422 rules. A first unlocked read
 * finds the customers being added and checks them in Customer Service; the transaction
 * then locks the row, repeats the revision and state checks and writes.
 */
public class UpdateAccountHandler implements UpdateAccountUseCase {

    private final AccountWritePort accounts;
    private final TransactionPort transactions;
    private final CustomerEligibilityVerifier eligibility;
    private final BusinessCalendar calendar;

    public UpdateAccountHandler(
            AccountWritePort accounts,
            TransactionPort transactions,
            CustomerEligibilityVerifier eligibility,
            BusinessCalendar calendar
    ) {
        this.accounts = Objects.requireNonNull(accounts);
        this.transactions = Objects.requireNonNull(transactions);
        this.eligibility = Objects.requireNonNull(eligibility);
        this.calendar = Objects.requireNonNull(calendar);
    }

    @Override
    public Mono<AccountView> execute(UpdateAccountCommand command) {
        UUID accountId = command.accountId();
        return accounts.findById(accountId)
                .switchIfEmpty(Mono.error(() -> new AccountNotFoundException(accountId)))
                .flatMap(current -> {
                    requireUpdatable(current, command.expectedVersion());
                    AccountParticipants next = AccountParticipants.of(
                            current.primaryHolderId(), command.holderIds(), command.signerIds());
                    next.requireAllowedFor(current.getCustomerType());
                    Set<UUID> added = new LinkedHashSet<>(next.customerIds());
                    added.removeAll(current.getParticipants().customerIds());
                    return eligibility.requireAllActive(added).thenReturn(next);
                })
                .flatMap(next -> transactions.inTransaction(() -> accounts.lockById(accountId)
                        .switchIfEmpty(Mono.error(() -> new AccountNotFoundException(accountId)))
                        .flatMap(locked -> apply(locked, next, command.expectedVersion()))))
                .map(AccountView::from);
    }

    private Mono<Account> apply(Account locked, AccountParticipants next, long expectedVersion) {
        requireUpdatable(locked, expectedVersion);
        if (!locked.replaceParticipants(next, calendar.now())) {
            // Same participants: keep the revision and return the current state.
            return Mono.just(locked);
        }
        return accounts.update(locked, expectedVersion)
                .then(accounts.replaceParticipants(locked))
                .thenReturn(locked);
    }

    private static void requireUpdatable(Account account, long expectedVersion) {
        if (account.getVersion() != expectedVersion) {
            throw new AccountConcurrentModificationException(account.getId());
        }
        if (!account.isActive()) {
            throw new AccountClosedException(account.getId());
        }
    }
}
