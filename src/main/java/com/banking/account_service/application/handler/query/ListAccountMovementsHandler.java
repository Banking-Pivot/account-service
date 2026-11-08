package com.banking.account_service.application.handler.query;

import com.banking.account_service.application.exception.AccountNotFoundException;
import com.banking.account_service.application.model.ResultPage;
import com.banking.account_service.application.port.in.ListAccountMovementsUseCase;
import com.banking.account_service.application.port.out.AccountReadPort;
import com.banking.account_service.application.port.out.MovementReadPort;
import com.banking.account_service.application.query.ListAccountMovementsQuery;
import com.banking.account_service.domain.model.Movement;
import reactor.core.publisher.Mono;

import java.util.Objects;

/** History of an account, newest first. Closed accounts keep their history. */
public class ListAccountMovementsHandler implements ListAccountMovementsUseCase {

    private final AccountReadPort accounts;
    private final MovementReadPort movements;

    public ListAccountMovementsHandler(AccountReadPort accounts, MovementReadPort movements) {
        this.accounts = Objects.requireNonNull(accounts);
        this.movements = Objects.requireNonNull(movements);
    }

    @Override
    public Mono<ResultPage<Movement>> execute(ListAccountMovementsQuery query) {
        return accounts.existsById(query.accountId())
                .flatMap(exists -> exists
                        ? movements.findPage(query)
                        : Mono.error(new AccountNotFoundException(query.accountId())));
    }
}
