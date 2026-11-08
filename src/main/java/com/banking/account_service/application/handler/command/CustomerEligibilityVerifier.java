package com.banking.account_service.application.handler.command;

import com.banking.account_service.application.model.CustomerSnapshot;
import com.banking.account_service.application.port.out.CreditCardPort;
import com.banking.account_service.application.port.out.CustomerDirectoryPort;
import com.banking.account_service.domain.exception.AccountRuleViolationException;
import com.banking.account_service.domain.exception.RuleViolation;
import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.OpeningEligibility;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Asks Customer Service and Credit Service about customers before any account row is
 * locked, so a slow dependency never holds a lock.
 *
 * <p>An answer that makes the customer ineligible becomes a rule violation (422). When no
 * valid answer arrives, the adapters raise {@code DependencyUnavailableException} (503).
 */
public class CustomerEligibilityVerifier {

    private final CustomerDirectoryPort customers;
    private final CreditCardPort creditCards;

    public CustomerEligibilityVerifier(CustomerDirectoryPort customers, CreditCardPort creditCards) {
        this.customers = Objects.requireNonNull(customers);
        this.creditCards = Objects.requireNonNull(creditCards);
    }

    /** The customer, which must exist and be active. */
    public Mono<CustomerSnapshot> requireActive(UUID customerId) {
        return customers.findById(customerId)
                .switchIfEmpty(Mono.error(() -> new AccountRuleViolationException(RuleViolation.CUSTOMER_NOT_FOUND,
                        "Customer " + customerId + " was not found.")))
                .flatMap(customer -> customer.active()
                        ? Mono.just(customer)
                        : Mono.error(new AccountRuleViolationException(RuleViolation.CUSTOMER_INACTIVE,
                        "Customer " + customerId + " is not active.")));
    }

    /** Checks the customers one by one, so the first failing ID is the one reported. */
    public Mono<Void> requireAllActive(Collection<UUID> customerIds) {
        return Flux.fromIterable(customerIds)
                .concatMap(this::requireActive)
                .then();
    }

    /**
     * What the account opening needs to know about the primary holder. Credit Service is
     * asked only when the product requires a card and accepts the customer, so a
     * customer who cannot have the product never waits for it.
     */
    public Mono<OpeningEligibility> openingEligibility(CustomerSnapshot primaryHolder, AccountProduct product) {
        boolean asksForCard = product.requiresCreditCard()
                && product.isAllowedFor(primaryHolder.type(), primaryHolder.profile());
        if (!asksForCard) {
            return Mono.just(new OpeningEligibility(primaryHolder.type(), primaryHolder.profile(), false));
        }
        return creditCards.hasActiveCreditCard(primaryHolder.id())
                .map(hasCard -> new OpeningEligibility(primaryHolder.type(), primaryHolder.profile(), hasCard));
    }
}
