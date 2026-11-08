package com.banking.account_service.domain.model;

import com.banking.account_service.domain.exception.AccountClosedException;
import com.banking.account_service.domain.exception.AccountRuleViolationException;
import com.banking.account_service.domain.exception.RuleViolation;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Bank account aggregate. It owns the balance and the monthly counters and is the only
 * place that changes them: every change goes through a monetary operation that returns
 * the {@link Movement} to record.
 *
 * <p>Every change increments {@code version}, which is also the HTTP ETag. The caller
 * loads the account under a row lock, applies one operation and stores the result
 * conditioned on the version it loaded.
 */
@Getter
public final class Account {

    private final UUID id;
    private final AccountProduct product;
    private final CurrencyCode currency;
    private final CustomerType customerType;
    private final CustomerProfile customerProfile;
    private final Instant openedAt;
    private AccountParticipants participants;
    private BigDecimal balance;
    private AccountStatus status;
    private MonthlyUsage usage;
    private long movementSequence;
    private Instant updatedAt;
    private Instant closedAt;
    private long version;

    private Account(
            UUID id,
            AccountProduct product,
            CurrencyCode currency,
            CustomerType customerType,
            CustomerProfile customerProfile,
            AccountParticipants participants,
            BigDecimal balance,
            AccountStatus status,
            MonthlyUsage usage,
            long movementSequence,
            Instant openedAt,
            Instant updatedAt,
            Instant closedAt,
            long version
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.product = Objects.requireNonNull(product, "product");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.customerType = Objects.requireNonNull(customerType, "customerType");
        this.customerProfile = Objects.requireNonNull(customerProfile, "customerProfile");
        this.participants = Objects.requireNonNull(participants, "participants");
        this.balance = Objects.requireNonNull(balance, "balance");
        this.status = Objects.requireNonNull(status, "status");
        this.usage = Objects.requireNonNull(usage, "usage");
        this.movementSequence = movementSequence;
        this.openedAt = Objects.requireNonNull(openedAt, "openedAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.closedAt = closedAt;
        this.version = version;
    }

    /**
     * Opens an ACTIVE account after the eligibility checks.
     *
     * <p>The product must accept the customer type and profile, VIP savings and SME checking
     * need an active credit card, the participants must fit the customer type and the
     * initial deposit must reach the opening minimum. A positive initial deposit becomes
     * the first movement and does not use the monthly quota.
     */
    public static OpenedAccount open(
            UUID id,
            AccountProduct product,
            CurrencyCode currency,
            OpeningEligibility eligibility,
            AccountParticipants participants,
            BigDecimal initialDeposit,
            ProductPolicy policy,
            PostingContext context
    ) {
        requireMatchingPolicy(policy, product, currency);
        if (!product.isAllowedFor(eligibility.customerType(), eligibility.customerProfile())) {
            throw new AccountRuleViolationException(RuleViolation.PRODUCT_NOT_ALLOWED,
                    product + " is not available for " + eligibility.customerType() + " customers with the "
                            + eligibility.customerProfile() + " profile.");
        }
        if (product.requiresCreditCard() && !eligibility.hasActiveCreditCard()) {
            throw new AccountRuleViolationException(RuleViolation.CREDIT_CARD_REQUIRED,
                    product + " requires an active credit card with the bank.");
        }
        participants.requireAllowedFor(eligibility.customerType());

        BigDecimal deposit = Amounts.nonNegative(initialDeposit);
        if (deposit.compareTo(policy.openingMinimum()) < 0) {
            throw new AccountRuleViolationException(RuleViolation.OPENING_MINIMUM_NOT_MET,
                    "The initial deposit must be at least " + policy.openingMinimum() + " " + currency + ".");
        }

        long sequence = deposit.signum() > 0 ? 1 : 0;
        Account account = new Account(id, product, currency, eligibility.customerType(),
                eligibility.customerProfile(), participants, deposit, AccountStatus.ACTIVE,
                MonthlyUsage.startOf(YearMonth.from(context.businessDate())), sequence,
                context.occurredAt(), context.occurredAt(), null, 0);

        Optional<Movement> openingMovement = sequence == 0 ? Optional.empty() : Optional.of(new Movement(
                UUID.randomUUID(), id, context.operationId(), sequence, MovementType.OPENING_DEPOSIT,
                deposit, Amounts.ZERO, deposit, null, null, context.occurredAt(), context.businessDate()));
        return new OpenedAccount(account, openingMovement);
    }

    /** Rebuilds a stored account without applying business rules. */
    public static Account restore(
            UUID id,
            AccountProduct product,
            CurrencyCode currency,
            CustomerType customerType,
            CustomerProfile customerProfile,
            AccountParticipants participants,
            BigDecimal balance,
            AccountStatus status,
            MonthlyUsage usage,
            long movementSequence,
            Instant openedAt,
            Instant updatedAt,
            Instant closedAt,
            long version
    ) {
        return new Account(id, product, currency, customerType, customerProfile, participants, balance,
                status, usage, movementSequence, openedAt, updatedAt, closedAt, version);
    }

    /** Whether the account accepts operations. */
    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /** Primary holder, the customer that owns the account. */
    public UUID primaryHolderId() {
        return participants.primaryHolderId();
    }

    /** Monthly usage as seen from the given business month. */
    public MonthlyUsage usageIn(YearMonth period) {
        return usage.asOf(period);
    }

    /** Credits a deposit, minus the movement fee when the free quota is used up. */
    public Movement deposit(BigDecimal amount, ProductPolicy policy, PostingContext context) {
        return post(MovementType.DEPOSIT, amount, null, policy, context);
    }

    /** Debits a withdrawal plus the movement fee when the free quota is used up. */
    public Movement withdraw(BigDecimal amount, ProductPolicy policy, PostingContext context) {
        return post(MovementType.WITHDRAWAL, amount, null, policy, context);
    }

    /** Debits the source leg of a transfer under this account's policy. */
    public Movement transferOut(BigDecimal amount, UUID destinationAccountId, ProductPolicy policy, PostingContext context) {
        return post(MovementType.TRANSFER_OUT, amount, destinationAccountId, policy, context);
    }

    /** Credits the destination leg of a transfer under this account's policy. */
    public Movement transferIn(BigDecimal amount, UUID sourceAccountId, ProductPolicy policy, PostingContext context) {
        return post(MovementType.TRANSFER_IN, amount, sourceAccountId, policy, context);
    }

    /**
     * Replaces holders and signers. The primary holder cannot change.
     *
     * @return {@code false} when the participants are already the requested ones
     */
    public boolean replaceParticipants(AccountParticipants next, Instant now) {
        requireActive();
        if (!next.primaryHolderId().equals(participants.primaryHolderId())) {
            throw new AccountRuleViolationException(RuleViolation.INVALID_PARTICIPANTS,
                    "The primary holder cannot change.");
        }
        next.requireAllowedFor(customerType);
        if (next.equals(participants)) {
            return false;
        }
        participants = next;
        touch(now);
        return true;
    }

    /**
     * Closes the account logically. Requires a zero balance.
     *
     * @return {@code false} when the account was already closed
     */
    public boolean close(Instant now) {
        if (!isActive()) {
            return false;
        }
        if (balance.signum() != 0) {
            throw new AccountRuleViolationException(RuleViolation.ACCOUNT_BALANCE_NOT_ZERO,
                    "Only accounts with a zero balance can be closed.");
        }
        status = AccountStatus.CLOSED;
        closedAt = now;
        touch(now);
        return true;
    }

    /**
     * Applies one counted movement: checks status, amount, fixed-term day and monthly limit,
     * charges the fee when the free quota is used up and keeps the balance non-negative.
     */
    private Movement post(
            MovementType type,
            BigDecimal requestedAmount,
            UUID counterpartyAccountId,
            ProductPolicy policy,
            PostingContext context
    ) {
        requireMatchingPolicy(policy, product, currency);
        requireActive();
        BigDecimal amount = Amounts.positive(requestedAmount);
        YearMonth period = YearMonth.from(context.businessDate());

        if (product == AccountProduct.FIXED_TERM) {
            LocalDate allowed = policy.operationDateIn(period);
            if (!context.businessDate().equals(allowed)) {
                throw new AccountRuleViolationException(RuleViolation.FIXED_TERM_OPERATION_DAY,
                        "Fixed-term accounts accept movements only on " + allowed + " this month.");
            }
        }

        MonthlyUsage current = usage.asOf(period);
        if (policy.limitReached(current.movements())) {
            throw new AccountRuleViolationException(RuleViolation.MONTHLY_MOVEMENT_LIMIT_REACHED,
                    "The account reached its limit of " + policy.monthlyMovementLimit()
                            + " movements for " + period + ".");
        }

        BigDecimal fee = policy.feeAfter(current.movements());
        BigDecimal newBalance = type.isCredit()
                ? balance.add(amount).subtract(fee)
                : balance.subtract(amount).subtract(fee);
        if (newBalance.signum() < 0) {
            throw new AccountRuleViolationException(RuleViolation.INSUFFICIENT_FUNDS,
                    type.isCredit()
                            ? "The balance does not cover the movement fee."
                            : "The balance does not cover the amount and the fee.");
        }
        if (newBalance.compareTo(Amounts.MAX_BALANCE) > 0) {
            throw new AccountRuleViolationException(RuleViolation.INVALID_AMOUNT,
                    "The balance would exceed the maximum the account can hold.");
        }

        balance = newBalance;
        usage = current.plusOne();
        movementSequence++;
        touch(context.occurredAt());
        return new Movement(UUID.randomUUID(), id, context.operationId(), movementSequence, type, amount, fee,
                newBalance, counterpartyAccountId, context.description(), context.occurredAt(),
                context.businessDate());
    }

    private void requireActive() {
        if (!isActive()) {
            throw new AccountClosedException(id);
        }
    }

    private void touch(Instant now) {
        updatedAt = now.isAfter(updatedAt) ? now : updatedAt;
        version++;
    }

    private static void requireMatchingPolicy(ProductPolicy policy, AccountProduct product, CurrencyCode currency) {
        if (policy.product() != product || policy.currency() != currency) {
            throw new IllegalArgumentException("Policy " + policy.product() + "/" + policy.currency()
                    + " does not apply to " + product + "/" + currency + ".");
        }
    }
}
