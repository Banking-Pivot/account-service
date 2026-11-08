package com.banking.account_service.application.port.in;

import com.banking.account_service.application.command.DepositCommand;
import com.banking.account_service.application.model.OperationResult;
import reactor.core.publisher.Mono;

/** Deposits into an account, idempotently by operationId. */
public interface DepositUseCase {

    Mono<OperationResult> execute(DepositCommand command);
}
