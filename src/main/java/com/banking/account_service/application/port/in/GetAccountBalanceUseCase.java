package com.banking.account_service.application.port.in;

import com.banking.account_service.application.model.AccountBalanceView;
import com.banking.account_service.application.query.GetAccountBalanceQuery;
import reactor.core.publisher.Mono;

/** Reads the balance and the monthly usage of an account. */
public interface GetAccountBalanceUseCase {

    Mono<AccountBalanceView> execute(GetAccountBalanceQuery query);
}
