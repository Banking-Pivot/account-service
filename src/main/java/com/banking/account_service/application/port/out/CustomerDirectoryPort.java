package com.banking.account_service.application.port.out;

import com.banking.account_service.application.model.CustomerSnapshot;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Customer Service, reached through the API Gateway. */
public interface CustomerDirectoryPort {

    /**
     * The customer, or empty when Customer Service answers that it does not exist.
     * Errors with {@code DependencyUnavailableException} when no valid answer arrives.
     */
    Mono<CustomerSnapshot> findById(UUID customerId);
}
