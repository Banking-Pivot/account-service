package com.banking.account_service.infrastructure.config;

import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.CurrencyCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;

/**
 * {@code account.*} settings, served by the Config Server ({@code account-service.yml}).
 * Fees, limits and minimums live here, never in code. Amounts are per currency.
 *
 * @param businessZone time zone of business dates: monthly quota, fixed-term day, reports
 */
@Validated
@ConfigurationProperties("account")
public record AccountProperties(
        @NotNull ZoneId businessZone,
        @NotNull @Valid Products products,
        @NotNull @Valid Integration integration
) {

    /** Terms of each product. */
    public record Products(
            @NotNull @Valid ProductTerms savings,
            @NotNull @Valid ProductTerms vipSavings,
            @NotNull @Valid ProductTerms checking,
            @NotNull @Valid ProductTerms smeChecking,
            @NotNull @Valid ProductTerms fixedTerm
    ) {

        public ProductTerms of(AccountProduct product) {
            return switch (product) {
                case SAVINGS -> savings;
                case VIP_SAVINGS -> vipSavings;
                case CHECKING -> checking;
                case SME_CHECKING -> smeChecking;
                case FIXED_TERM -> fixedTerm;
            };
        }
    }

    /**
     * Terms of one product. {@code ProductPolicy} rejects combinations that contradict the
     * product, for example a maintenance fee on savings.
     *
     * @param freeMonthlyMovements       movements per business month without fee (quota)
     * @param movementFee                fee per movement beyond the quota
     * @param monthlyMovementLimit       hard limit; savings only (fixed term is always 1)
     * @param monthlyMaintenanceFee      standard checking only; omitted means zero
     * @param minimumDailyAverageBalance VIP savings only
     * @param operationDayOfMonth        fixed term only; 31 means the last day of each month
     */
    public record ProductTerms(
            @NotEmpty Map<CurrencyCode, @NotNull @PositiveOrZero BigDecimal> openingMinimum,
            @NotNull @PositiveOrZero Integer freeMonthlyMovements,
            @NotEmpty Map<CurrencyCode, @NotNull @PositiveOrZero BigDecimal> movementFee,
            @Min(1) Integer monthlyMovementLimit,
            Map<CurrencyCode, @NotNull @PositiveOrZero BigDecimal> monthlyMaintenanceFee,
            Map<CurrencyCode, @NotNull @PositiveOrZero BigDecimal> minimumDailyAverageBalance,
            @Min(1) @Max(31) Integer operationDayOfMonth
    ) {
    }

    /**
     * Calls to other services, always through the API Gateway.
     *
     * @param timeout limit per call before the answer counts as unavailable
     */
    public record Integration(
            @NotNull URI gatewayBaseUrl,
            @NotNull @DefaultValue("2s") Duration timeout,
            @NotNull @Valid @DefaultValue CircuitBreaker circuitBreaker
    ) {
    }

    /** Resilience4j circuit breaker shared by the Customer and Credit calls (one instance each). */
    public record CircuitBreaker(
            @DefaultValue("50") @Min(1) @Max(100) float failureRateThreshold,
            @DefaultValue("10") @Min(1) int slidingWindowSize,
            @DefaultValue("5") @Min(1) int minimumNumberOfCalls,
            @DefaultValue("10s") @NotNull Duration waitDurationInOpenState,
            @DefaultValue("2") @Min(1) int permittedCallsInHalfOpenState
    ) {
    }
}
