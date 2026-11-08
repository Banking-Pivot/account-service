package com.banking.account_service.application.handler.query;

import com.banking.account_service.application.exception.AccountNotFoundException;
import com.banking.account_service.application.model.AccountBalanceView;
import com.banking.account_service.application.model.AccountView;
import com.banking.account_service.application.model.MonthlyUsageView;
import com.banking.account_service.application.port.in.GetAccountBalanceUseCase;
import com.banking.account_service.application.port.out.AccountPolicyPort;
import com.banking.account_service.application.port.out.AccountReadPort;
import com.banking.account_service.application.query.GetAccountBalanceQuery;
import com.banking.account_service.application.support.BusinessCalendar;
import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.AccountStatus;
import com.banking.account_service.domain.model.MonthlyUsage;
import com.banking.account_service.domain.model.ProductPolicy;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Balance with the usage of the current business month. A counter stored in an earlier
 * month reads as zero: the month changed and no operation has happened yet.
 */
public class GetAccountBalanceHandler implements GetAccountBalanceUseCase {

    private final AccountReadPort accounts;
    private final AccountPolicyPort policies;
    private final BusinessCalendar calendar;

    public GetAccountBalanceHandler(AccountReadPort accounts, AccountPolicyPort policies, BusinessCalendar calendar) {
        this.accounts = Objects.requireNonNull(accounts);
        this.policies = Objects.requireNonNull(policies);
        this.calendar = Objects.requireNonNull(calendar);
    }

    @Override
    public Mono<AccountBalanceView> execute(GetAccountBalanceQuery query) {
        return accounts.findById(query.accountId())
                .switchIfEmpty(Mono.error(() -> new AccountNotFoundException(query.accountId())))
                .map(this::toBalance);
    }

    private AccountBalanceView toBalance(AccountView account) {
        ProductPolicy policy = policies.policyFor(account.product(), account.currency());
        LocalDate today = calendar.today();
        MonthlyUsage usage = account.usage().asOf(YearMonth.from(today));
        return new AccountBalanceView(
                account.id(),
                account.product(),
                account.currency(),
                account.balance(),
                account.status(),
                calendar.now(),
                MonthlyUsageView.of(usage, policy),
                policy.monthlyMaintenanceFee(),
                policy.operationDayOfMonth(),
                nextOperationDate(account, policy, usage, today));
    }

    /**
     * Fixed term only: this month's operation day while it has not passed and has not been
     * used, otherwise next month's. {@code null} for other products and closed accounts.
     */
    static LocalDate nextOperationDate(AccountView account, ProductPolicy policy, MonthlyUsage usage, LocalDate today) {
        if (account.product() != AccountProduct.FIXED_TERM || account.status() != AccountStatus.ACTIVE) {
            return null;
        }
        YearMonth month = YearMonth.from(today);
        LocalDate thisMonth = policy.operationDateIn(month);
        boolean available = !today.isAfter(thisMonth) && !policy.limitReached(usage.movements());
        return available ? thisMonth : policy.operationDateIn(month.plusMonths(1));
    }
}
