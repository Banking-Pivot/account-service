package com.banking.account_service.infrastructure.adapter.out.http;

import com.banking.account_service.application.exception.DependencyUnavailableException;
import com.banking.account_service.application.port.out.CreditCardPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Credit Service through the API Gateway.
 *
 * <p>Credit Service has no published contract yet. This client relies on one call:
 * {@code GET /credits?customerId=&type=CREDIT_CARD&status=ACTIVE&size=1} answers 200 with a
 * page whose {@code totalElements} counts the matching credits. A customer without cards
 * gets 200 with zero, never 404. Any other answer, a timeout or an open circuit is
 * {@link DependencyUnavailableException}.
 */
@Component
public class CreditServiceClient implements CreditCardPort {

    static final String DEPENDENCY = "Credit Service";
    static final String CIRCUIT_BREAKER = "credit-service";

    private final WebClient gateway;
    private final ReactiveCircuitBreakerFactory<?, ?> circuitBreakers;

    public CreditServiceClient(WebClient gatewayWebClient, ReactiveCircuitBreakerFactory<?, ?> circuitBreakers) {
        this.gateway = gatewayWebClient;
        this.circuitBreakers = circuitBreakers;
    }

    @Override
    public Mono<Boolean> hasActiveCreditCard(UUID customerId) {
        Mono<Boolean> call = gateway.get()
                .uri(uri -> uri.path("/credits")
                        .queryParam("customerId", customerId)
                        .queryParam("type", "CREDIT_CARD")
                        .queryParam("status", "ACTIVE")
                        .queryParam("size", 1)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchangeToMono(this::read);
        return circuitBreakers.create(CIRCUIT_BREAKER)
                .run(call, failure -> Mono.error(new DependencyUnavailableException(DEPENDENCY, failure)));
    }

    private Mono<Boolean> read(ClientResponse response) {
        if (!response.statusCode().isSameCodeAs(HttpStatus.OK)) {
            return response.releaseBody()
                    .then(Mono.error(new UnexpectedResponseException("Status " + response.statusCode().value() + ".")));
        }
        return response.bodyToMono(CreditPagePayload.class)
                .filter(page -> page.totalElements() != null && page.totalElements() >= 0)
                .switchIfEmpty(Mono.error(() -> new UnexpectedResponseException("Credit page without totalElements.")))
                .map(page -> page.totalElements() > 0);
    }

    /** Field of the credit page that Account Service uses. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record CreditPagePayload(Long totalElements) {
    }
}
