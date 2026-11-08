package com.banking.account_service.application.port.in;

import com.banking.account_service.application.model.ResultPage;
import com.banking.account_service.application.query.ListAccountMovementsQuery;
import com.banking.account_service.domain.model.Movement;
import reactor.core.publisher.Mono;

/** Lists the movement history of an account. */
public interface ListAccountMovementsUseCase {

    Mono<ResultPage<Movement>> execute(ListAccountMovementsQuery query);
}
