package com.banking.account_service.application.handler.command;

import com.banking.account_service.application.command.DepositCommand;
import com.banking.account_service.application.model.OperationRequest;
import com.banking.account_service.application.model.OperationResult;
import com.banking.account_service.application.port.in.DepositUseCase;
import com.banking.account_service.domain.model.Account;
import com.banking.account_service.domain.model.OperationType;
import reactor.core.publisher.Mono;

import java.util.Objects;

/** Deposit, idempotent by operationId. */
public class DepositHandler implements DepositUseCase {

    private final IdempotentOperationExecutor executor;
    private final SingleAccountPosting posting;

    public DepositHandler(IdempotentOperationExecutor executor, SingleAccountPosting posting) {
        this.executor = Objects.requireNonNull(executor);
        this.posting = Objects.requireNonNull(posting);
    }

    @Override
    public Mono<OperationResult> execute(DepositCommand command) {
        return Mono.fromCallable(() -> new OperationRequest(command.operationId(), OperationType.DEPOSIT,
                        command.accountId(), null, command.amount(), command.description()))
                .flatMap(request -> executor.execute(request, () -> posting.post(request, Account::deposit)));
    }
}
