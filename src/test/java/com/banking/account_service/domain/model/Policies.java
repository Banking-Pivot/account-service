package com.banking.account_service.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/** PEN terms equal to {@code stub-config/account-service.yml}, for domain unit tests. */
final class Policies {

    static final ProductPolicy SAVINGS = new ProductPolicy(AccountProduct.SAVINGS, CurrencyCode.PEN,
            money("0"), 5, money("2.50"), 10, money("0"), null, null);
    static final ProductPolicy VIP_SAVINGS = new ProductPolicy(AccountProduct.VIP_SAVINGS, CurrencyCode.PEN,
            money("1000"), 10, money("1.50"), 20, money("0"), money("1000"), null);
    static final ProductPolicy CHECKING = new ProductPolicy(AccountProduct.CHECKING, CurrencyCode.PEN,
            money("0"), 20, money("1.00"), null, money("12.00"), null, null);
    static final ProductPolicy SME_CHECKING = new ProductPolicy(AccountProduct.SME_CHECKING, CurrencyCode.PEN,
            money("0"), 50, money("0.80"), null, money("0"), null, null);
    static final ProductPolicy FIXED_TERM = new ProductPolicy(AccountProduct.FIXED_TERM, CurrencyCode.PEN,
            money("1000"), 1, money("0"), 1, money("0"), null, 15);

    static final OpeningEligibility PERSONAL_STANDARD =
            new OpeningEligibility(CustomerType.PERSONAL, CustomerProfile.STANDARD, false);

    private Policies() {
    }

    static BigDecimal money(String value) {
        return new BigDecimal(value);
    }

    static PostingContext on(LocalDate businessDate) {
        return new PostingContext(UUID.randomUUID(), businessDate.atTime(15, 0).toInstant(ZoneOffset.UTC),
                businessDate, null);
    }

    static Account open(ProductPolicy policy, OpeningEligibility eligibility, String deposit, LocalDate date) {
        UUID holder = UUID.randomUUID();
        return Account.open(UUID.randomUUID(), policy.product(), policy.currency(), eligibility,
                AccountParticipants.of(holder, null, null), money(deposit), policy, on(date)).account();
    }
}
