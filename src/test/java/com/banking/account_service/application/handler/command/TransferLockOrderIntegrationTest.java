package com.banking.account_service.application.handler.command;

import com.banking.account_service.support.PostgresIntegrationTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both accounts of a transfer are locked in one global order, so opposite transfers never
 * wait on each other in a cycle. The order must not depend on which account is the source.
 */
class TransferLockOrderIntegrationTest extends PostgresIntegrationTest {

    private static final UUID LOW = UUID.fromString("10000000-0000-4000-8000-000000000000");
    private static final UUID HIGH = UUID.fromString("80000000-0000-4000-8000-000000000000");

    @Test
    void orderIsTheSameWhicheverAccountIsTheSource() {
        assertThat(TransferHandler.lockOrder(LOW, HIGH)).containsExactly(LOW, HIGH);
        assertThat(TransferHandler.lockOrder(HIGH, LOW)).containsExactly(LOW, HIGH);
    }

    @Test
    void orderIsNotUuidCompareTo() {
        // UUID.compareTo compares signed longs, so 8000... sorts before 1000...
        assertThat(HIGH.compareTo(LOW)).isNegative();
        assertThat(TransferHandler.lockOrder(HIGH, LOW).get(0)).isEqualTo(LOW);
    }

    @RepeatedTest(20)
    void orderMatchesPostgresql() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        List<UUID> postgres = databaseClient.sql("SELECT id FROM (VALUES (:first), (:second)) AS ids(id) ORDER BY id")
                .bind("first", first)
                .bind("second", second)
                .map(row -> row.get("id", UUID.class))
                .all()
                .collectList()
                .block();

        assertThat(TransferHandler.lockOrder(first, second)).isEqualTo(postgres);
    }
}
