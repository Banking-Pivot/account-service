package com.banking.account_service.application.port.in;

import com.banking.account_service.application.command.CloseAccountCommand;
import reactor.core.publisher.Mono;

/** Closes an account with a zero balance, keeping its history. */
public interface CloseAccountUseCase {

    Mono<Void> execute(CloseAccountCommand command);
}
