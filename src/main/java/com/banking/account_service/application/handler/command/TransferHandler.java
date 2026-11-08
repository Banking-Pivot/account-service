package com.banking.account_service.application.handler.command;

import com.banking.account_service.application.command.TransferCommand;
import com.banking.account_service.application.exception.AccountNotFoundException;
import com.banking.account_service.application.model.OperationOutcome;
import com.banking.account_service.application.model.OperationRequest;
import com.banking.account_service.application.model.OperationResult;
import com.banking.account_service.application.model.RecordedOperation;
import com.banking.account_service.application.port.in.TransferUseCase;
import com.banking.account_service.application.port.out.AccountPolicyPort;
import com.banking.account_service.application.port.out.AccountWritePort;
import com.banking.account_service.application.port.out.MovementWritePort;
import com.banking.account_service.application.support.BusinessCalendar;
import com.banking.account_service.domain.exception.AccountClosedException;
import com.banking.account_service.domain.exception.AccountRuleViolationException;
import com.banking.account_service.domain.exception.RuleViolation;
import com.banking.account_service.domain.model.Account;
import com.banking.account_service.domain.model.Movement;
import com.banking.account_service.domain.model.OperationType;
import com.banking.account_service.domain.model.PostingContext;
import com.banking.account_service.domain.model.TransferKind;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Transfer between two accounts of the bank, idempotent by operationId.
 *
 * <p>Inside the transaction of {@link IdempotentOperationExecutor}:
 * <ol>
 *   <li>Locks both rows one after the other in ascending ID order. Two transfers in
 *       opposite directions lock in the same order, so they queue instead of deadlocking.</li>
 *   <li>Under the locks checks: source exists (404) and is active (409), destination
 *       exists and is active, same currency (422).</li>
 *   <li>Debits the source under its product terms and credits the destination under its
 *       own: each side checks its limit, fixed-term day and fee.</li>
 *   <li>Writes the source, its movement, the destination and its movement. A failure at
 *       any point rolls back everything, including the claim of the key.</li>
 * </ol>
 */
public class TransferHandler implements TransferUseCase {

    private static final Comparator<UUID> LOCK_ORDER = Comparator.comparing(UUID::toString);

    private final IdempotentOperationExecutor executor;
    private final AccountWritePort accounts;
    private final MovementWritePort movements;
    private final AccountPolicyPort policies;
    private final BusinessCalendar calendar;

    public TransferHandler(
            IdempotentOperationExecutor executor,
            AccountWritePort accounts,
            MovementWritePort movements,
            AccountPolicyPort policies,
            BusinessCalendar calendar
    ) {
        this.executor = Objects.requireNonNull(executor);
        this.accounts = Objects.requireNonNull(accounts);
        this.movements = Objects.requireNonNull(movements);
        this.policies = Objects.requireNonNull(policies);
        this.calendar = Objects.requireNonNull(calendar);
    }

    @Override
    public Mono<OperationResult> execute(TransferCommand command) {
        return Mono.fromCallable(() -> toRequest(command))
                .flatMap(request -> executor.execute(request, () -> lockAndPost(request)));
    }

    private static OperationRequest toRequest(TransferCommand command) {
        if (command.sourceAccountId().equals(command.destinationAccountId())) {
            throw new AccountRuleViolationException(RuleViolation.SAME_ACCOUNT_TRANSFER,
                    "The destination account must be different from the source account.");
        }
        return new OperationRequest(command.operationId(), OperationType.TRANSFER, command.sourceAccountId(),
                command.destinationAccountId(), command.amount(), command.description());
    }

    private Mono<RecordedOperation> lockAndPost(OperationRequest request) {
        UUID sourceId = request.accountId();
        UUID destinationId = request.counterpartyAccountId();
        return Flux.fromIterable(lockOrder(sourceId, destinationId))
                .concatMap(accounts::lockById)
                .collectMap(Account::getId)
                .flatMap(locked -> post(request, locked.get(sourceId), locked.get(destinationId)));
    }

    /** Ascending order of the canonical text form, the same order PostgreSQL uses for UUIDs. */
    static List<UUID> lockOrder(UUID first, UUID second) {
        return Stream.of(first, second).sorted(LOCK_ORDER).toList();
    }

    private Mono<RecordedOperation> post(OperationRequest request, Account source, Account destination) {
        requireTransferable(request, source, destination);
        long sourceVersion = source.getVersion();
        long destinationVersion = destination.getVersion();
        PostingContext context = calendar.posting(request.operationId(), request.description());

        Movement debit = source.transferOut(request.amount(), destination.getId(),
                policies.policyFor(source.getProduct(), source.getCurrency()), context);
        Movement credit = credit(destination, request, source.getId(), context);

        TransferKind kind = source.primaryHolderId().equals(destination.primaryHolderId())
                ? TransferKind.OWN_ACCOUNTS
                : TransferKind.THIRD_PARTY;
        RecordedOperation recorded = RecordedOperation.of(request, context,
                OperationOutcome.of(source.getCurrency(), kind, debit));

        return accounts.update(source, sourceVersion)
                .then(movements.append(debit))
                .then(accounts.update(destination, destinationVersion))
                .then(movements.append(credit))
                .thenReturn(recorded);
    }

    private static void requireTransferable(OperationRequest request, Account source, Account destination) {
        if (source == null) {
            throw new AccountNotFoundException(request.accountId());
        }
        if (!source.isActive()) {
            throw new AccountClosedException(source.getId());
        }
        if (destination == null) {
            throw new AccountRuleViolationException(RuleViolation.DESTINATION_ACCOUNT_NOT_FOUND,
                    "Destination account " + request.counterpartyAccountId() + " was not found.");
        }
        if (!destination.isActive()) {
            throw new AccountRuleViolationException(RuleViolation.DESTINATION_ACCOUNT_CLOSED,
                    "Destination account " + destination.getId() + " is closed.");
        }
        if (source.getCurrency() != destination.getCurrency()) {
            throw new AccountRuleViolationException(RuleViolation.CURRENCY_MISMATCH,
                    "Both accounts must use the same currency.");
        }
    }

    /** Credits the destination; a rule it breaks is reported as the destination's. */
    private Movement credit(Account destination, OperationRequest request, UUID sourceId, PostingContext context) {
        try {
            return destination.transferIn(request.amount(), sourceId,
                    policies.policyFor(destination.getProduct(), destination.getCurrency()), context);
        } catch (AccountRuleViolationException rejected) {
            throw new AccountRuleViolationException(rejected.violation(),
                    "Destination account: " + rejected.getMessage());
        }
    }
}
