package com.banking.account_service.infrastructure.adapter.in.rest;

import com.banking.account_service.application.port.in.GenerateProductReportUseCase;
import com.banking.account_service.application.query.GenerateProductReportQuery;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.api.ReportsApiDelegate;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.ProductReportResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

/** Report by product for a range of business dates. */
@Component
public class ReportsApiDelegateImpl implements ReportsApiDelegate {

    private final GenerateProductReportUseCase generateProductReport;
    private final AccountRestMapper mapper;

    public ReportsApiDelegateImpl(GenerateProductReportUseCase generateProductReport, AccountRestMapper mapper) {
        this.generateProductReport = generateProductReport;
        this.mapper = mapper;
    }

    @Override
    public Mono<ResponseEntity<ProductReportResponse>> getProductReport(
            LocalDate from,
            LocalDate to,
            ServerWebExchange exchange
    ) {
        return Mono.fromSupplier(() -> new GenerateProductReportQuery(from, to))
                .flatMap(generateProductReport::execute)
                .map(report -> ResponseEntity.ok(mapper.toResponse(report)));
    }
}
