package com.banking.account_service.application.port.out;

import com.banking.account_service.application.model.ResultPage;
import com.banking.account_service.application.query.ListAccountMovementsQuery;
import com.banking.account_service.domain.model.Movement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Read side of the account history. */
public interface MovementReadPort {

    /** One page of history, newest first, with the total matching the filters. */
    Mono<ResultPage<Movement>> findPage(ListAccountMovementsQuery query);

    /** Balance after the last movement dated before {@code date}; empty when there is none. */
    Mono<BigDecimal> closingBalanceBefore(UUID accountId, LocalDate date);

    /** Movements dated in {@code [from, to]}, in sequence order. */
    Flux<Movement> findBetween(UUID accountId, LocalDate from, LocalDate to);
}
