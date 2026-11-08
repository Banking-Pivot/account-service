package com.banking.account_service.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/** Credit Service, reached through the API Gateway. */
public interface CreditCardPort {

    /**
     * Whether the customer has an active credit card with the bank. Errors with
     * {@code DependencyUnavailableException} when no valid answer arrives.
     */
    Mono<Boolean> hasActiveCreditCard(UUID customerId);
}
