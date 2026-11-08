package com.banking.account_service.application.handler.command;

import com.banking.account_service.application.command.OpenAccountCommand;
import com.banking.account_service.application.model.AccountView;
import com.banking.account_service.application.model.CustomerSnapshot;
import com.banking.account_service.application.model.OperationOutcome;
import com.banking.account_service.application.model.OperationRequest;
import com.banking.account_service.application.model.RecordedOperation;
import com.banking.account_service.application.port.in.OpenAccountUseCase;
import com.banking.account_service.application.port.out.AccountPolicyPort;
import com.banking.account_service.application.port.out.AccountWritePort;
import com.banking.account_service.application.port.out.MovementWritePort;
import com.banking.account_service.application.port.out.OperationLogPort;
import com.banking.account_service.application.port.out.TransactionPort;
import com.banking.account_service.application.support.BusinessCalendar;
import com.banking.account_service.domain.model.Account;
import com.banking.account_service.domain.model.AccountParticipants;
import com.banking.account_service.domain.model.Amounts;
import com.banking.account_service.domain.model.Movement;
import com.banking.account_service.domain.model.OpenedAccount;
import com.banking.account_service.domain.model.OpeningEligibility;
import com.banking.account_service.domain.model.OperationType;
import com.banking.account_service.domain.model.PostingContext;
import com.banking.account_service.domain.model.ProductPolicy;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Opens an account.
 *
 * <p>Order: request shape (participants, amount) without calls, then the primary holder
 * in Customer Service, product and participant rules, the other participants, the credit
 * card when the product needs one, and finally the opening minimum. Only then does a
 * transaction insert the account, its participants and the opening deposit. The partial
 * unique index of PostgreSQL rejects a second active personal savings or checking account
 * even when two openings race.
 */
public class OpenAccountHandler implements OpenAccountUseCase {

    private final AccountWritePort accounts;
    private final MovementWritePort movements;
    private final OperationLogPort operations;
    private final TransactionPort transactions;
    private final AccountPolicyPort policies;
    private final CustomerEligibilityVerifier eligibility;
    private final BusinessCalendar calendar;

    public OpenAccountHandler(
            AccountWritePort accounts,
            MovementWritePort movements,
            OperationLogPort operations,
            TransactionPort transactions,
            AccountPolicyPort policies,
            CustomerEligibilityVerifier eligibility,
            BusinessCalendar calendar
    ) {
        this.accounts = Objects.requireNonNull(accounts);
        this.movements = Objects.requireNonNull(movements);
        this.operations = Objects.requireNonNull(operations);
        this.transactions = Objects.requireNonNull(transactions);
        this.policies = Objects.requireNonNull(policies);
        this.eligibility = Objects.requireNonNull(eligibility);
        this.calendar = Objects.requireNonNull(calendar);
    }

    @Override
    public Mono<AccountView> execute(OpenAccountCommand command) {
        return Mono.fromCallable(() -> Opening.from(command, policies))
                .flatMap(opening -> verify(opening)
                        .map(checked -> open(opening, checked)))
                .flatMap(opened -> transactions.inTransaction(() -> store(opened)))
                .map(AccountView::from);
    }

    /** Calls the external services; nothing is locked or written yet. */
    private Mono<OpeningEligibility> verify(Opening opening) {
        return eligibility.requireActive(opening.participants().primaryHolderId())
                .flatMap(primary -> verifyParticipants(opening, primary)
                        .then(eligibility.openingEligibility(primary, opening.command().product())));
    }

    private Mono<Void> verifyParticipants(Opening opening, CustomerSnapshot primary) {
        if (!opening.command().product().isAllowedFor(primary.type(), primary.profile())) {
            // The domain rejects the product; do not ask about anyone else.
            return Mono.empty();
        }
        opening.participants().requireAllowedFor(primary.type());
        Set<UUID> others = new LinkedHashSet<>(opening.participants().customerIds());
        others.remove(primary.id());
        return eligibility.requireAllActive(others);
    }

    private OpenedAccount open(Opening opening, OpeningEligibility checked) {
        PostingContext context = calendar.posting(UUID.randomUUID(), null);
        return Account.open(UUID.randomUUID(), opening.command().product(), opening.command().currency(), checked,
                opening.participants(), opening.initialDeposit(), opening.policy(), context);
    }

    /** One transaction: account and participants, then the opening deposit when there is one. */
    private Mono<Account> store(OpenedAccount opened) {
        Account account = opened.account();
        Mono<Void> stored = accounts.insert(account);
        return opened.openingMovement()
                .map(movement -> stored.then(recordOpeningDeposit(account, movement)))
                .orElse(stored)
                .thenReturn(account);
    }

    /** The opening deposit is an operation like any other, so its movement has an operation ID. */
    private Mono<Void> recordOpeningDeposit(Account account, Movement movement) {
        OperationRequest request = new OperationRequest(movement.operationId(), OperationType.OPENING_DEPOSIT,
                account.getId(), null, movement.amount(), null);
        PostingContext context = new PostingContext(movement.operationId(), movement.occurredAt(),
                movement.businessDate(), null);
        RecordedOperation recorded = RecordedOperation.of(request, context,
                OperationOutcome.of(account.getCurrency(), null, movement));
        return operations.claim(request)
                .then(movements.append(movement))
                .then(operations.complete(recorded));
    }

    /** Request checks that need no external call. */
    private record Opening(
            OpenAccountCommand command,
            AccountParticipants participants,
            BigDecimal initialDeposit,
            ProductPolicy policy
    ) {

        static Opening from(OpenAccountCommand command, AccountPolicyPort policies) {
            AccountParticipants participants = AccountParticipants.of(
                    command.primaryHolderId(), command.holderIds(), command.signerIds());
            BigDecimal deposit = command.initialDeposit() == null
                    ? Amounts.ZERO
                    : Amounts.nonNegative(command.initialDeposit());
            return new Opening(command, participants, deposit,
                    policies.policyFor(command.product(), command.currency()));
        }
    }
}
