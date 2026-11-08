package com.banking.account_service.application.handler.command;

import com.banking.account_service.application.exception.OperationAlreadyRecordedException;
import com.banking.account_service.application.exception.OperationIdConflictException;
import com.banking.account_service.application.model.OperationRequest;
import com.banking.account_service.application.model.OperationResult;
import com.banking.account_service.application.model.RecordedOperation;
import com.banking.account_service.application.port.out.OperationLogPort;
import com.banking.account_service.application.port.out.TransactionPort;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Runs a monetary operation at most once per operationId.
 *
 * <ol>
 *   <li>A recorded key is answered from the record: same data is a replay, other data a conflict.</li>
 *   <li>Otherwise one transaction claims the key (INSERT into the unique primary key),
 *       runs the posting and stores the result. Any error rolls back the claim too, so a
 *       failed operation leaves the key free for a retry.</li>
 *   <li>When a concurrent request with the same key commits first, the claim fails after
 *       waiting on the index; this transaction rolls back and the request is answered
 *       from the record the winner left.</li>
 * </ol>
 */
public class IdempotentOperationExecutor {

    private final OperationLogPort operations;
    private final TransactionPort transactions;

    public IdempotentOperationExecutor(OperationLogPort operations, TransactionPort transactions) {
        this.operations = Objects.requireNonNull(operations);
        this.transactions = Objects.requireNonNull(transactions);
    }

    /**
     * @param posting work done after the claim, inside the same transaction: lock the
     *                accounts, apply the domain operation, write balances and movements
     *                and return the record of the operation
     */
    public Mono<OperationResult> execute(OperationRequest request, Supplier<Mono<RecordedOperation>> posting) {
        return operations.findById(request.operationId())
                .map(recorded -> replay(request, recorded))
                .switchIfEmpty(Mono.defer(() -> runOnce(request, posting)))
                .onErrorResume(OperationAlreadyRecordedException.class, lostRace ->
                        operations.findById(request.operationId())
                                .switchIfEmpty(Mono.error(lostRace))
                                .map(recorded -> replay(request, recorded)));
    }

    private Mono<OperationResult> runOnce(OperationRequest request, Supplier<Mono<RecordedOperation>> posting) {
        return transactions.inTransaction(() -> operations.claim(request)
                .then(Mono.defer(posting))
                .flatMap(recorded -> operations.complete(recorded)
                        .thenReturn(new OperationResult(recorded, false))));
    }

    private static OperationResult replay(OperationRequest request, RecordedOperation recorded) {
        if (!request.matches(recorded)) {
            throw new OperationIdConflictException(request.operationId());
        }
        return new OperationResult(recorded, true);
    }
}
