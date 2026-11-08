package com.banking.account_service.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Average of the closing balances of consecutive business days.
 *
 * <p>The closing balance of a day is the balance after its last movement. A day without
 * movements keeps the closing balance of the previous day, so it still counts.
 *
 * @param fromDate first day counted
 * @param toDate   last day counted, inclusive
 */
public record DailyAverageBalance(LocalDate fromDate, LocalDate toDate, int daysCounted, BigDecimal average) {

    /**
     * Computes the average for {@code [fromDate, toDate]}.
     *
     * @param balanceBefore closing balance of the day before {@code fromDate}; zero for a new account
     * @param movements     movements whose business date falls in the range, in any order
     */
    public static DailyAverageBalance compute(
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal balanceBefore,
            List<Movement> movements
    ) {
        Objects.requireNonNull(fromDate, "fromDate");
        Objects.requireNonNull(toDate, "toDate");
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("toDate is before fromDate");
        }

        Map<LocalDate, BigDecimal> closingByDay = movements.stream()
                .collect(Collectors.groupingBy(Movement::businessDate, TreeMap::new,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparingLong(Movement::sequence)),
                                last -> last.map(Movement::balanceAfter).orElseThrow())));

        int days = (int) ChronoUnit.DAYS.between(fromDate, toDate) + 1;
        BigDecimal closing = balanceBefore;
        BigDecimal sum = BigDecimal.ZERO;
        for (LocalDate day = fromDate; !day.isAfter(toDate); day = day.plusDays(1)) {
            closing = closingByDay.getOrDefault(day, closing);
            sum = sum.add(closing);
        }
        BigDecimal average = sum.divide(BigDecimal.valueOf(days), Amounts.SCALE, RoundingMode.HALF_EVEN);
        return new DailyAverageBalance(fromDate, toDate, days, average);
    }
}
