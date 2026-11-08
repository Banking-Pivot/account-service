package com.banking.account_service.application.support;

import com.banking.account_service.domain.model.PostingContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Translates the UTC clock into business dates. Instants are stored in UTC; days and
 * months for quotas, limits, the fixed-term day and reports follow the configured zone.
 */
public final class BusinessCalendar {

    private final Clock clock;
    private final ZoneId zone;

    public BusinessCalendar(Clock clock, ZoneId zone) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /** Current instant with millisecond precision, the precision kept in PostgreSQL and JSON. */
    public Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    /** Business date of an instant. */
    public LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, zone);
    }

    /** Current business date. */
    public LocalDate today() {
        return dateOf(now());
    }

    /** Current business month. */
    public YearMonth currentPeriod() {
        return YearMonth.from(today());
    }

    /** First instant of a business date. */
    public Instant startOf(LocalDate date) {
        return date.atStartOfDay(zone).toInstant();
    }

    /** Business time zone. */
    public ZoneId zone() {
        return zone;
    }

    /** Context for movements posted now under the given operation. */
    public PostingContext posting(UUID operationId, String description) {
        Instant now = now();
        return new PostingContext(operationId, now, dateOf(now), description);
    }
}
