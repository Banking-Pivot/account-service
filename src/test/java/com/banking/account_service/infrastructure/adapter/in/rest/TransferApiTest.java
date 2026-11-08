package com.banking.account_service.infrastructure.adapter.in.rest;

import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.MovementResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.OperationResponse;
import com.banking.account_service.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Journey 3: transfers between accounts of the bank. */
class TransferApiTest extends PostgresIntegrationTest {

    @Test
    void transferBetweenOwnAccountsWritesBothLegsUnderOneOperation() {
        UUID customer = personalCustomer();
        AccountResponse savings = openSavings(customer, "500.00");
        AccountResponse checking = open("CHECKING", "PEN", customer, "0");
        UUID operationId = UUID.randomUUID();

        OperationResponse result = created(post("/accounts/" + savings.getId() + "/transfers", Map.of(
                "operationId", operationId, "destinationAccountId", checking.getId(), "amount", 120,
                "description", "Rent")));

        assertThat(result.getType().getValue()).isEqualTo("TRANSFER");
        assertThat(result.getTransferKind().getValue()).isEqualTo("OWN_ACCOUNTS");
        assertThat(result.getAccountId()).isEqualTo(savings.getId());
        assertThat(result.getCounterpartyAccountId()).isEqualTo(checking.getId());
        assertMoney(result.getAmount(), "120.00");
        assertMoney(result.getBalanceAfter(), "380.00");
        assertMoney(storedBalance(checking.getId()), "120.00");

        MovementResponse debit = movements(savings.getId()).getItems().get(0);
        MovementResponse credit = movements(checking.getId()).getItems().get(0);
        assertThat(debit.getType().getValue()).isEqualTo("TRANSFER_OUT");
        assertThat(credit.getType().getValue()).isEqualTo("TRANSFER_IN");
        assertThat(debit.getOperationId()).isEqualTo(operationId).isEqualTo(credit.getOperationId());
        assertThat(debit.getCounterpartyAccountId()).isEqualTo(checking.getId());
        assertThat(credit.getCounterpartyAccountId()).isEqualTo(savings.getId());
        assertThat(debit.getOccurredAt()).isEqualTo(credit.getOccurredAt());
        assertThat(credit.getDescription()).isEqualTo("Rent");
        assertMoney(credit.getBalanceAfter(), "120.00");
        assertThat(balance(savings.getId()).getMonthlyUsage().getMovementsUsed()).isEqualTo(1);
        assertThat(balance(checking.getId()).getMonthlyUsage().getMovementsUsed()).isEqualTo(1);
    }

    @Test
    void transferToAnotherCustomerIsThirdParty() {
        AccountResponse source = openSavings(personalCustomer(), "100.00");
        AccountResponse destination = openSavings(personalCustomer(), "0");

        assertThat(transfer(source.getId(), destination.getId(), "40.00").getTransferKind().getValue())
                .isEqualTo("THIRD_PARTY");
    }

    @Test
    void eachAccountAppliesItsOwnPolicy() {
        AccountResponse source = openSavings(personalCustomer(), "100.00");
        AccountResponse destination = openSavings(personalCustomer(), "0");
        for (int i = 0; i < 5; i++) {
            deposit(source.getId(), "1.00");
            deposit(destination.getId(), "1.00");
        }

        OperationResponse result = transfer(source.getId(), destination.getId(), "10.00");

        // Source pays its fee on top of the amount, destination pays its own from the credit.
        assertMoney(result.getFee(), "2.50");
        assertMoney(result.getBalanceAfter(), "92.50");
        MovementResponse credit = movements(destination.getId()).getItems().get(0);
        assertMoney(credit.getAmount(), "10.00");
        assertMoney(credit.getFee(), "2.50");
        assertMoney(credit.getBalanceAfter(), "12.50");
    }

    @Test
    void destinationRulesRejectTheWholeTransfer() {
        AccountResponse source = open("CHECKING", "PEN", personalCustomer(), "100.00");
        AccountResponse destination = openSavings(personalCustomer(), "0");
        for (int i = 0; i < 10; i++) {
            deposit(destination.getId(), "5.00");
        }
        AccountResponse fixedTerm = open("FIXED_TERM", "PEN", personalCustomer(), "1000.00");

        transferRequest(source.getId(), UUID.randomUUID(), destination.getId(), "10.00")
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.code").isEqualTo("MONTHLY_MOVEMENT_LIMIT_REACHED")
                .jsonPath("$.message").value(message -> assertThat((String) message).startsWith("Destination account: "));
        assertRuleViolation(transferRequest(source.getId(), UUID.randomUUID(), fixedTerm.getId(), "10.00"),
                "FIXED_TERM_OPERATION_DAY");

        assertMoney(storedBalance(source.getId()), "100.00");
        assertThat(movementCount(source.getId())).isEqualTo(1);
        assertThat(balance(source.getId()).getMonthlyUsage().getMovementsUsed()).isZero();
    }

    @Test
    void sourceMustCoverAmountAndFee() {
        AccountResponse source = openSavings(personalCustomer(), "50.00");
        AccountResponse destination = openSavings(personalCustomer(), "0");

        assertRuleViolation(transferRequest(source.getId(), UUID.randomUUID(), destination.getId(), "50.01"),
                "INSUFFICIENT_FUNDS");
        assertMoney(storedBalance(destination.getId()), "0");
    }

    @Test
    void transferRulesOnAccountsAndCurrency() {
        UUID customer = personalCustomer();
        AccountResponse pen = openSavings(customer, "100.00");
        AccountResponse usd = open("CHECKING", "USD", customer, "0");
        AccountResponse closed = open("CHECKING", "PEN", personalCustomer(), "0");
        webTestClient.delete().uri("/accounts/{id}", closed.getId()).header("If-Match", "\"0\"").exchange()
                .expectStatus().isNoContent();

        assertRuleViolation(transferRequest(pen.getId(), UUID.randomUUID(), pen.getId(), "1.00"), "SAME_ACCOUNT_TRANSFER");
        assertRuleViolation(transferRequest(pen.getId(), UUID.randomUUID(), usd.getId(), "1.00"), "CURRENCY_MISMATCH");
        assertRuleViolation(transferRequest(pen.getId(), UUID.randomUUID(), UUID.randomUUID(), "1.00"),
                "DESTINATION_ACCOUNT_NOT_FOUND");
        assertRuleViolation(transferRequest(pen.getId(), UUID.randomUUID(), closed.getId(), "1.00"),
                "DESTINATION_ACCOUNT_CLOSED");
        transferRequest(UUID.randomUUID(), UUID.randomUUID(), pen.getId(), "1.00").expectStatus().isNotFound()
                .expectBody().jsonPath("$.code").isEqualTo("ACCOUNT_NOT_FOUND");
        transferRequest(closed.getId(), UUID.randomUUID(), pen.getId(), "1.00").expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.code").isEqualTo("ACCOUNT_CLOSED");

        assertMoney(storedBalance(pen.getId()), "100.00");
        assertThat(operationCount()).as("only the opening deposit").isEqualTo(1);
    }

    @Test
    void replayReturnsTheOriginalDebitLegAndMovesNothing() {
        AccountResponse source = openSavings(personalCustomer(), "100.00");
        AccountResponse destination = openSavings(personalCustomer(), "0");
        UUID operationId = UUID.randomUUID();
        OperationResponse original = created(transferRequest(source.getId(), operationId, destination.getId(), "30.00"));
        clock.advance(Duration.ofDays(1));

        OperationResponse replay = transferRequest(source.getId(), operationId, destination.getId(), "30.00")
                .expectStatus().isOk()
                .expectHeader().valueEquals("Idempotent-Replayed", "true")
                .expectBody(OperationResponse.class).returnResult().getResponseBody();

        assertThat(replay).isEqualTo(original);
        transferRequest(source.getId(), operationId, destination.getId(), "31.00").expectStatus().isEqualTo(409).expectBody(Void.class);
        transferRequest(destination.getId(), operationId, source.getId(), "30.00").expectStatus().isEqualTo(409).expectBody(Void.class);
        assertMoney(storedBalance(source.getId()), "70.00");
        assertMoney(storedBalance(destination.getId()), "30.00");
        assertThat(movementCount(source.getId()) + movementCount(destination.getId())).isEqualTo(3);
    }
}
