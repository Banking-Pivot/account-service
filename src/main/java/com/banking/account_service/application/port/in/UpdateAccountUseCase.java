package com.banking.account_service.application.port.in;

import com.banking.account_service.application.command.UpdateAccountCommand;
import com.banking.account_service.application.model.AccountView;
import reactor.core.publisher.Mono;

/** Replaces the holders and authorized signers of an account. */
public interface UpdateAccountUseCase {

    Mono<AccountView> execute(UpdateAccountCommand command);
}
