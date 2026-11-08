package com.banking.account_service.application.port.in;

import com.banking.account_service.application.model.DailyAverageBalanceView;
import com.banking.account_service.application.query.GetDailyAverageBalanceQuery;
import reactor.core.publisher.Mono;

/** Computes the average daily balance of a business month. */
public interface GetDailyAverageBalanceUseCase {

    Mono<DailyAverageBalanceView> execute(GetDailyAverageBalanceQuery query);
}
