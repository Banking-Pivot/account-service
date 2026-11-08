package com.banking.account_service.application.handler.query;

import com.banking.account_service.application.exception.AccountNotFoundException;
import com.banking.account_service.application.model.AccountView;
import com.banking.account_service.application.model.DailyAverageBalanceView;
import com.banking.account_service.application.port.in.GetDailyAverageBalanceUseCase;
import com.banking.account_service.application.port.out.AccountPolicyPort;
import com.banking.account_service.application.port.out.AccountReadPort;
import com.banking.account_service.application.port.out.MovementReadPort;
import com.banking.account_service.application.query.GetDailyAverageBalanceQuery;
import com.banking.account_service.application.support.BusinessCalendar;
import com.banking.account_service.domain.exception.AccountRuleViolationException;
import com.banking.account_service.domain.exception.RuleViolation;
import com.banking.account_service.domain.model.Amounts;
import com.banking.account_service.domain.model.DailyAverageBalance;
import com.banking.account_service.domain.model.ProductPolicy;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * Average daily balance of a business month, built from closing balances. Days without
 * movements keep the previous closing balance.
 *
 * <p>The days counted go from the later of the first day of the month and the opening
 * date, to the earliest of the last day of the month, today and the closing date. A
 * future month, or a month the account did not exist in, has no days and answers
 * {@code PERIOD_NOT_AVAILABLE}. For VIP savings it also says whether the average meets the
 * configured minimum.
 */
public class GetDailyAverageBalanceHandler implements GetDailyAverageBalanceUseCase {

    private final AccountReadPort accounts;
    private final MovementReadPort movements;
    private final AccountPolicyPort policies;
    private final BusinessCalendar calendar;

    public GetDailyAverageBalanceHandler(
            AccountReadPort accounts,
            MovementReadPort movements,
            AccountPolicyPort policies,
            BusinessCalendar calendar
    ) {
        this.accounts = Objects.requireNonNull(accounts);
        this.movements = Objects.requireNonNull(movements);
        this.policies = Objects.requireNonNull(policies);
        this.calendar = Objects.requireNonNull(calendar);
    }

    @Override
    public Mono<DailyAverageBalanceView> execute(GetDailyAverageBalanceQuery query) {
        return accounts.findById(query.accountId())
                .switchIfEmpty(Mono.error(() -> new AccountNotFoundException(query.accountId())))
                .flatMap(account -> average(account, query.period()));
    }

    private Mono<DailyAverageBalanceView> average(AccountView account, YearMonth requested) {
        LocalDate today = calendar.today();
        YearMonth period = requested == null ? YearMonth.from(today) : requested;
        LocalDate from = latest(period.atDay(1), calendar.dateOf(account.openedAt()));
        LocalDate to = earliest(period.atEndOfMonth(), today);
        if (account.closedAt() != null) {
            to = earliest(to, calendar.dateOf(account.closedAt()));
        }
        if (from.isAfter(to)) {
            return Mono.error(new AccountRuleViolationException(RuleViolation.PERIOD_NOT_AVAILABLE,
                    "The account has no days to average in " + period + "."));
        }

        LocalDate firstDay = from;
        LocalDate lastDay = to;
        ProductPolicy policy = policies.policyFor(account.product(), account.currency());
        return Mono.zip(
                        movements.closingBalanceBefore(account.id(), firstDay).defaultIfEmpty(Amounts.ZERO),
                        movements.findBetween(account.id(), firstDay, lastDay).collectList())
                .map(inputs -> DailyAverageBalance.compute(firstDay, lastDay, inputs.getT1(), inputs.getT2()))
                .map(result -> toView(account, period, result, policy.minimumDailyAverageBalance()));
    }

    private static DailyAverageBalanceView toView(
            AccountView account,
            YearMonth period,
            DailyAverageBalance result,
            BigDecimal requiredMinimum
    ) {
        Boolean meets = requiredMinimum == null ? null : result.average().compareTo(requiredMinimum) >= 0;
        return new DailyAverageBalanceView(account.id(), account.currency(), period, result.fromDate(),
                result.toDate(), result.daysCounted(), result.average(), requiredMinimum, meets);
    }

    private static LocalDate latest(LocalDate first, LocalDate second) {
        return first.isAfter(second) ? first : second;
    }

    private static LocalDate earliest(LocalDate first, LocalDate second) {
        return first.isBefore(second) ? first : second;
    }
}
