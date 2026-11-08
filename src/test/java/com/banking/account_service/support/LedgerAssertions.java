package com.banking.account_service.support;

import org.springframework.r2dbc.core.DatabaseClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Invariants of the stored ledger, checked straight in PostgreSQL after every integration
 * test. Each query lists the rows that break one rule; all must be empty.
 */
public final class LedgerAssertions {

    private static final String SIGNED_AMOUNT = """
            CASE WHEN m.movement_type IN ('OPENING_DEPOSIT', 'DEPOSIT', 'TRANSFER_IN')
                 THEN m.amount ELSE -m.amount END - m.fee""";

    /** Balance = credits - debits - fees; last sequence = number of movements. */
    private static final String BALANCE_MATCHES_HISTORY = """
            SELECT a.id::text AS id
              FROM accounts a
              LEFT JOIN (SELECT m.account_id, SUM(%s) AS net, MAX(m.sequence_number) AS last_sequence,
                                COUNT(*) AS movements
                           FROM account_movements m GROUP BY m.account_id) h ON h.account_id = a.id
             WHERE a.balance <> COALESCE(h.net, 0)
                OR a.movement_sequence <> COALESCE(h.last_sequence, 0)
                OR a.movement_sequence <> COALESCE(h.movements, 0)
            """.formatted(SIGNED_AMOUNT);

    /** Each balance_after follows from the previous one and the movement itself. */
    private static final String RUNNING_BALANCE = """
            SELECT id::text AS id FROM (
                SELECT m.id, m.balance_after,
                       COALESCE(LAG(m.balance_after) OVER (PARTITION BY m.account_id ORDER BY m.sequence_number), 0)
                           + %s AS expected
                  FROM account_movements m) chain
             WHERE balance_after <> expected
            """.formatted(SIGNED_AMOUNT);

    /** Every operation is completed and its result is the movement of its own account. */
    private static final String OPERATION_MATCHES_MOVEMENT = """
            SELECT o.id::text AS id
              FROM operations o
              LEFT JOIN account_movements m ON m.operation_id = o.id AND m.account_id = o.account_id
             WHERE o.status <> 'COMPLETED' OR m.id IS NULL
                OR m.amount <> o.amount OR m.fee <> o.fee OR m.balance_after <> o.balance_after
            """;

    /** A transfer has exactly two movements, any other operation exactly one. */
    private static final String MOVEMENTS_PER_OPERATION = """
            SELECT o.id::text AS id
              FROM operations o
              JOIN account_movements m ON m.operation_id = o.id
             GROUP BY o.id, o.operation_type
            HAVING COUNT(*) <> CASE WHEN o.operation_type = 'TRANSFER' THEN 2 ELSE 1 END
            """;

    private LedgerAssertions() {
    }

    public static void assertConsistent(DatabaseClient client) {
        assertThat(ids(client, BALANCE_MATCHES_HISTORY)).as("accounts whose balance differs from history").isEmpty();
        assertThat(ids(client, RUNNING_BALANCE)).as("movements with a wrong balance_after").isEmpty();
        assertThat(ids(client, OPERATION_MATCHES_MOVEMENT)).as("operations without a matching movement").isEmpty();
        assertThat(ids(client, MOVEMENTS_PER_OPERATION)).as("operations with a wrong number of movements").isEmpty();
    }

    private static List<String> ids(DatabaseClient client, String sql) {
        return client.sql(sql).map(row -> row.get("id", String.class)).all().collectList().block();
    }
}
