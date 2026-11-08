package com.banking.account_service.infrastructure.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Outbound HTTP: one WebClient pointed at the API Gateway, and the Resilience4j settings
 * every circuit breaker uses (time limit per call and failure thresholds), all from
 * {@code account.integration}.
 */
@Configuration
public class IntegrationConfiguration {

    @Bean
    public WebClient gatewayWebClient(WebClient.Builder builder, AccountProperties properties) {
        return builder.clone()
                .baseUrl(properties.integration().gatewayBaseUrl().toString())
                .build();
    }

    @Bean
    public Customizer<ReactiveResilience4JCircuitBreakerFactory> dependencyCircuitBreakers(AccountProperties properties) {
        AccountProperties.Integration integration = properties.integration();
        AccountProperties.CircuitBreaker breaker = integration.circuitBreaker();
        TimeLimiterConfig timeLimiter = TimeLimiterConfig.custom()
                .timeoutDuration(integration.timeout())
                .cancelRunningFuture(true)
                .build();
        CircuitBreakerConfig circuitBreaker = CircuitBreakerConfig.custom()
                .failureRateThreshold(breaker.failureRateThreshold())
                .slidingWindowSize(breaker.slidingWindowSize())
                .minimumNumberOfCalls(breaker.minimumNumberOfCalls())
                .waitDurationInOpenState(breaker.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(breaker.permittedCallsInHalfOpenState())
                .build();
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(timeLimiter)
                .circuitBreakerConfig(circuitBreaker)
                .build());
    }
}
