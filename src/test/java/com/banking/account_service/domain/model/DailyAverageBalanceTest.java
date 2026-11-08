package com.banking.account_service.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static com.banking.account_service.domain.model.Policies.money;
import static org.assertj.core.api.Assertions.assertThat;

class DailyAverageBalanceTest {

    private static final UUID ACCOUNT = UUID.randomUUID();

    @Test
    void daysWithoutMovementsKeepThePreviousClosingBalance() {
        // 1-10: 1000 (from the previous month), 11-20: 3000, 21-30: 0 -> (10000 + 30000 + 0) / 30
        List<Movement> movements = List.of(
                movement(1, "2026-11-11", "3000"),
                movement(2, "2026-11-21", "0"));

        DailyAverageBalance average = DailyAverageBalance.compute(LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 30), money("1000"), movements);

        assertThat(average.daysCounted()).isEqualTo(30);
        assertThat(average.average()).isEqualByComparingTo("1333.33");
    }

    @Test
    void theLastMovementOfTheDayIsItsClosingBalance() {
        List<Movement> movements = List.of(
                movement(3, "2026-11-02", "50"),
                movement(1, "2026-11-01", "100"),
                movement(2, "2026-11-01", "900"));

        DailyAverageBalance average = DailyAverageBalance.compute(LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 2), BigDecimal.ZERO, movements);

        assertThat(average.average()).isEqualByComparingTo("475.00");
    }

    @Test
    void aNewAccountStartsFromZero() {
        DailyAverageBalance average = DailyAverageBalance.compute(LocalDate.of(2026, 11, 16),
                LocalDate.of(2026, 11, 16), BigDecimal.ZERO, List.of());

        assertThat(average.daysCounted()).isEqualTo(1);
        assertThat(average.average()).isEqualByComparingTo("0");
    }

    private static Movement movement(long sequence, String date, String balanceAfter) {
        return new Movement(UUID.randomUUID(), ACCOUNT, UUID.randomUUID(), sequence, MovementType.DEPOSIT,
                money("1"), money("0"), money(balanceAfter), null, null, Instant.parse(date + "T15:00:00Z"),
                LocalDate.parse(date));
    }
}
