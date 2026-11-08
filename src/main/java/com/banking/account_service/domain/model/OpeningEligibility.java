package com.banking.account_service.domain.model;

import java.util.Objects;

/**
 * What Customer Service and Credit Service said about the primary holder, collected
 * before the account is created.
 *
 * @param hasActiveCreditCard only meaningful for products that require a credit card
 */
public record OpeningEligibility(CustomerType customerType, CustomerProfile customerProfile, boolean hasActiveCreditCard) {

    public OpeningEligibility {
        Objects.requireNonNull(customerType, "customerType");
        Objects.requireNonNull(customerProfile, "customerProfile");
    }
}
