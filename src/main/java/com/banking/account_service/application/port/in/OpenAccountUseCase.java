package com.banking.account_service.application.port.in;

import com.banking.account_service.application.command.OpenAccountCommand;
import com.banking.account_service.application.model.AccountView;
import reactor.core.publisher.Mono;

/** Opens an account for an eligible customer. */
public interface OpenAccountUseCase {

    Mono<AccountView> execute(OpenAccountCommand command);
}
