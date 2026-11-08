package com.banking.account_service.domain.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static com.banking.account_service.domain.model.Policies.SAVINGS;
import static com.banking.account_service.domain.model.Policies.money;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductPolicyTest {

    @Test
    void feeStartsAfterTheFreeQuota() {
        assertThat(SAVINGS.feeAfter(4)).isEqualByComparingTo("0");
        assertThat(SAVINGS.feeAfter(5)).isEqualByComparingTo("2.50");
    }

    @Test
    void limitIsReachedAtTheConfiguredCount() {
        assertThat(SAVINGS.limitReached(9)).isFalse();
        assertThat(SAVINGS.limitReached(10)).isTrue();
        assertThat(Policies.CHECKING.limitReached(10_000)).isFalse();
    }

    @Test
    void amountsAreNormalizedToTwoDecimals() {
        ProductPolicy policy = new ProductPolicy(AccountProduct.SAVINGS, CurrencyCode.USD,
                money("1"), 5, money("0.5"), 10, money("0"), null, null);

        assertThat(policy.openingMinimum().scale()).isEqualTo(2);
        assertThat(policy.movementFee()).isEqualTo(money("0.50"));
    }

    @Test
    void rejectsTermsThatContradictTheProduct() {
        assertInvalid(() -> new ProductPolicy(AccountProduct.SAVINGS, CurrencyCode.PEN,
                money("0"), 5, money("1"), null, money("0"), null, null), "requires a monthly movement limit");
        assertInvalid(() -> new ProductPolicy(AccountProduct.SAVINGS, CurrencyCode.PEN,
                money("0"), 5, money("1"), 10, money("5"), null, null), "exempt from the maintenance fee");
        assertInvalid(() -> new ProductPolicy(AccountProduct.CHECKING, CurrencyCode.PEN,
                money("0"), 5, money("1"), 10, money("5"), null, null), "cannot have a monthly movement limit");
        assertInvalid(() -> new ProductPolicy(AccountProduct.SME_CHECKING, CurrencyCode.PEN,
                money("0"), 5, money("1"), null, money("5"), null, null), "exempt from the maintenance fee");
        assertInvalid(() -> new ProductPolicy(AccountProduct.FIXED_TERM, CurrencyCode.PEN,
                money("0"), 1, money("0"), 2, money("0"), null, 15), "exactly one movement per month");
        assertInvalid(() -> new ProductPolicy(AccountProduct.FIXED_TERM, CurrencyCode.PEN,
                money("0"), 1, money("0"), 1, money("0"), null, null), "requires an operation day");
        assertInvalid(() -> new ProductPolicy(AccountProduct.VIP_SAVINGS, CurrencyCode.PEN,
                money("0"), 5, money("1"), 10, money("0"), null, null), "requires a minimum daily average balance");
        assertInvalid(() -> new ProductPolicy(AccountProduct.SAVINGS, CurrencyCode.PEN,
                money("0"), 5, money("1"), 10, money("0"), money("100"), null), "cannot have a minimum");
        assertInvalid(() -> new ProductPolicy(AccountProduct.SAVINGS, CurrencyCode.PEN,
                money("0"), 5, money("0.001"), 10, money("0"), null, null), "at most two decimals");
        assertInvalid(() -> new ProductPolicy(AccountProduct.SAVINGS, CurrencyCode.PEN,
                money("-1"), 5, money("1"), 10, money("0"), null, null), "zero or positive");
    }

    @Test
    void operationDateIsClampedToTheMonthLength() {
        ProductPolicy day31 = new ProductPolicy(AccountProduct.FIXED_TERM, CurrencyCode.PEN,
                money("0"), 1, money("0"), 1, money("0"), null, 31);

        assertThat(day31.operationDateIn(YearMonth.of(2027, 2))).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(day31.operationDateIn(YearMonth.of(2028, 2))).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(Policies.FIXED_TERM.operationDateIn(YearMonth.of(2026, 11))).isEqualTo(LocalDate.of(2026, 11, 15));
    }

    private static void assertInvalid(Runnable construction, String message) {
        assertThatThrownBy(construction::run).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(message);
    }
}
