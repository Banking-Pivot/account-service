package com.banking.account_service.application.port.out;

import com.banking.account_service.application.model.AccountView;
import com.banking.account_service.application.model.ResultPage;
import com.banking.account_service.application.query.ListAccountsQuery;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Read side of the account store. */
public interface AccountReadPort {

    Mono<AccountView> findById(UUID accountId);

    Mono<Boolean> existsById(UUID accountId);

    /** One page ordered by account ID, with the total of accounts matching the filters. */
    Mono<ResultPage<AccountView>> findPage(ListAccountsQuery query);
}
