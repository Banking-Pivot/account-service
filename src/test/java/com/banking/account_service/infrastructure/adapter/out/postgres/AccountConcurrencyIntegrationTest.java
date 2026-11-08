package com.banking.account_service.infrastructure.adapter.out.postgres;

import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.OperationResponse;
import com.banking.account_service.support.PostgresIntegrationTest;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Races against the real PostgreSQL: requests are released together by a barrier and the
 * outcome is checked in the responses and in the tables. The ledger check of the base
 * class runs after each test.
 */
class AccountConcurrencyIntegrationTest extends PostgresIntegrationTest {

    private static final int PARALLEL = 8;

    @RepeatedTest(3)
    void concurrentOpeningsOfTheSameSavingsCreateOneAccount() throws Exception {
        UUID customer = personalCustomer();

        List<Reply> replies = concurrently(PARALLEL, i -> send(post("/accounts", Map.of(
                "product", "SAVINGS", "currency", "PEN", "primaryHolderId", customer, "initialDeposit", 10))));

        assertThat(statuses(replies)).containsEntry(201, 1L).containsEntry(409, (long) PARALLEL - 1);
        assertThat(replies).filteredOn(reply -> reply.status() == 409)
                .allSatisfy(reply -> assertThat(reply.body()).contains("PERSONAL_ACCOUNT_ALREADY_EXISTS"));
        assertThat(count("SELECT COUNT(*) FROM accounts", Map.of())).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM account_movements", Map.of())).isEqualTo(1);
    }

    @RepeatedTest(3)
    void twoWithdrawalsThatTogetherExceedTheBalance() throws Exception {
        AccountResponse account = openSavings(personalCustomer(), "100.00");

        List<Reply> replies = concurrently(2, i -> send(withdrawRequest(account.getId(), UUID.randomUUID(), "60.00")));

        assertThat(statuses(replies)).containsEntry(201, 1L).containsEntry(422, 1L);
        assertThat(replies).filteredOn(reply -> reply.status() == 422)
                .allSatisfy(reply -> assertThat(reply.body()).contains("INSUFFICIENT_FUNDS"));
        assertMoney(storedBalance(account.getId()), "40.00");
        assertThat(movementCount(account.getId())).isEqualTo(2);
    }

    @Test
    void manyWithdrawalsNeverOverdraw() throws Exception {
        AccountResponse account = open("CHECKING", "PEN", personalCustomer(), "100.00");

        List<Reply> replies = concurrently(10, i -> send(withdrawRequest(account.getId(), UUID.randomUUID(), "30.00")));

        assertThat(statuses(replies)).containsEntry(201, 3L).containsEntry(422, 7L);
        assertMoney(storedBalance(account.getId()), "10.00");
    }

    @RepeatedTest(3)
    void concurrentRepeatsOfATransferMoveTheMoneyOnce() throws Exception {
        AccountResponse source = openSavings(personalCustomer(), "100.00");
        AccountResponse destination = openSavings(personalCustomer(), "0");
        UUID operationId = UUID.randomUUID();

        List<Reply> replies = concurrently(PARALLEL,
                i -> send(transferRequest(source.getId(), operationId, destination.getId(), "25.00")));

        assertThat(statuses(replies)).containsEntry(201, 1L).containsEntry(200, (long) PARALLEL - 1);
        assertThat(replies).filteredOn(reply -> reply.status() == 200)
                .allSatisfy(reply -> assertThat(reply.replayed()).isTrue());
        assertThat(replies.stream().map(Reply::body).distinct()).as("same original result").hasSize(1);
        assertMoney(storedBalance(source.getId()), "75.00");
        assertMoney(storedBalance(destination.getId()), "25.00");
        assertThat(count("SELECT COUNT(*) FROM account_movements WHERE operation_id = :id",
                Map.of("id", operationId))).isEqualTo(2);
    }

    @RepeatedTest(3)
    void concurrentUseOfOneKeyForDifferentTransfers() throws Exception {
        AccountResponse source = openSavings(personalCustomer(), "100.00");
        AccountResponse destination = openSavings(personalCustomer(), "0");
        UUID operationId = UUID.randomUUID();

        List<Reply> replies = concurrently(2, i -> send(transferRequest(source.getId(), operationId,
                destination.getId(), i == 0 ? "10.00" : "20.00")));

        assertThat(statuses(replies)).containsEntry(201, 1L).containsEntry(409, 1L);
        BigDecimal moved = new BigDecimal("100.00").subtract(storedBalance(source.getId()));
        assertThat(moved).isIn(new BigDecimal("10.00"), new BigDecimal("20.00"));
        assertMoney(storedBalance(destination.getId()), moved.toPlainString());
    }

    @Test
    void oppositeTransfersDoNotDeadlock() throws Exception {
        // Two business checking accounts: 20 free movements each, so no fee changes the total.
        UUID company = businessCustomer("STANDARD");
        AccountResponse first = open("CHECKING", "PEN", company, "1000.00");
        AccountResponse second = open("CHECKING", "PEN", company, "1000.00");

        List<Reply> replies = concurrently(16, i -> i % 2 == 0
                ? send(transferRequest(first.getId(), UUID.randomUUID(), second.getId(), "10.00"))
                : send(transferRequest(second.getId(), UUID.randomUUID(), first.getId(), "10.00")));

        assertThat(statuses(replies)).containsOnlyKeys(201).containsEntry(201, 16L);
        assertMoney(storedBalance(first.getId()).add(storedBalance(second.getId())), "2000.00");
    }

    @Test
    void failureBetweenDebitAndCreditRollsBackEverything() {
        AccountResponse source = openSavings(personalCustomer(), "100.00");
        AccountResponse destination = openSavings(personalCustomer(), "0");
        UUID operationId = UUID.randomUUID();
        // The credit leg is the last write of the transfer: by then the source balance, the
        // debit movement and the destination balance are already written in the transaction.
        execute("CREATE FUNCTION fail_transfer_in() RETURNS trigger LANGUAGE plpgsql AS "
                + "$$ BEGIN RAISE EXCEPTION 'simulated failure before the credit movement'; END $$");
        execute("CREATE TRIGGER fail_transfer_in BEFORE INSERT ON account_movements FOR EACH ROW "
                + "WHEN (NEW.movement_type = 'TRANSFER_IN') EXECUTE FUNCTION fail_transfer_in()");
        try {
            transferRequest(source.getId(), operationId, destination.getId(), "40.00")
                    .expectStatus().isEqualTo(500)
                    .expectBody().jsonPath("$.code").isEqualTo("INTERNAL_ERROR");
        } finally {
            execute("DROP TRIGGER fail_transfer_in ON account_movements");
            execute("DROP FUNCTION fail_transfer_in()");
        }

        assertMoney(storedBalance(source.getId()), "100.00");
        assertMoney(storedBalance(destination.getId()), "0");
        assertThat(movementCount(source.getId())).isEqualTo(1);
        assertThat(movementCount(destination.getId())).isZero();
        assertThat(count("SELECT COUNT(*) FROM operations WHERE id = :id", Map.of("id", operationId))).isZero();
        assertThat(count("SELECT version FROM accounts WHERE id = :id", Map.of("id", source.getId())))
                .as("source version untouched").isZero();

        OperationResponse retried = created(transferRequest(source.getId(), operationId, destination.getId(), "40.00"));

        assertMoney(retried.getBalanceAfter(), "60.00");
        assertMoney(storedBalance(destination.getId()), "40.00");
    }

    private void execute(String sql) {
        databaseClient.sql(sql).then().block();
    }

    private List<Reply> concurrently(int requests, IntFunction<Reply> call) throws Exception {
        CyclicBarrier start = new CyclicBarrier(requests);
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        try {
            List<Future<Reply>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return call.apply(index);
                }));
            }
            List<Reply> replies = new ArrayList<>();
            for (Future<Reply> future : futures) {
                replies.add(future.get(30, TimeUnit.SECONDS));
            }
            return replies;
        } finally {
            executor.shutdownNow();
        }
    }

    private static Reply send(WebTestClient.ResponseSpec response) {
        EntityExchangeResult<String> result = response.expectBody(String.class).returnResult();
        return new Reply(result.getStatus().value(), result.getResponseBody(),
                "true".equals(result.getResponseHeaders().getFirst("Idempotent-Replayed")));
    }

    private static Map<Integer, Long> statuses(List<Reply> replies) {
        return replies.stream().collect(Collectors.groupingBy(Reply::status, Collectors.counting()));
    }

    private record Reply(int status, String body, boolean replayed) {
    }
}
