package com.banking.account_service.application.port.in;

import com.banking.account_service.application.command.TransferCommand;
import com.banking.account_service.application.model.OperationResult;
import reactor.core.publisher.Mono;

/** Transfers between two accounts of the bank, idempotently by operationId. */
public interface TransferUseCase {

    Mono<OperationResult> execute(TransferCommand command);
}
