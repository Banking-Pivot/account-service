package com.banking.account_service.application.port.in;

import com.banking.account_service.application.model.ProductReport;
import com.banking.account_service.application.query.GenerateProductReportQuery;
import reactor.core.publisher.Mono;

/** Builds the report by product for a date range. */
public interface GenerateProductReportUseCase {

    Mono<ProductReport> execute(GenerateProductReportQuery query);
}
