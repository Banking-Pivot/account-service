package com.banking.account_service.infrastructure.adapter.in.rest;

import com.banking.account_service.application.command.CloseAccountCommand;
import com.banking.account_service.application.exception.AccountConcurrentModificationException;
import com.banking.account_service.application.model.AccountView;
import com.banking.account_service.application.port.in.CloseAccountUseCase;
import com.banking.account_service.application.port.in.GetAccountBalanceUseCase;
import com.banking.account_service.application.port.in.GetAccountUseCase;
import com.banking.account_service.application.port.in.GetDailyAverageBalanceUseCase;
import com.banking.account_service.application.port.in.ListAccountMovementsUseCase;
import com.banking.account_service.application.port.in.ListAccountsUseCase;
import com.banking.account_service.application.port.in.OpenAccountUseCase;
import com.banking.account_service.application.port.in.UpdateAccountUseCase;
import com.banking.account_service.application.query.GetAccountBalanceQuery;
import com.banking.account_service.application.query.GetAccountQuery;
import com.banking.account_service.application.query.GetDailyAverageBalanceQuery;
import com.banking.account_service.application.query.ListAccountMovementsQuery;
import com.banking.account_service.application.query.PageRequest;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.api.AccountsApiDelegate;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountBalanceResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountPageResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountProduct;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountStatus;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.DailyAverageBalanceResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.MovementPageResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.OpenAccountRequest;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.UpdateAccountRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Implements the contract-generated delegate for account commands and queries. The
 * generated {@code AccountsApiController} receives HTTP requests, validates parameters and
 * calls these methods; this class only translates and chooses status codes.
 */
@Component
public class AccountsApiDelegateImpl implements AccountsApiDelegate {

    private final OpenAccountUseCase openAccount;
    private final UpdateAccountUseCase updateAccount;
    private final CloseAccountUseCase closeAccount;
    private final GetAccountUseCase getAccount;
    private final ListAccountsUseCase listAccounts;
    private final GetAccountBalanceUseCase getAccountBalance;
    private final ListAccountMovementsUseCase listAccountMovements;
    private final GetDailyAverageBalanceUseCase getDailyAverageBalance;
    private final AccountRestMapper mapper;

    public AccountsApiDelegateImpl(
            OpenAccountUseCase openAccount,
            UpdateAccountUseCase updateAccount,
            CloseAccountUseCase closeAccount,
            GetAccountUseCase getAccount,
            ListAccountsUseCase listAccounts,
            GetAccountBalanceUseCase getAccountBalance,
            ListAccountMovementsUseCase listAccountMovements,
            GetDailyAverageBalanceUseCase getDailyAverageBalance,
            AccountRestMapper mapper
    ) {
        this.openAccount = openAccount;
        this.updateAccount = updateAccount;
        this.closeAccount = closeAccount;
        this.getAccount = getAccount;
        this.listAccounts = listAccounts;
        this.getAccountBalance = getAccountBalance;
        this.listAccountMovements = listAccountMovements;
        this.getDailyAverageBalance = getDailyAverageBalance;
        this.mapper = mapper;
    }

    @Override
    public Mono<ResponseEntity<AccountResponse>> openAccount(
            Mono<OpenAccountRequest> request,
            ServerWebExchange exchange
    ) {
        return request
                .map(mapper::toCommand)
                .flatMap(openAccount::execute)
                .map(view -> ResponseEntity
                        .created(URI.create("/accounts/" + view.id()))
                        .eTag(mapper.toETag(view.version()))
                        .body(mapper.toResponse(view)));
    }

    @Override
    public Mono<ResponseEntity<AccountResponse>> getAccount(UUID accountId, ServerWebExchange exchange) {
        return getAccount.execute(new GetAccountQuery(accountId)).map(this::okWithETag);
    }

    @Override
    public Mono<ResponseEntity<AccountPageResponse>> listAccounts(
            Integer page,
            Integer size,
            UUID customerId,
            AccountProduct product,
            AccountStatus status,
            ServerWebExchange exchange
    ) {
        return Mono.fromSupplier(() -> mapper.toQuery(page, size, customerId, product, status))
                .flatMap(listAccounts::execute)
                .map(result -> ResponseEntity.ok(mapper.toAccountPage(result)));
    }

    @Override
    public Mono<ResponseEntity<AccountResponse>> updateAccount(
            UUID accountId,
            String ifMatch,
            Mono<UpdateAccountRequest> request,
            ServerWebExchange exchange
    ) {
        return request
                .flatMap(body -> {
                    OptionalLong expectedVersion = mapper.toExpectedVersion(ifMatch);
                    if (expectedVersion.isEmpty()) {
                        return unmatchableRevision(accountId);
                    }
                    return updateAccount.execute(mapper.toCommand(accountId, expectedVersion.getAsLong(), body));
                })
                .map(this::okWithETag);
    }

    @Override
    public Mono<ResponseEntity<Void>> closeAccount(UUID accountId, String ifMatch, ServerWebExchange exchange) {
        ResponseEntity<Void> noContent = ResponseEntity.noContent().build();
        return Mono.defer(() -> {
                    OptionalLong expectedVersion = mapper.toExpectedVersion(ifMatch);
                    if (expectedVersion.isEmpty()) {
                        return unmatchableRevision(accountId).then();
                    }
                    return closeAccount.execute(new CloseAccountCommand(accountId, expectedVersion.getAsLong()));
                })
                .thenReturn(noContent);
    }

    @Override
    public Mono<ResponseEntity<AccountBalanceResponse>> getAccountBalance(
            UUID accountId,
            ServerWebExchange exchange
    ) {
        return getAccountBalance.execute(new GetAccountBalanceQuery(accountId))
                .map(view -> ResponseEntity.ok(mapper.toResponse(view)));
    }

    @Override
    public Mono<ResponseEntity<DailyAverageBalanceResponse>> getDailyAverageBalance(
            UUID accountId,
            String period,
            ServerWebExchange exchange
    ) {
        return Mono.fromSupplier(() -> new GetDailyAverageBalanceQuery(accountId,
                        period == null ? null : YearMonth.parse(period)))
                .flatMap(getDailyAverageBalance::execute)
                .map(view -> ResponseEntity.ok(mapper.toResponse(view)));
    }

    @Override
    public Mono<ResponseEntity<MovementPageResponse>> listAccountMovements(
            UUID accountId,
            Integer page,
            Integer size,
            LocalDate from,
            LocalDate to,
            ServerWebExchange exchange
    ) {
        return Mono.fromSupplier(() -> new ListAccountMovementsQuery(accountId, PageRequest.of(page, size), from, to))
                .flatMap(listAccountMovements::execute)
                .map(result -> ResponseEntity.ok(mapper.toMovementPage(result)));
    }

    private ResponseEntity<AccountResponse> okWithETag(AccountView view) {
        return ResponseEntity.ok()
                .eTag(mapper.toETag(view.version()))
                .body(mapper.toResponse(view));
    }

    /**
     * A well-formed tag that no revision can equal: keep 404 precedence for a missing
     * account, otherwise the precondition fails (412).
     */
    private Mono<AccountView> unmatchableRevision(UUID accountId) {
        return getAccount.execute(new GetAccountQuery(accountId))
                .then(Mono.error(() -> new AccountConcurrentModificationException(accountId)));
    }
}
