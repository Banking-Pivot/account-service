package com.banking.account_service.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Instant;

/** Replaces the system clock with one the tests control. */
@TestConfiguration(proxyBeanMethods = false)
public class MutableClockConfiguration {

    /** Monday 16 November 2026, 10:00 in Lima. The fixed-term day (15) of November has passed. */
    public static final Instant START = Instant.parse("2026-11-16T15:00:00Z");

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(START);
    }
}
