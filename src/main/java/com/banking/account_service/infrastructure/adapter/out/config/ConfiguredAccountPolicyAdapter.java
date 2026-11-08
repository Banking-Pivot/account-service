package com.banking.account_service.infrastructure.adapter.out.config;

import com.banking.account_service.application.port.out.AccountPolicyPort;
import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.Amounts;
import com.banking.account_service.domain.model.CurrencyCode;
import com.banking.account_service.domain.model.ProductPolicy;
import com.banking.account_service.infrastructure.config.AccountProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/**
 * Product terms from {@code account.products}, built once at startup. A product or
 * currency with missing or contradictory terms stops the application instead of failing
 * on the first request that needs it.
 */
@Component
public class ConfiguredAccountPolicyAdapter implements AccountPolicyPort {

    private final Map<AccountProduct, Map<CurrencyCode, ProductPolicy>> policies;

    public ConfiguredAccountPolicyAdapter(AccountProperties properties) {
        Map<AccountProduct, Map<CurrencyCode, ProductPolicy>> built = new EnumMap<>(AccountProduct.class);
        for (AccountProduct product : AccountProduct.values()) {
            Map<CurrencyCode, ProductPolicy> byCurrency = new EnumMap<>(CurrencyCode.class);
            for (CurrencyCode currency : CurrencyCode.values()) {
                byCurrency.put(currency, policy(product, currency, properties.products().of(product)));
            }
            built.put(product, Map.copyOf(byCurrency));
        }
        this.policies = Map.copyOf(built);
    }

    @Override
    public ProductPolicy policyFor(AccountProduct product, CurrencyCode currency) {
        return policies.get(product).get(currency);
    }

    static ProductPolicy policy(AccountProduct product, CurrencyCode currency, AccountProperties.ProductTerms terms) {
        String prefix = "account.products." + product + "." + currency + ": ";
        Integer limit = product == AccountProduct.FIXED_TERM && terms.monthlyMovementLimit() == null
                ? Integer.valueOf(ProductPolicy.FIXED_TERM_MONTHLY_LIMIT)
                : terms.monthlyMovementLimit();
        try {
            return new ProductPolicy(
                    product,
                    currency,
                    required(terms.openingMinimum(), currency, prefix + "opening-minimum"),
                    terms.freeMonthlyMovements(),
                    required(terms.movementFee(), currency, prefix + "movement-fee"),
                    limit,
                    terms.monthlyMaintenanceFee() == null
                            ? Amounts.ZERO
                            : terms.monthlyMaintenanceFee().getOrDefault(currency, Amounts.ZERO),
                    terms.minimumDailyAverageBalance() == null
                            ? null
                            : required(terms.minimumDailyAverageBalance(), currency,
                            prefix + "minimum-daily-average-balance"),
                    terms.operationDayOfMonth());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException(prefix + invalid.getMessage(), invalid);
        }
    }

    private static BigDecimal required(Map<CurrencyCode, BigDecimal> values, CurrencyCode currency, String name) {
        BigDecimal value = values == null ? null : values.get(currency);
        if (value == null) {
            throw new IllegalStateException(name + " is missing.");
        }
        return value;
    }
}
