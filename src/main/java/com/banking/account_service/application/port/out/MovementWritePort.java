package com.banking.account_service.application.port.out;

import com.banking.account_service.domain.model.Movement;
import reactor.core.publisher.Mono;

/** Appends entries to the account history. History is never updated or deleted. */
public interface MovementWritePort {

    Mono<Void> append(Movement movement);
}
