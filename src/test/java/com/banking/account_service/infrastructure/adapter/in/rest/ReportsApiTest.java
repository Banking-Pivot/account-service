package com.banking.account_service.infrastructure.adapter.in.rest;

import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.DailyAverageBalanceResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.MovementTotals;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.ProductReportLine;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.ProductReportResponse;
import com.banking.account_service.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Journey 4: daily average balance and the report by product. */
class ReportsApiTest extends PostgresIntegrationTest {

    @Nested
    class DailyAverageBalance {

        @Test
        void vipSavingsComparesTheAverageWithTheRequiredMinimum() {
            UUID vip = vipCustomer();
            GATEWAY.giveCreditCard(vip);
            AccountResponse account = open("VIP_SAVINGS", "PEN", vip, "1000.00");      // 16 Nov
            clock.set(Instant.parse("2026-11-20T15:00:00Z"));
            withdraw(account.getId(), "500.00");                                      // 20 Nov
            clock.set(Instant.parse("2026-11-25T15:00:00Z"));

            // 16-19 Nov at 1000 and 20-25 Nov at 500: (4 x 1000 + 6 x 500) / 10
            DailyAverageBalanceResponse current = average(account.getId(), null);
            assertThat(current.getPeriod()).isEqualTo("2026-11");
            assertThat(current.getFromDate()).hasToString("2026-11-16");
            assertThat(current.getToDate()).hasToString("2026-11-25");
            assertThat(current.getDaysCounted()).isEqualTo(10);
            assertMoney(current.getAverageDailyBalance(), "700.00");
            assertMoney(current.getRequiredMinimum(), "1000.00");
            assertThat(current.getMeetsRequirement()).isFalse();

            clock.set(Instant.parse("2026-12-03T15:00:00Z"));
            deposit(account.getId(), "1500.00");                                      // 3 Dec

            // Whole November counts now: 4 x 1000 + 11 x 500 over 15 days, days without movements included.
            DailyAverageBalanceResponse november = average(account.getId(), "2026-11");
            assertThat(november.getToDate()).hasToString("2026-11-30");
            assertThat(november.getDaysCounted()).isEqualTo(15);
            assertMoney(november.getAverageDailyBalance(), "633.33");

            // 1-2 Dec close at 500 (carried from November), 3 Dec at 2000: 3000 / 3
            DailyAverageBalanceResponse december = average(account.getId(), "2026-12");
            assertThat(december.getFromDate()).hasToString("2026-12-01");
            assertMoney(december.getAverageDailyBalance(), "1000.00");
            assertThat(december.getMeetsRequirement()).isTrue();
        }

        @Test
        void otherProductsReportTheAverageWithoutRequirement() {
            AccountResponse account = openSavings(personalCustomer(), "300.00");

            DailyAverageBalanceResponse average = average(account.getId(), "2026-11");

            assertMoney(average.getAverageDailyBalance(), "300.00");
            assertThat(average.getRequiredMinimum()).isNull();
            assertThat(average.getMeetsRequirement()).isNull();
            // The contract declares both fields optional, not nullable: they are left out, not sent as null.
            // jsonPath(...).doesNotExist() also accepts an explicit null, so the raw JSON is checked.
            averageRequest(account.getId(), "2026-11").expectStatus().isOk()
                    .expectBody(String.class)
                    .value(json -> assertThat(json).doesNotContain("requiredMinimum", "meetsRequirement"));
        }

        @Test
        void monthsOutsideTheLifeOfTheAccountAreNotAvailable() {
            AccountResponse account = openSavings(personalCustomer(), "300.00");

            assertRuleViolation(averageRequest(account.getId(), "2026-10"), "PERIOD_NOT_AVAILABLE");
            assertRuleViolation(averageRequest(account.getId(), "2026-12"), "PERIOD_NOT_AVAILABLE");
            averageRequest(account.getId(), "2026-13").expectStatus().isBadRequest().expectBody(Void.class);
            averageRequest(UUID.randomUUID(), "2026-11").expectStatus().isNotFound().expectBody(Void.class);
        }

        private DailyAverageBalanceResponse average(UUID accountId, String period) {
            return averageRequest(accountId, period).expectStatus().isOk()
                    .expectBody(DailyAverageBalanceResponse.class).returnResult().getResponseBody();
        }

        private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec averageRequest(
                UUID accountId, String period) {
            return webTestClient.get()
                    .uri(builder -> builder.path("/accounts/{id}/daily-average-balance")
                            .queryParamIfPresent("period", java.util.Optional.ofNullable(period))
                            .build(accountId))
                    .exchange();
        }
    }

    @Nested
    class ProductReport {

        @Test
        void aggregatesAccountsAndMovementsByProductAndBusinessDate() {
            AccountResponse first = openSavings(personalCustomer(), "100.00");          // OPENING_DEPOSIT 100
            AccountResponse second = openSavings(personalCustomer(), "0");
            AccountResponse closed = open("CHECKING", "PEN", personalCustomer(), "0");
            webTestClient.delete().uri("/accounts/{id}", closed.getId()).header("If-Match", "\"0\"").exchange()
                    .expectStatus().isNoContent();
            deposit(first.getId(), "50.00");                                            // 150
            withdraw(first.getId(), "20.00");                                           // 130
            transfer(first.getId(), second.getId(), "30.00");                           // 100 / 30
            deposit(first.getId(), "1.00");                                             // 101
            deposit(first.getId(), "1.00");                                             // 102
            deposit(first.getId(), "1.00");                                             // 6th movement: 100.50
            clock.set(Instant.parse("2026-12-02T15:00:00Z"));
            deposit(second.getId(), "10.00");                                           // December: 40

            ProductReportResponse november = report("2026-11-01", "2026-11-30");

            assertThat(november.getBusinessZone()).isEqualTo("America/Lima");
            assertThat(november.getProducts()).hasSize(10)
                    .allSatisfy(line -> assertThat(line.getMovements()).hasSize(5));
            ProductReportLine savings = line(november, "SAVINGS", "PEN");
            assertThat(savings.getAccountsOpened()).isEqualTo(2);
            assertThat(savings.getAccountsClosed()).isZero();
            assertThat(savings.getActiveAccountsAtEnd()).isEqualTo(2);
            assertMoney(savings.getClosingBalanceTotal(), "130.50");
            assertMoney(savings.getTotalFees(), "2.50");
            assertTotals(savings, "OPENING_DEPOSIT", 1, "100.00", "0");
            assertTotals(savings, "DEPOSIT", 4, "53.00", "2.50");
            assertTotals(savings, "WITHDRAWAL", 1, "20.00", "0");
            assertTotals(savings, "TRANSFER_OUT", 1, "30.00", "0");
            assertTotals(savings, "TRANSFER_IN", 1, "30.00", "0");
            ProductReportLine checking = line(november, "CHECKING", "PEN");
            assertThat(checking.getAccountsOpened()).isEqualTo(1);
            assertThat(checking.getAccountsClosed()).isEqualTo(1);
            assertThat(checking.getActiveAccountsAtEnd()).isZero();
            ProductReportLine fixedTermUsd = line(november, "FIXED_TERM", "USD");
            assertThat(fixedTermUsd.getAccountsOpened()).isZero();
            assertMoney(fixedTermUsd.getClosingBalanceTotal(), "0");

            ProductReportLine december = line(report("2026-12-01", "2026-12-31"), "SAVINGS", "PEN");
            assertThat(december.getAccountsOpened()).isZero();
            assertThat(december.getActiveAccountsAtEnd()).isEqualTo(2);
            assertMoney(december.getClosingBalanceTotal(), "140.50");
            assertTotals(december, "DEPOSIT", 1, "10.00", "0");
            assertTotals(december, "TRANSFER_OUT", 0, "0", "0");
        }

        @Test
        void aMovementBelongsToTheBusinessDateOfTheBusinessZone() {
            AccountResponse account = openSavings(personalCustomer(), "0");
            clock.set(Instant.parse("2026-12-01T04:59:00Z"));                           // 30 Nov 23:59 in Lima
            deposit(account.getId(), "5.00");
            clock.advance(Duration.ofMinutes(2));                                       // 1 Dec 00:01 in Lima
            deposit(account.getId(), "7.00");

            assertTotals(line(report("2026-11-30", "2026-11-30"), "SAVINGS", "PEN"), "DEPOSIT", 1, "5.00", "0");
            assertTotals(line(report("2026-12-01", "2026-12-01"), "SAVINGS", "PEN"), "DEPOSIT", 1, "7.00", "0");
        }

        @Test
        void rejectsInvalidRanges() {
            webTestClient.get().uri("/accounts/reports/products?from=2026-12-01&to=2026-11-01").exchange()
                    .expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
            webTestClient.get().uri("/accounts/reports/products?from=2026-01-01&to=2027-01-02").exchange()
                    .expectStatus().isBadRequest().expectBody(Void.class);
            webTestClient.get().uri("/accounts/reports/products?from=2026-01-01").exchange()
                    .expectStatus().isBadRequest().expectBody(Void.class);
        }

        private ProductReportResponse report(String from, String to) {
            return webTestClient.get().uri("/accounts/reports/products?from={from}&to={to}", from, to).exchange()
                    .expectStatus().isOk()
                    .expectBody(ProductReportResponse.class).returnResult().getResponseBody();
        }

        private ProductReportLine line(ProductReportResponse report, String product, String currency) {
            return report.getProducts().stream()
                    .filter(line -> line.getProduct().getValue().equals(product)
                            && line.getCurrency().getValue().equals(currency))
                    .findFirst()
                    .orElseThrow();
        }

        private void assertTotals(ProductReportLine line, String type, long count, String amount, String fees) {
            MovementTotals totals = line.getMovements().stream()
                    .filter(movement -> movement.getType().getValue().equals(type))
                    .findFirst()
                    .orElseThrow();
            assertThat(totals.getCount()).as(type + " count").isEqualTo(count);
            assertMoney(totals.getTotalAmount(), amount);
            assertMoney(totals.getTotalFees(), fees);
        }
    }
}
