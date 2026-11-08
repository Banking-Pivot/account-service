package com.banking.account_service.infrastructure.adapter.out.config;

import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.CurrencyCode;
import com.banking.account_service.domain.model.ProductPolicy;
import com.banking.account_service.infrastructure.config.AccountProperties;
import com.banking.account_service.infrastructure.config.AccountProperties.ProductTerms;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredAccountPolicyAdapterTest {

    private static final Map<CurrencyCode, BigDecimal> ZERO = Map.of(CurrencyCode.PEN, BigDecimal.ZERO,
            CurrencyCode.USD, BigDecimal.ZERO);

    @Test
    void buildsEveryProductAndCurrencyWithDefaults() {
        ConfiguredAccountPolicyAdapter adapter = new ConfiguredAccountPolicyAdapter(properties(fixedTerm(null), savings()));

        ProductPolicy fixedTerm = adapter.policyFor(AccountProduct.FIXED_TERM, CurrencyCode.USD);
        assertThat(fixedTerm.monthlyMovementLimit()).as("fixed term is always one per month").isEqualTo(1);
        assertThat(fixedTerm.operationDayOfMonth()).isEqualTo(15);
        assertThat(fixedTerm.monthlyMaintenanceFee()).isEqualByComparingTo("0");
        ProductPolicy checking = adapter.policyFor(AccountProduct.CHECKING, CurrencyCode.PEN);
        assertThat(checking.monthlyMaintenanceFee()).isEqualByComparingTo("12.00");
        assertThat(adapter.policyFor(AccountProduct.CHECKING, CurrencyCode.USD).monthlyMaintenanceFee())
                .as("currency without maintenance fee").isEqualByComparingTo("0");
        assertThat(adapter.policyFor(AccountProduct.SAVINGS, CurrencyCode.PEN).movementFee())
                .isEqualByComparingTo("2.50");
    }

    @Test
    void aMissingCurrencyStopsTheStartup() {
        ProductTerms savingsWithoutUsdFee = new ProductTerms(ZERO, 5, Map.of(CurrencyCode.PEN, new BigDecimal("2.50")),
                10, null, null, null);

        assertThatThrownBy(() -> new ConfiguredAccountPolicyAdapter(properties(fixedTerm(null), savingsWithoutUsdFee)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("account.products.SAVINGS.USD: movement-fee is missing");
    }

    @Test
    void termsThatContradictTheProductStopTheStartup() {
        assertThatThrownBy(() -> new ConfiguredAccountPolicyAdapter(properties(fixedTerm(3), savings())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("account.products.FIXED_TERM.PEN: ")
                .hasMessageContaining("exactly one movement per month");
    }

    private static ProductTerms savings() {
        return new ProductTerms(ZERO, 5, Map.of(CurrencyCode.PEN, new BigDecimal("2.50"),
                CurrencyCode.USD, new BigDecimal("0.75")), 10, null, null, null);
    }

    private static ProductTerms fixedTerm(Integer limit) {
        return new ProductTerms(ZERO, 1, ZERO, limit, null, null, 15);
    }

    private static AccountProperties properties(ProductTerms fixedTerm, ProductTerms savings) {
        ProductTerms vip = new ProductTerms(ZERO, 10, ZERO, 20, null, ZERO, null);
        ProductTerms checking = new ProductTerms(ZERO, 20, ZERO, null,
                Map.of(CurrencyCode.PEN, new BigDecimal("12.00")), null, null);
        ProductTerms sme = new ProductTerms(ZERO, 50, ZERO, null, null, null, null);
        return new AccountProperties(ZoneId.of("America/Lima"),
                new AccountProperties.Products(savings, vip, checking, sme, fixedTerm),
                new AccountProperties.Integration(URI.create("http://localhost:8080"), Duration.ofSeconds(2),
                        new AccountProperties.CircuitBreaker(50, 10, 5, Duration.ofSeconds(10), 2)));
    }
}
