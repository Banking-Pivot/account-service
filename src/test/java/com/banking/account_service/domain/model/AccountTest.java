package com.banking.account_service.domain.model;

import com.banking.account_service.domain.exception.AccountClosedException;
import com.banking.account_service.domain.exception.AccountRuleViolationException;
import com.banking.account_service.domain.exception.RuleViolation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static com.banking.account_service.domain.model.Policies.CHECKING;
import static com.banking.account_service.domain.model.Policies.FIXED_TERM;
import static com.banking.account_service.domain.model.Policies.PERSONAL_STANDARD;
import static com.banking.account_service.domain.model.Policies.SAVINGS;
import static com.banking.account_service.domain.model.Policies.SME_CHECKING;
import static com.banking.account_service.domain.model.Policies.VIP_SAVINGS;
import static com.banking.account_service.domain.model.Policies.money;
import static com.banking.account_service.domain.model.Policies.on;
import static com.banking.account_service.domain.model.Policies.open;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final LocalDate NOV_16 = LocalDate.of(2026, 11, 16);

    @Nested
    class Opening {

        @Test
        void zeroDepositOpensWithoutMovement() {
            OpenedAccount opened = Account.open(UUID.randomUUID(), AccountProduct.SAVINGS, CurrencyCode.PEN,
                    PERSONAL_STANDARD, AccountParticipants.of(UUID.randomUUID(), null, null), money("0"),
                    SAVINGS, on(NOV_16));

            assertThat(opened.openingMovement()).isEmpty();
            assertThat(opened.account().getBalance()).isEqualByComparingTo("0");
            assertThat(opened.account().getMovementSequence()).isZero();
            assertThat(opened.account().getStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(opened.account().getVersion()).isZero();
        }

        @Test
        void positiveDepositIsRecordedAndDoesNotUseTheQuota() {
            OpenedAccount opened = Account.open(UUID.randomUUID(), AccountProduct.SAVINGS, CurrencyCode.PEN,
                    PERSONAL_STANDARD, AccountParticipants.of(UUID.randomUUID(), null, null), money("500"),
                    SAVINGS, on(NOV_16));

            Movement movement = opened.openingMovement().orElseThrow();
            assertThat(movement.type()).isEqualTo(MovementType.OPENING_DEPOSIT);
            assertThat(movement.amount()).isEqualByComparingTo("500");
            assertThat(movement.fee()).isEqualByComparingTo("0");
            assertThat(movement.balanceAfter()).isEqualByComparingTo("500");
            assertThat(movement.sequence()).isEqualTo(1);
            assertThat(opened.account().usageIn(YearMonth.of(2026, 11)).movements()).isZero();
        }

        @Test
        void initialDepositMustReachTheOpeningMinimum() {
            assertViolation(() -> open(FIXED_TERM, PERSONAL_STANDARD, "999.99", NOV_16),
                    RuleViolation.OPENING_MINIMUM_NOT_MET);
            assertThat(open(FIXED_TERM, PERSONAL_STANDARD, "1000", NOV_16).getBalance()).isEqualByComparingTo("1000");
        }

        @Test
        void businessCustomersOnlyOpenCheckingProducts() {
            OpeningEligibility business = new OpeningEligibility(CustomerType.BUSINESS, CustomerProfile.STANDARD, false);

            assertViolation(() -> open(SAVINGS, business, "0", NOV_16), RuleViolation.PRODUCT_NOT_ALLOWED);
            assertViolation(() -> open(FIXED_TERM, business, "1000", NOV_16), RuleViolation.PRODUCT_NOT_ALLOWED);
            assertThat(open(CHECKING, business, "0", NOV_16).getCustomerType()).isEqualTo(CustomerType.BUSINESS);
        }

        @Test
        void vipSavingsNeedsAVipPersonalCustomerWithACard() {
            OpeningEligibility standardWithCard = new OpeningEligibility(CustomerType.PERSONAL, CustomerProfile.STANDARD, true);
            OpeningEligibility vipWithoutCard = new OpeningEligibility(CustomerType.PERSONAL, CustomerProfile.VIP, false);
            OpeningEligibility vipWithCard = new OpeningEligibility(CustomerType.PERSONAL, CustomerProfile.VIP, true);

            assertViolation(() -> open(VIP_SAVINGS, standardWithCard, "1000", NOV_16), RuleViolation.PRODUCT_NOT_ALLOWED);
            assertViolation(() -> open(VIP_SAVINGS, vipWithoutCard, "1000", NOV_16), RuleViolation.CREDIT_CARD_REQUIRED);
            assertThat(open(VIP_SAVINGS, vipWithCard, "1000", NOV_16).getCustomerProfile()).isEqualTo(CustomerProfile.VIP);
        }

        @Test
        void smeCheckingNeedsAnSmeBusinessCustomerWithACard() {
            OpeningEligibility personalSme = new OpeningEligibility(CustomerType.PERSONAL, CustomerProfile.SME, true);
            OpeningEligibility smeWithoutCard = new OpeningEligibility(CustomerType.BUSINESS, CustomerProfile.SME, false);
            OpeningEligibility smeWithCard = new OpeningEligibility(CustomerType.BUSINESS, CustomerProfile.SME, true);

            assertViolation(() -> open(SME_CHECKING, personalSme, "0", NOV_16), RuleViolation.PRODUCT_NOT_ALLOWED);
            assertViolation(() -> open(SME_CHECKING, smeWithoutCard, "0", NOV_16), RuleViolation.CREDIT_CARD_REQUIRED);
            assertThat(open(SME_CHECKING, smeWithCard, "0", NOV_16).getProduct()).isEqualTo(AccountProduct.SME_CHECKING);
        }

        @Test
        void personalAccountsHaveASingleHolder() {
            UUID holder = UUID.randomUUID();
            AccountParticipants twoHolders = AccountParticipants.of(holder, List.of(holder, UUID.randomUUID()), null);

            assertViolation(() -> Account.open(UUID.randomUUID(), AccountProduct.CHECKING, CurrencyCode.PEN,
                    PERSONAL_STANDARD, twoHolders, money("0"), CHECKING, on(NOV_16)), RuleViolation.INVALID_PARTICIPANTS);
        }

        @Test
        void businessAccountsAcceptCoHoldersAndSigners() {
            UUID primary = UUID.randomUUID();
            UUID coHolder = UUID.randomUUID();
            UUID signer = UUID.randomUUID();
            OpeningEligibility business = new OpeningEligibility(CustomerType.BUSINESS, CustomerProfile.STANDARD, false);

            Account account = Account.open(UUID.randomUUID(), AccountProduct.CHECKING, CurrencyCode.PEN, business,
                    AccountParticipants.of(primary, List.of(coHolder, primary), List.of(signer)), money("0"),
                    CHECKING, on(NOV_16)).account();

            assertThat(account.getParticipants().holderIds()).first().isEqualTo(primary);
            assertThat(account.getParticipants().holderIds()).containsExactlyInAnyOrder(primary, coHolder);
            assertThat(account.getParticipants().signerIds()).containsExactly(signer);
        }

        @Test
        void policyOfAnotherProductIsAProgrammingError() {
            assertThatThrownBy(() -> Account.open(UUID.randomUUID(), AccountProduct.SAVINGS, CurrencyCode.PEN,
                    PERSONAL_STANDARD, AccountParticipants.of(UUID.randomUUID(), null, null), money("0"),
                    CHECKING, on(NOV_16))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class FeesAndLimits {

        @Test
        void savingsChargesTheFeeOnlyAfterTheFreeQuota() {
            Account account = open(SAVINGS, PERSONAL_STANDARD, "100", NOV_16);
            for (int i = 1; i <= 5; i++) {
                assertThat(account.deposit(money("10"), SAVINGS, on(NOV_16)).fee()).isEqualByComparingTo("0");
            }

            Movement sixth = account.deposit(money("10"), SAVINGS, on(NOV_16));

            assertThat(sixth.fee()).isEqualByComparingTo("2.50");
            assertThat(sixth.balanceAfter()).isEqualByComparingTo("157.50");
            assertThat(account.getBalance()).isEqualByComparingTo("157.50");
        }

        @Test
        void savingsRejectsMovementsBeyondTheHardLimitWithoutTouchingTheBalance() {
            Account account = open(SAVINGS, PERSONAL_STANDARD, "100", NOV_16);
            for (int i = 1; i <= 10; i++) {
                account.deposit(money("1"), SAVINGS, on(NOV_16));
            }
            long version = account.getVersion();

            assertViolation(() -> account.withdraw(money("1"), SAVINGS, on(NOV_16)),
                    RuleViolation.MONTHLY_MOVEMENT_LIMIT_REACHED);
            assertThat(account.getBalance()).isEqualByComparingTo("97.50");
            assertThat(account.getVersion()).isEqualTo(version);
            assertThat(account.getMovementSequence()).isEqualTo(11);
        }

        @Test
        void aNewBusinessMonthRestartsQuotaAndLimit() {
            Account account = open(SAVINGS, PERSONAL_STANDARD, "100", NOV_16);
            for (int i = 1; i <= 10; i++) {
                account.deposit(money("1"), SAVINGS, on(NOV_16));
            }

            Movement december = account.withdraw(money("1"), SAVINGS, on(LocalDate.of(2026, 12, 1)));

            assertThat(december.fee()).isEqualByComparingTo("0");
            assertThat(account.usageIn(YearMonth.of(2026, 12)).movements()).isEqualTo(1);
            assertThat(december.sequence()).isEqualTo(12);
        }

        @Test
        void checkingHasNoLimitButChargesBeyondTheQuota() {
            Account account = open(CHECKING, PERSONAL_STANDARD, "100", NOV_16);
            for (int i = 1; i <= 20; i++) {
                assertThat(account.deposit(money("1"), CHECKING, on(NOV_16)).fee()).isEqualByComparingTo("0");
            }
            for (int i = 21; i <= 30; i++) {
                assertThat(account.deposit(money("1"), CHECKING, on(NOV_16)).fee()).isEqualByComparingTo("1.00");
            }
            assertThat(account.getBalance()).isEqualByComparingTo("120.00");
        }

        @Test
        void withdrawalMustCoverAmountAndFee() {
            Account account = open(SAVINGS, PERSONAL_STANDARD, "200", NOV_16);
            for (int i = 1; i <= 5; i++) {
                account.deposit(money("1"), SAVINGS, on(NOV_16));
            }

            assertViolation(() -> account.withdraw(money("205"), SAVINGS, on(NOV_16)), RuleViolation.INSUFFICIENT_FUNDS);
            Movement exact = account.withdraw(money("202.50"), SAVINGS, on(NOV_16));

            assertThat(exact.fee()).isEqualByComparingTo("2.50");
            assertThat(account.getBalance()).isEqualByComparingTo("0");
        }

        @Test
        void depositSmallerThanTheFeeOnAnEmptyAccountIsRejected() {
            Account account = open(SAVINGS, PERSONAL_STANDARD, "0", NOV_16);
            for (int i = 1; i <= 5; i++) {
                account.deposit(money("1"), SAVINGS, on(NOV_16));
            }
            account.withdraw(money("2.50"), SAVINGS, on(NOV_16));

            assertViolation(() -> account.deposit(money("2"), SAVINGS, on(NOV_16)), RuleViolation.INSUFFICIENT_FUNDS);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1", "0.001", "1000000000000.00"})
        void amountsMustBePositiveWithTwoDecimalsAndBelowTheMaximum(String amount) {
            Account account = open(SAVINGS, PERSONAL_STANDARD, "100", NOV_16);

            assertViolation(() -> account.deposit(money(amount), SAVINGS, on(NOV_16)), RuleViolation.INVALID_AMOUNT);
        }

        @Test
        void everyChangeIncrementsTheVersion() {
            Account account = open(SAVINGS, PERSONAL_STANDARD, "100", NOV_16);

            account.deposit(money("1"), SAVINGS, on(NOV_16));
            account.withdraw(money("1"), SAVINGS, on(NOV_16));

            assertThat(account.getVersion()).isEqualTo(2);
        }
    }

    @Nested
    class FixedTerm {

        @Test
        void acceptsOnlyTheConfiguredDay() {
            Account account = open(FIXED_TERM, PERSONAL_STANDARD, "1000", NOV_16);

            assertViolation(() -> account.deposit(money("10"), FIXED_TERM, on(LocalDate.of(2026, 12, 14))),
                    RuleViolation.FIXED_TERM_OPERATION_DAY);
            assertThat(account.deposit(money("10"), FIXED_TERM, on(LocalDate.of(2026, 12, 15))).fee())
                    .isEqualByComparingTo("0");
        }

        @Test
        void allowsOneMovementPerMonth() {
            Account account = open(FIXED_TERM, PERSONAL_STANDARD, "1000", NOV_16);
            LocalDate december15 = LocalDate.of(2026, 12, 15);
            account.withdraw(money("100"), FIXED_TERM, on(december15));

            assertViolation(() -> account.deposit(money("100"), FIXED_TERM, on(december15)),
                    RuleViolation.MONTHLY_MOVEMENT_LIMIT_REACHED);
            assertThat(account.deposit(money("100"), FIXED_TERM, on(LocalDate.of(2027, 1, 15))).balanceAfter())
                    .isEqualByComparingTo("1000");
        }

        @Test
        void theDayFallsOnTheLastDayOfShortMonths() {
            ProductPolicy day31 = new ProductPolicy(AccountProduct.FIXED_TERM, CurrencyCode.PEN,
                    money("0"), 1, money("0"), 1, money("0"), null, 31);
            Account account = open(day31, PERSONAL_STANDARD, "100", NOV_16);

            assertThat(account.deposit(money("1"), day31, on(LocalDate.of(2027, 2, 28))).businessDate())
                    .isEqualTo(LocalDate.of(2027, 2, 28));
        }
    }

    @Nested
    class Closing {

        @Test
        void requiresAZeroBalance() {
            Account account = open(SAVINGS, PERSONAL_STANDARD, "100", NOV_16);

            assertViolation(() -> account.close(NOV_16.atStartOfDay().toInstant(java.time.ZoneOffset.UTC)),
                    RuleViolation.ACCOUNT_BALANCE_NOT_ZERO);
            assertThat(account.isActive()).isTrue();
        }

        @Test
        void isLogicalAndRejectsLaterOperations() {
            Account account = open(SAVINGS, PERSONAL_STANDARD, "0", NOV_16);
            java.time.Instant now = java.time.Instant.parse("2026-11-17T15:00:00Z");

            assertThat(account.close(now)).isTrue();
            assertThat(account.close(now)).isFalse();
            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
            assertThat(account.getClosedAt()).isEqualTo(now);
            assertThatThrownBy(() -> account.deposit(money("1"), SAVINGS, on(NOV_16)))
                    .isInstanceOf(AccountClosedException.class);
        }
    }

    @Nested
    class Participants {

        @Test
        void thePrimaryHolderCannotChange() {
            Account account = open(CHECKING, new OpeningEligibility(CustomerType.BUSINESS, CustomerProfile.STANDARD, false),
                    "0", NOV_16);

            assertViolation(() -> account.replaceParticipants(AccountParticipants.of(UUID.randomUUID(), null, null),
                    java.time.Instant.now()), RuleViolation.INVALID_PARTICIPANTS);
        }

        @Test
        void sameParticipantsInAnotherOrderAreNoChange() {
            UUID primary = UUID.randomUUID();
            UUID a = UUID.randomUUID();
            UUID b = UUID.randomUUID();
            Account account = Account.open(UUID.randomUUID(), AccountProduct.CHECKING, CurrencyCode.PEN,
                    new OpeningEligibility(CustomerType.BUSINESS, CustomerProfile.STANDARD, false),
                    AccountParticipants.of(primary, List.of(primary, a, b), null), money("0"), CHECKING,
                    on(NOV_16)).account();

            assertThat(account.replaceParticipants(AccountParticipants.of(primary, List.of(b, primary, a), List.of()),
                    java.time.Instant.now())).isFalse();
            assertThat(account.getVersion()).isZero();
        }
    }

    private static void assertViolation(Runnable action, RuleViolation violation) {
        assertThatThrownBy(action::run)
                .isInstanceOf(AccountRuleViolationException.class)
                .satisfies(error -> assertThat(((AccountRuleViolationException) error).violation()).isEqualTo(violation));
    }
}
