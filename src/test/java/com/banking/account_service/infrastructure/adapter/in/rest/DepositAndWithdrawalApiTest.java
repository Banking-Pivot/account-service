package com.banking.account_service.infrastructure.adapter.in.rest;

import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountBalanceResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.MovementResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.OperationResponse;
import com.banking.account_service.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Journey 2: deposits and withdrawals, their idempotency and the monthly product rules. */
class DepositAndWithdrawalApiTest extends PostgresIntegrationTest {

    @Nested
    class Posting {

        @Test
        void depositAndWithdrawalUpdateBalanceAndHistory() {
            AccountResponse account = openSavings(personalCustomer(), "100.00");
            UUID depositId = UUID.randomUUID();

            OperationResponse deposit = created(depositRequest(account.getId(), depositId, "50.00"));
            OperationResponse withdrawal = withdraw(account.getId(), "30.00");

            assertThat(deposit.getOperationId()).isEqualTo(depositId);
            assertThat(deposit.getType().getValue()).isEqualTo("DEPOSIT");
            assertThat(deposit.getStatus().getValue()).isEqualTo("COMPLETED");
            assertThat(deposit.getAccountId()).isEqualTo(account.getId());
            assertThat(deposit.getCurrency().getValue()).isEqualTo("PEN");
            assertMoney(deposit.getAmount(), "50.00");
            assertMoney(deposit.getFee(), "0");
            assertMoney(deposit.getBalanceAfter(), "150.00");
            assertThat(deposit.getOccurredAt().toInstant()).isEqualTo(clock.instant());
            assertThat(deposit.getBusinessDate()).hasToString("2026-11-16");
            assertThat(deposit.getCounterpartyAccountId()).isNull();
            assertThat(deposit.getTransferKind()).isNull();
            assertThat(withdrawal.getType().getValue()).isEqualTo("WITHDRAWAL");
            assertMoney(withdrawal.getBalanceAfter(), "120.00");

            assertThat(movements(account.getId()).getItems())
                    .extracting(movement -> movement.getType().getValue(), MovementResponse::getSequence)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("WITHDRAWAL", 3L),
                            org.assertj.core.groups.Tuple.tuple("DEPOSIT", 2L),
                            org.assertj.core.groups.Tuple.tuple("OPENING_DEPOSIT", 1L));
            AccountBalanceResponse balance = balance(account.getId());
            assertMoney(balance.getBalance(), "120.00");
            assertThat(balance.getMonthlyUsage().getMovementsUsed()).isEqualTo(2);
            assertThat(balance.getMonthlyUsage().getFreeMovementsRemaining()).isEqualTo(3);
            webTestClient.get().uri("/accounts/{id}", account.getId()).exchange()
                    .expectHeader().valueEquals("ETag", "\"2\"");
        }

        @Test
        void descriptionIsTrimmedAndKept() {
            AccountResponse account = openSavings(personalCustomer(), "0");

            OperationResponse deposit = created(post("/accounts/" + account.getId() + "/deposits",
                    Map.of("operationId", UUID.randomUUID(), "amount", 10, "description", "  Rent  ")));

            assertThat(deposit.getDescription()).isEqualTo("Rent");
            assertThat(movements(account.getId()).getItems().get(0).getDescription()).isEqualTo("Rent");
        }

        @Test
        void withdrawalCannotLeaveANegativeBalance() {
            AccountResponse account = openSavings(personalCustomer(), "100.00");

            assertRuleViolation(withdrawRequest(account.getId(), UUID.randomUUID(), "100.01"), "INSUFFICIENT_FUNDS");

            assertMoney(storedBalance(account.getId()), "100.00");
            assertThat(movementCount(account.getId())).isEqualTo(1);
            assertThat(operationCount()).as("only the opening deposit").isEqualTo(1);
        }

        @Test
        void malformedAmountsAreRejected() {
            AccountResponse account = openSavings(personalCustomer(), "0");
            String uri = "/accounts/" + account.getId() + "/deposits";

            for (Object amount : new Object[] {0, -5, 1.001, new BigDecimal("1000000000000.00")}) {
                post(uri, Map.of("operationId", UUID.randomUUID(), "amount", amount)).expectStatus().isBadRequest().expectBody(Void.class);
            }
            post(uri, Map.of("amount", 10)).expectStatus().isBadRequest().expectBody(Void.class);
            post(uri, Map.of("operationId", "not-a-uuid", "amount", 10)).expectStatus().isBadRequest().expectBody(Void.class);
            assertThat(movementCount(account.getId())).isZero();
        }

        @Test
        void unknownAccountIsNotFound() {
            depositRequest(UUID.randomUUID(), UUID.randomUUID(), "10.00").expectStatus().isNotFound()
                    .expectBody().jsonPath("$.code").isEqualTo("ACCOUNT_NOT_FOUND");
            assertThat(operationCount()).isZero();
        }
    }

    @Nested
    class Idempotency {

        @Test
        void repeatingTheSameRequestReturnsTheOriginalResultWith200() {
            AccountResponse account = openSavings(personalCustomer(), "100.00");
            UUID operationId = UUID.randomUUID();
            OperationResponse original = created(withdrawRequest(account.getId(), operationId, "40.00"));
            clock.advance(Duration.ofHours(1));

            OperationResponse replay = withdrawRequest(account.getId(), operationId, "40.00")
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("Idempotent-Replayed", "true")
                    .expectBody(OperationResponse.class).returnResult().getResponseBody();

            assertThat(replay).isEqualTo(original);
            assertMoney(storedBalance(account.getId()), "60.00");
            assertThat(movementCount(account.getId())).isEqualTo(2);
            assertThat(balance(account.getId()).getMonthlyUsage().getMovementsUsed()).isEqualTo(1);
        }

        @Test
        void theSameKeyWithDifferentDataIsAConflict() {
            AccountResponse account = openSavings(personalCustomer(), "100.00");
            AccountResponse other = openSavings(personalCustomer(), "100.00");
            UUID operationId = UUID.randomUUID();
            created(depositRequest(account.getId(), operationId, "10.00"));

            assertConflict(depositRequest(account.getId(), operationId, "10.01"));
            assertConflict(withdrawRequest(account.getId(), operationId, "10.00"));
            assertConflict(depositRequest(other.getId(), operationId, "10.00"));
            assertConflict(post("/accounts/" + account.getId() + "/deposits",
                    Map.of("operationId", operationId, "amount", 10, "description", "other")));
            assertConflict(transferRequest(account.getId(), operationId, other.getId(), "10.00"));

            assertMoney(storedBalance(account.getId()), "110.00");
            assertMoney(storedBalance(other.getId()), "100.00");
        }

        @Test
        void amountsWithTheSameValueAreTheSameRequest() {
            AccountResponse account = openSavings(personalCustomer(), "0");
            UUID operationId = UUID.randomUUID();
            created(post("/accounts/" + account.getId() + "/deposits", Map.of("operationId", operationId, "amount", 10)));

            post("/accounts/" + account.getId() + "/deposits", Map.of("operationId", operationId, "amount", 10.00))
                    .expectStatus().isOk().expectBody(Void.class);
        }

        @Test
        void aRejectedOperationRecordsNothingAndItsKeyCanBeRetried() {
            AccountResponse account = openSavings(personalCustomer(), "0");
            UUID operationId = UUID.randomUUID();

            assertRuleViolation(withdrawRequest(account.getId(), operationId, "50.00"), "INSUFFICIENT_FUNDS");
            deposit(account.getId(), "80.00");
            OperationResponse retried = created(withdrawRequest(account.getId(), operationId, "50.00"));

            assertMoney(retried.getBalanceAfter(), "30.00");
        }

        @Test
        void theOpeningDepositHasItsOwnOperation() {
            AccountResponse account = openSavings(personalCustomer(), "25.00");

            UUID openingOperation = movements(account.getId()).getItems().get(0).getOperationId();

            assertThat(count("SELECT COUNT(*) FROM operations WHERE id = :id AND operation_type = 'OPENING_DEPOSIT'",
                    Map.of("id", openingOperation))).isEqualTo(1);
            assertConflict(depositRequest(account.getId(), openingOperation, "25.00"));
        }

        private void assertConflict(org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec response) {
            response.expectStatus().isEqualTo(409).expectBody().jsonPath("$.code").isEqualTo("OPERATION_ID_CONFLICT");
        }
    }

    @Nested
    class MonthlyRules {

        @Test
        void savingsChargesTheFeeAfterFiveFreeMovements() {
            AccountResponse account = openSavings(personalCustomer(), "100.00");
            for (int i = 0; i < 5; i++) {
                assertMoney(deposit(account.getId(), "10.00").getFee(), "0");
            }
            assertMoney(balance(account.getId()).getMonthlyUsage().getNextMovementFee(), "2.50");

            OperationResponse sixth = withdraw(account.getId(), "10.00");

            assertMoney(sixth.getFee(), "2.50");
            assertMoney(sixth.getBalanceAfter(), "137.50");
            MovementResponse movement = movements(account.getId()).getItems().get(0);
            assertMoney(movement.getFee(), "2.50");
            assertMoney(movement.getAmount(), "10.00");
            assertMoney(movement.getBalanceAfter(), "137.50");
        }

        @Test
        void checkingHasTwentyFreeMovementsAndNoLimit() {
            AccountResponse account = open("CHECKING", "USD", personalCustomer(), "100.00");
            for (int i = 0; i < 20; i++) {
                deposit(account.getId(), "1.00");
            }

            assertMoney(deposit(account.getId(), "1.00").getFee(), "0.30");
            AccountBalanceResponse balance = balance(account.getId());
            assertThat(balance.getMonthlyUsage().getMovementLimit()).isNull();
            assertThat(balance.getMonthlyUsage().getMovementsRemaining()).isNull();
            assertMoney(balance.getBalance(), "120.70");
        }

        @Test
        void savingsRejectsTheEleventhMovementOfTheMonth() {
            AccountResponse account = openSavings(personalCustomer(), "100.00");
            for (int i = 0; i < 10; i++) {
                deposit(account.getId(), "1.00");
            }

            assertRuleViolation(depositRequest(account.getId(), UUID.randomUUID(), "1.00"), "MONTHLY_MOVEMENT_LIMIT_REACHED");

            AccountBalanceResponse balance = balance(account.getId());
            assertThat(balance.getMonthlyUsage().getMovementsRemaining()).isZero();
            assertMoney(balance.getBalance(), "97.50");
        }

        @Test
        void theNewMonthStartsAtMidnightInTheBusinessZone() {
            AccountResponse account = openSavings(personalCustomer(), "100.00");
            for (int i = 0; i < 10; i++) {
                deposit(account.getId(), "1.00");
            }

            // 30 Nov 23:30 in Lima is already 1 Dec in UTC: still November for the business.
            clock.set(Instant.parse("2026-12-01T04:30:00Z"));
            assertRuleViolation(withdrawRequest(account.getId(), UUID.randomUUID(), "1.00"),
                    "MONTHLY_MOVEMENT_LIMIT_REACHED");
            assertThat(balance(account.getId()).getMonthlyUsage().getPeriod()).isEqualTo("2026-11");

            clock.set(Instant.parse("2026-12-01T05:00:00Z"));
            OperationResponse december = withdraw(account.getId(), "1.00");

            assertThat(december.getBusinessDate()).hasToString("2026-12-01");
            assertMoney(december.getFee(), "0");
            AccountBalanceResponse balance = balance(account.getId());
            assertThat(balance.getMonthlyUsage().getPeriod()).isEqualTo("2026-12");
            assertThat(balance.getMonthlyUsage().getMovementsUsed()).isEqualTo(1);
        }

        @Test
        void fixedTermAcceptsOneMovementOnTheConfiguredDay() {
            AccountResponse account = open("FIXED_TERM", "PEN", personalCustomer(), "1000.00");
            AccountBalanceResponse november = balance(account.getId());
            assertThat(november.getOperationDayOfMonth()).isEqualTo(15);
            assertThat(november.getNextOperationDate()).as("15 Nov already passed").hasToString("2026-12-15");

            assertRuleViolation(depositRequest(account.getId(), UUID.randomUUID(), "100.00"), "FIXED_TERM_OPERATION_DAY");

            clock.set(Instant.parse("2026-12-15T15:00:00Z"));
            assertThat(balance(account.getId()).getNextOperationDate()).hasToString("2026-12-15");
            OperationResponse december = withdraw(account.getId(), "200.00");
            assertMoney(december.getFee(), "0");
            assertMoney(december.getBalanceAfter(), "800.00");
            assertRuleViolation(depositRequest(account.getId(), UUID.randomUUID(), "1.00"), "MONTHLY_MOVEMENT_LIMIT_REACHED");
            assertThat(balance(account.getId()).getNextOperationDate()).hasToString("2027-01-15");

            clock.set(Instant.parse("2027-01-15T15:00:00Z"));
            assertMoney(deposit(account.getId(), "200.00").getBalanceAfter(), "1000.00");
        }
    }
}
