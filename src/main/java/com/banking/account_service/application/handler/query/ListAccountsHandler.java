package com.banking.account_service.application.handler.query;

import com.banking.account_service.application.model.AccountView;
import com.banking.account_service.application.model.ResultPage;
import com.banking.account_service.application.port.in.ListAccountsUseCase;
import com.banking.account_service.application.port.out.AccountReadPort;
import com.banking.account_service.application.query.ListAccountsQuery;
import reactor.core.publisher.Mono;

import java.util.Objects;

/** Lists accounts page by page, optionally filtered by holder, product and status. */
public class ListAccountsHandler implements ListAccountsUseCase {

    private final AccountReadPort accounts;

    public ListAccountsHandler(AccountReadPort accounts) {
        this.accounts = Objects.requireNonNull(accounts);
    }

    @Override
    public Mono<ResultPage<AccountView>> execute(ListAccountsQuery query) {
        return accounts.findPage(query);
    }
}
