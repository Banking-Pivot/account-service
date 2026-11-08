package com.banking.account_service.application.port.in;

import com.banking.account_service.application.model.AccountView;
import com.banking.account_service.application.model.ResultPage;
import com.banking.account_service.application.query.ListAccountsQuery;
import reactor.core.publisher.Mono;

/** Lists accounts page by page. */
public interface ListAccountsUseCase {

    Mono<ResultPage<AccountView>> execute(ListAccountsQuery query);
}
