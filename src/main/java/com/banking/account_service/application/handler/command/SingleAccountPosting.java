package com.banking.account_service.application.handler.command;

import com.banking.account_service.application.exception.AccountNotFoundException;
import com.banking.account_service.application.model.OperationOutcome;
import com.banking.account_service.application.model.OperationRequest;
import com.banking.account_service.application.model.RecordedOperation;
import com.banking.account_service.application.port.out.AccountPolicyPort;
import com.banking.account_service.application.port.out.AccountWritePort;
import com.banking.account_service.application.port.out.MovementWritePort;
import com.banking.account_service.application.support.BusinessCalendar;
import com.banking.account_service.domain.model.Account;
import com.banking.account_service.domain.model.Movement;
import com.banking.account_service.domain.model.PostingContext;
import com.banking.account_service.domain.model.ProductPolicy;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Deposit or withdrawal on one account, run inside the transaction of
 * {@link IdempotentOperationExecutor}: lock the row, apply the operation with the
 * product terms, store balance and counters conditioned on the locked revision and append
 * the movement.
 *
 * <p>The posting instant is taken after the lock, so the history of an account is in
 * time order even when requests wait for each other.
 */
public class SingleAccountPosting {

    private final AccountWritePort accounts;
    private final MovementWritePort movements;
    private final AccountPolicyPort policies;
    private final BusinessCalendar calendar;

    public SingleAccountPosting(
            AccountWritePort accounts,
            MovementWritePort movements,
            AccountPolicyPort policies,
            BusinessCalendar calendar
    ) {
        this.accounts = Objects.requireNonNull(accounts);
        this.movements = Objects.requireNonNull(movements);
        this.policies = Objects.requireNonNull(policies);
        this.calendar = Objects.requireNonNull(calendar);
    }

    public Mono<RecordedOperation> post(OperationRequest request, Posting posting) {
        return accounts.lockById(request.accountId())
                .switchIfEmpty(Mono.error(() -> new AccountNotFoundException(request.accountId())))
                .flatMap(account -> {
                    long lockedVersion = account.getVersion();
                    PostingContext context = calendar.posting(request.operationId(), request.description());
                    ProductPolicy policy = policies.policyFor(account.getProduct(), account.getCurrency());
                    Movement movement = posting.apply(account, request.amount(), policy, context);
                    RecordedOperation recorded = RecordedOperation.of(request, context,
                            OperationOutcome.of(account.getCurrency(), null, movement));
                    return accounts.update(account, lockedVersion)
                            .then(movements.append(movement))
                            .thenReturn(recorded);
                });
    }

    /** A monetary operation of the aggregate, such as {@code Account::deposit}. */
    @FunctionalInterface
    public interface Posting {

        Movement apply(Account account, BigDecimal amount, ProductPolicy policy, PostingContext context);
    }
}
