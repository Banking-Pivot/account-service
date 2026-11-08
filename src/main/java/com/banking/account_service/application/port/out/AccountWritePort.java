package com.banking.account_service.application.port.out;

import com.banking.account_service.domain.model.Account;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Write side of the account store. Methods that change data run inside a {@link TransactionPort}. */
public interface AccountWritePort {

    /**
     * Inserts a new account with its holders and signers. Errors with
     * {@code DuplicatePersonalAccountException} when the personal uniqueness index rejects it.
     */
    Mono<Void> insert(Account account);

    /** Reads an account without locking it, for checks done before the transaction. */
    Mono<Account> findById(UUID accountId);

    /**
     * Reads and locks the account row ({@code SELECT ... FOR UPDATE}) until the current
     * transaction ends. Completes empty when the account does not exist.
     */
    Mono<Account> lockById(UUID accountId);

    /**
     * Stores balance, status, counters and version. Errors with
     * {@code AccountConcurrentModificationException} when the stored version is not
     * {@code expectedVersion}.
     */
    Mono<Void> update(Account account, long expectedVersion);

    /** Replaces the stored holders and signers with those of the account. */
    Mono<Void> replaceParticipants(Account account);
}
