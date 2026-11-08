package com.banking.account_service.application.handler.query;

import com.banking.account_service.application.exception.AccountNotFoundException;
import com.banking.account_service.application.model.AccountView;
import com.banking.account_service.application.port.in.GetAccountUseCase;
import com.banking.account_service.application.port.out.AccountReadPort;
import com.banking.account_service.application.query.GetAccountQuery;
import reactor.core.publisher.Mono;

import java.util.Objects;

/** Reads one account, ACTIVE or CLOSED. */
public class GetAccountHandler implements GetAccountUseCase {

    private final AccountReadPort accounts;

    public GetAccountHandler(AccountReadPort accounts) {
        this.accounts = Objects.requireNonNull(accounts);
    }

    @Override
    public Mono<AccountView> execute(GetAccountQuery query) {
        return accounts.findById(query.accountId())
                .switchIfEmpty(Mono.error(() -> new AccountNotFoundException(query.accountId())));
    }
}
