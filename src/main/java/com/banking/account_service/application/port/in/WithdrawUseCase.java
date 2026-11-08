package com.banking.account_service.application.port.in;

import com.banking.account_service.application.command.WithdrawCommand;
import com.banking.account_service.application.model.OperationResult;
import reactor.core.publisher.Mono;

/** Withdraws from an account, idempotently by operationId. */
public interface WithdrawUseCase {

    Mono<OperationResult> execute(WithdrawCommand command);
}
