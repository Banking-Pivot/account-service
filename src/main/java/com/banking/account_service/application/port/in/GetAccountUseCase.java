package com.banking.account_service.application.port.in;

import com.banking.account_service.application.model.AccountView;
import com.banking.account_service.application.query.GetAccountQuery;
import reactor.core.publisher.Mono;

/** Reads one account. */
public interface GetAccountUseCase {

    Mono<AccountView> execute(GetAccountQuery query);
}
