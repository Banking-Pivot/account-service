package com.banking.account_service.infrastructure.adapter.in.rest;

import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountBalanceResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.MovementPageResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.MovementResponse;
import com.banking.account_service.support.PostgresIntegrationTest;
import com.banking.account_service.support.StubGateway;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Journey 1: open an account and read it back, with the eligibility checks against the Gateway. */
class OpenAndQueryAccountApiTest extends PostgresIntegrationTest {

    @Nested
    class Opening {

        @Test
        void opensASavingsAccountAndReadsItBack() {
            UUID customer = personalCustomer();

            AccountResponse opened = openRequest("SAVINGS", "PEN", customer, "500.00")
                    .expectStatus().isCreated()
                    .expectHeader().valueMatches("Location", "/accounts/[0-9a-f-]{36}")
                    .expectHeader().valueEquals("ETag", "\"0\"")
                    .expectBody(AccountResponse.class).returnResult().getResponseBody();

            assertThat(opened.getProduct().getValue()).isEqualTo("SAVINGS");
            assertThat(opened.getType().getValue()).isEqualTo("SAVINGS");
            assertThat(opened.getCustomerType().getValue()).isEqualTo("PERSONAL");
            assertThat(opened.getCustomerProfile().getValue()).isEqualTo("STANDARD");
            assertThat(opened.getHolderIds()).containsExactly(customer);
            assertThat(opened.getAuthorizedSignerIds()).isEmpty();
            assertThat(opened.getStatus().getValue()).isEqualTo("ACTIVE");
            assertMoney(opened.getBalance(), "500.00");
            assertThat(opened.getOpenedAt().toInstant()).isEqualTo(clock.instant());
            assertThat(opened.getClosedAt()).isNull();

            webTestClient.get().uri("/accounts/{id}", opened.getId()).exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("ETag", "\"0\"")
                    .expectBody(AccountResponse.class).isEqualTo(opened);

            MovementPageResponse history = movements(opened.getId());
            assertThat(history.getTotalElements()).isEqualTo(1);
            MovementResponse openingDeposit = history.getItems().get(0);
            assertThat(openingDeposit.getType().getValue()).isEqualTo("OPENING_DEPOSIT");
            assertMoney(openingDeposit.getAmount(), "500.00");
            assertMoney(openingDeposit.getFee(), "0");
            assertMoney(openingDeposit.getBalanceAfter(), "500.00");
            assertThat(openingDeposit.getSequence()).isEqualTo(1);
            assertThat(openingDeposit.getBusinessDate()).hasToString("2026-11-16");

            AccountBalanceResponse balance = balance(opened.getId());
            assertMoney(balance.getBalance(), "500.00");
            assertThat(balance.getMonthlyUsage().getPeriod()).isEqualTo("2026-11");
            assertThat(balance.getMonthlyUsage().getMovementsUsed()).as("opening deposit is free").isZero();
            assertThat(balance.getMonthlyUsage().getFreeMovementsRemaining()).isEqualTo(5);
            assertThat(balance.getMonthlyUsage().getMovementLimit()).isEqualTo(10);
            assertThat(balance.getMonthlyUsage().getMovementsRemaining()).isEqualTo(10);
            assertMoney(balance.getMonthlyUsage().getNextMovementFee(), "0");
            assertMoney(balance.getMonthlyMaintenanceFee(), "0");
            assertThat(balance.getOperationDayOfMonth()).isNull();
        }

        @Test
        void zeroInitialDepositOpensWithoutMovement() {
            AccountResponse opened = open("CHECKING", "USD", personalCustomer(), "0");

            assertMoney(opened.getBalance(), "0");
            assertThat(movements(opened.getId()).getTotalElements()).isZero();
            assertMoney(balance(opened.getId()).getMonthlyMaintenanceFee(), "4.00");
            assertThat(operationCount()).isZero();
        }

        @Test
        void personalCustomerHasOneActiveSavingsAndOneCheckingButManyFixedTerms() {
            UUID customer = vipCustomer();
            GATEWAY.giveCreditCard(customer);
            openSavings(customer, "0");
            open("CHECKING", "PEN", customer, "0");
            open("FIXED_TERM", "PEN", customer, "1000");
            open("FIXED_TERM", "USD", customer, "300");

            for (String product : List.of("SAVINGS", "VIP_SAVINGS", "CHECKING")) {
                String deposit = product.equals("VIP_SAVINGS") ? "1000" : "0";
                openRequest(product, "USD", customer, deposit)
                        .expectStatus().isEqualTo(409)
                        .expectBody().jsonPath("$.code").isEqualTo("PERSONAL_ACCOUNT_ALREADY_EXISTS");
            }
            assertThat(count("SELECT COUNT(*) FROM accounts", Map.of())).isEqualTo(4);
        }

        @Test
        void closingTheSavingsAccountAllowsANewOne() {
            UUID customer = personalCustomer();
            AccountResponse first = openSavings(customer, "0");
            closeAccount(first.getId(), "\"0\"").expectStatus().isNoContent();

            AccountResponse second = openSavings(customer, "0");

            assertThat(second.getId()).isNotEqualTo(first.getId());
        }

        @Test
        void businessCustomerOpensSeveralCheckingAccountsWithCoHoldersAndSigners() {
            UUID company = businessCustomer("STANDARD");
            UUID partner = personalCustomer();
            UUID signer = personalCustomer();

            AccountResponse first = openBusiness(company, List.of(partner, company), List.of(signer))
                    .expectStatus().isCreated()
                    .expectBody(AccountResponse.class).returnResult().getResponseBody();
            open("CHECKING", "PEN", company, "0");

            assertThat(first.getHolderIds()).containsExactly(company, partner);
            assertThat(first.getAuthorizedSignerIds()).containsExactly(signer);
            webTestClient.get().uri("/accounts?customerId={id}", partner).exchange()
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.totalElements").isEqualTo(1)
                    .jsonPath("$.items[0].id").isEqualTo(first.getId().toString());
            webTestClient.get().uri("/accounts?customerId={id}&product=CHECKING&status=ACTIVE", company).exchange()
                    .expectStatus().isOk()
                    .expectBody().jsonPath("$.totalElements").isEqualTo(2);
        }

        @Test
        void personalAccountRejectsCoHoldersAndSigners() {
            UUID customer = personalCustomer();

            assertRuleViolation(openBusiness(customer, List.of(customer, personalCustomer()), List.of()),
                    "INVALID_PARTICIPANTS");
            assertRuleViolation(openBusiness(customer, List.of(), List.of(personalCustomer())),
                    "INVALID_PARTICIPANTS");
        }

        @Test
        void primaryHolderMustBeAmongTheHolders() {
            UUID company = businessCustomer("STANDARD");

            assertRuleViolation(openBusiness(company, List.of(personalCustomer()), List.of()), "INVALID_PARTICIPANTS");
        }

        @Test
        void initialDepositMustReachTheOpeningMinimum() {
            assertRuleViolation(openRequest("FIXED_TERM", "PEN", personalCustomer(), "999.99"),
                    "OPENING_MINIMUM_NOT_MET");
        }

        @Test
        void malformedRequestsAreRejectedBeforeAnyCall() {
            UUID customer = personalCustomer();
            Map<String, Object> unknownField = new LinkedHashMap<>(Map.of("product", "SAVINGS", "currency", "PEN",
                    "primaryHolderId", customer, "initialDeposit", 0, "balance", 100));

            post("/accounts", unknownField).expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
            post("/accounts", Map.of("product", "SAVINGS", "currency", "PEN", "primaryHolderId", customer,
                    "initialDeposit", 1.005)).expectStatus().isBadRequest().expectBody(Void.class);
            post("/accounts", Map.of("product", "SAVINGS", "currency", "EUR", "primaryHolderId", customer,
                    "initialDeposit", 0)).expectStatus().isBadRequest().expectBody(Void.class);
            webTestClient.post().uri("/accounts").contentType(MediaType.TEXT_PLAIN).bodyValue("x").exchange()
                    .expectStatus().isEqualTo(415).expectBody(Void.class);
            assertThat(GATEWAY.calls(StubGateway.CUSTOMERS)).isZero();
        }
    }

    @Nested
    class Eligibility {

        @Test
        void unknownOrInactiveCustomersCannotOpen() {
            UUID inactive = GATEWAY.addCustomer("PERSONAL", "STANDARD", false);

            assertRuleViolation(openRequest("SAVINGS", "PEN", UUID.randomUUID(), "0"), "CUSTOMER_NOT_FOUND");
            assertRuleViolation(openRequest("SAVINGS", "PEN", inactive, "0"), "CUSTOMER_INACTIVE");
            assertThat(count("SELECT COUNT(*) FROM accounts", Map.of())).isZero();
        }

        @Test
        void everyHolderAndSignerMustBeActive() {
            UUID company = businessCustomer("STANDARD");
            UUID inactiveSigner = GATEWAY.addCustomer("PERSONAL", "STANDARD", false);

            assertRuleViolation(openBusiness(company, List.of(company), List.of(inactiveSigner)), "CUSTOMER_INACTIVE");
            assertRuleViolation(openBusiness(company, List.of(company, UUID.randomUUID()), List.of()),
                    "CUSTOMER_NOT_FOUND");
        }

        @Test
        void businessCustomersCannotOpenSavingsOrFixedTerm() {
            UUID company = businessCustomer("STANDARD");

            assertRuleViolation(openRequest("SAVINGS", "PEN", company, "0"), "PRODUCT_NOT_ALLOWED");
            assertRuleViolation(openRequest("FIXED_TERM", "PEN", company, "1000"), "PRODUCT_NOT_ALLOWED");
        }

        @Test
        void vipSavingsRequiresTheVipProfileAndAnActiveCard() {
            UUID standard = personalCustomer();
            GATEWAY.giveCreditCard(standard);
            UUID vipWithoutCard = vipCustomer();
            UUID vip = vipCustomer();
            GATEWAY.giveCreditCard(vip);

            assertRuleViolation(openRequest("VIP_SAVINGS", "PEN", standard, "1000"), "PRODUCT_NOT_ALLOWED");
            assertThat(GATEWAY.calls(StubGateway.CREDITS)).as("profile fails before asking Credit").isZero();
            assertRuleViolation(openRequest("VIP_SAVINGS", "PEN", vipWithoutCard, "1000"), "CREDIT_CARD_REQUIRED");
            assertRuleViolation(openRequest("VIP_SAVINGS", "PEN", vip, "999.99"), "OPENING_MINIMUM_NOT_MET");

            AccountResponse opened = open("VIP_SAVINGS", "PEN", vip, "1000");

            assertThat(opened.getType().getValue()).isEqualTo("SAVINGS");
            assertThat(opened.getCustomerProfile().getValue()).isEqualTo("VIP");
        }

        @Test
        void smeCheckingRequiresTheSmeProfileAndAnActiveCard() {
            UUID standardCompany = businessCustomer("STANDARD");
            GATEWAY.giveCreditCard(standardCompany);
            UUID smeWithoutCard = businessCustomer("SME");
            UUID sme = businessCustomer("SME");
            GATEWAY.giveCreditCard(sme);

            assertRuleViolation(openRequest("SME_CHECKING", "PEN", standardCompany, "0"), "PRODUCT_NOT_ALLOWED");
            assertRuleViolation(openRequest("SME_CHECKING", "PEN", smeWithoutCard, "0"), "CREDIT_CARD_REQUIRED");

            AccountResponse opened = open("SME_CHECKING", "PEN", sme, "0");

            assertMoney(balance(opened.getId()).getMonthlyMaintenanceFee(), "0");
            assertThat(balance(opened.getId()).getMonthlyUsage().getMovementLimit()).isNull();
        }

        @Test
        void productsWithoutCardRequirementDoNotCallCreditService() {
            open("CHECKING", "PEN", personalCustomer(), "0");

            assertThat(GATEWAY.calls(StubGateway.CREDITS)).isZero();
        }
    }

    @Nested
    class UnavailableDependencies {

        @Test
        void customerServiceErrorIsUnavailableNotIneligible() {
            GATEWAY.fail(StubGateway.CUSTOMERS, 500);

            assertUnavailable(openRequest("SAVINGS", "PEN", UUID.randomUUID(), "0"));
        }

        @Test
        void slowCustomerServiceTimesOutAfterTwoSeconds() {
            UUID customer = personalCustomer();
            GATEWAY.delay(StubGateway.CUSTOMERS, Duration.ofSeconds(4));
            long start = System.nanoTime();

            assertUnavailable(openRequest("SAVINGS", "PEN", customer, "0"));

            assertThat(Duration.ofNanos(System.nanoTime() - start)).isBetween(Duration.ofMillis(1900), Duration.ofMillis(3900));
        }

        @Test
        void missingGatewayRouteIsUnavailableNotCustomerNotFound() {
            GATEWAY.misroute(StubGateway.CUSTOMERS);

            assertUnavailable(openRequest("SAVINGS", "PEN", UUID.randomUUID(), "0"));
        }

        @Test
        void creditServiceFailureIsUnavailableNotCardRequired() {
            UUID vip = vipCustomer();
            GATEWAY.giveCreditCard(vip);
            GATEWAY.fail(StubGateway.CREDITS, 502);

            assertUnavailable(openRequest("VIP_SAVINGS", "PEN", vip, "1000"));
        }

        @Test
        void openCircuitAnswersWithoutCallingTheGateway() {
            GATEWAY.fail(StubGateway.CUSTOMERS, 503);
            for (int i = 0; i < 5; i++) {
                assertUnavailable(openRequest("SAVINGS", "PEN", UUID.randomUUID(), "0"));
            }
            assertThat(GATEWAY.calls(StubGateway.CUSTOMERS)).isEqualTo(5);

            assertUnavailable(openRequest("SAVINGS", "PEN", UUID.randomUUID(), "0"));

            assertThat(GATEWAY.calls(StubGateway.CUSTOMERS)).as("circuit open").isEqualTo(5);
        }

        private void assertUnavailable(org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec response) {
            response.expectStatus().isEqualTo(503)
                    .expectBody().jsonPath("$.code").isEqualTo("DEPENDENCY_UNAVAILABLE");
            assertThat(count("SELECT COUNT(*) FROM accounts", Map.of())).isZero();
        }
    }

    @Nested
    class UpdateAndClose {

        @Test
        void replacesParticipantsWithTheCurrentRevision() {
            UUID company = businessCustomer("STANDARD");
            UUID partner = personalCustomer();
            UUID signer = personalCustomer();
            AccountResponse opened = open("CHECKING", "PEN", company, "0");

            AccountResponse updated = update(opened.getId(), "\"0\"", List.of(company, partner), List.of(signer))
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("ETag", "\"1\"")
                    .expectBody(AccountResponse.class).returnResult().getResponseBody();

            assertThat(updated.getHolderIds()).containsExactly(company, partner);
            assertThat(updated.getAuthorizedSignerIds()).containsExactly(signer);
            update(opened.getId(), "\"0\"", List.of(company), List.of()).expectStatus().isEqualTo(412)
                    .expectBody().jsonPath("$.code").isEqualTo("ACCOUNT_VERSION_CONFLICT");
            update(opened.getId(), "\"1\"", List.of(partner, company), List.of(signer)).expectStatus().isOk()
                    .expectHeader().valueEquals("ETag", "\"1\"")
                    .expectBody(Void.class);
            update(opened.getId(), "\"1\"", List.of(company), List.of()).expectStatus().isOk()
                    .expectHeader().valueEquals("ETag", "\"2\"")
                    .expectBody(Void.class);
            assertThat(count("SELECT COUNT(*) FROM account_holders WHERE account_id = :id",
                    Map.of("id", opened.getId()))).isEqualTo(1);
            assertThat(count("SELECT COUNT(*) FROM account_signers WHERE account_id = :id",
                    Map.of("id", opened.getId()))).isZero();
        }

        @Test
        void newParticipantsMustBeActiveCustomers() {
            UUID company = businessCustomer("STANDARD");
            AccountResponse opened = open("CHECKING", "PEN", company, "0");
            UUID inactive = GATEWAY.addCustomer("PERSONAL", "STANDARD", false);

            assertRuleViolation(update(opened.getId(), "\"0\"", List.of(company, inactive), List.of()),
                    "CUSTOMER_INACTIVE");
            assertRuleViolation(update(opened.getId(), "\"0\"", List.of(personalCustomer()), List.of()),
                    "INVALID_PARTICIPANTS");
        }

        @Test
        void updateNeedsIfMatch() {
            UUID company = businessCustomer("STANDARD");
            AccountResponse opened = open("CHECKING", "PEN", company, "0");

            webTestClient.put().uri("/accounts/{id}", opened.getId()).contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("holderIds", List.of(company), "authorizedSignerIds", List.of()))
                    .exchange()
                    .expectStatus().isEqualTo(428).expectBody(Void.class);
        }

        @Test
        void monetaryOperationsChangeTheRevision() {
            UUID company = businessCustomer("STANDARD");
            AccountResponse opened = open("CHECKING", "PEN", company, "0");
            deposit(opened.getId(), "10.00");

            update(opened.getId(), "\"0\"", List.of(company), List.of()).expectStatus().isEqualTo(412).expectBody(Void.class);
            webTestClient.get().uri("/accounts/{id}", opened.getId()).exchange()
                    .expectHeader().valueEquals("ETag", "\"1\"")
                    .expectBody(Void.class);
        }

        @Test
        void closingRequiresZeroBalanceAndKeepsTheHistory() {
            AccountResponse opened = openSavings(personalCustomer(), "100.00");

            assertRuleViolation(closeAccount(opened.getId(), "\"0\""), "ACCOUNT_BALANCE_NOT_ZERO");
            withdraw(opened.getId(), "100.00");
            closeAccount(opened.getId(), "\"0\"").expectStatus().isEqualTo(412).expectBody(Void.class);
            closeAccount(opened.getId(), "\"1\"").expectStatus().isNoContent();

            AccountResponse closed = webTestClient.get().uri("/accounts/{id}", opened.getId()).exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals("ETag", "\"2\"")
                    .expectBody(AccountResponse.class).returnResult().getResponseBody();
            assertThat(closed.getStatus().getValue()).isEqualTo("CLOSED");
            assertThat(closed.getClosedAt()).isNotNull();
            assertThat(movements(opened.getId()).getTotalElements()).isEqualTo(2);
            closeAccount(opened.getId(), "\"2\"").expectStatus().isNoContent();
            depositRequest(opened.getId(), UUID.randomUUID(), "1.00").expectStatus().isEqualTo(409)
                    .expectBody().jsonPath("$.code").isEqualTo("ACCOUNT_CLOSED");
            update(opened.getId(), "\"2\"", List.of(closed.getPrimaryHolderId()), List.of())
                    .expectStatus().isEqualTo(409).expectBody(Void.class);
        }

        @Test
        void unknownAccountsAreNotFound() {
            UUID missing = UUID.randomUUID();

            webTestClient.get().uri("/accounts/{id}", missing).exchange().expectStatus().isNotFound()
                    .expectBody().jsonPath("$.code").isEqualTo("ACCOUNT_NOT_FOUND");
            webTestClient.get().uri("/accounts/{id}/movements", missing).exchange().expectStatus().isNotFound().expectBody(Void.class);
            webTestClient.get().uri("/accounts/{id}/balance", missing).exchange().expectStatus().isNotFound().expectBody(Void.class);
            closeAccount(missing, "\"0\"").expectStatus().isNotFound().expectBody(Void.class);
            webTestClient.get().uri("/accounts/not-a-uuid").exchange().expectStatus().isBadRequest().expectBody(Void.class);
        }
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec openBusiness(
            UUID primary, List<UUID> holders, List<UUID> signers) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("product", "CHECKING");
        body.put("currency", "PEN");
        body.put("primaryHolderId", primary);
        body.put("holderIds", holders);
        body.put("authorizedSignerIds", signers);
        body.put("initialDeposit", 0);
        return post("/accounts", body);
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec update(
            UUID accountId, String ifMatch, List<UUID> holders, List<UUID> signers) {
        return webTestClient.put().uri("/accounts/{id}", accountId)
                .header("If-Match", ifMatch)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("holderIds", holders, "authorizedSignerIds", signers))
                .exchange();
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec closeAccount(UUID accountId, String ifMatch) {
        return webTestClient.delete().uri("/accounts/{id}", accountId).header("If-Match", ifMatch).exchange();
    }
}
