package com.banking.account_service.infrastructure.adapter.in.rest;

import com.banking.account_service.application.command.DepositCommand;
import com.banking.account_service.application.command.TransferCommand;
import com.banking.account_service.application.command.WithdrawCommand;
import com.banking.account_service.application.model.OperationResult;
import com.banking.account_service.application.port.in.DepositUseCase;
import com.banking.account_service.application.port.in.TransferUseCase;
import com.banking.account_service.application.port.in.WithdrawUseCase;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.api.OperationsApiDelegate;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.DepositRequest;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.OperationResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.TransferRequest;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.WithdrawalRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Monetary operations. Each request turns into a command identified by its operationId;
 * a new operation answers 201, a recognized repeat answers 200 with the original result
 * and {@code Idempotent-Replayed: true}.
 */
@Component
public class OperationsApiDelegateImpl implements OperationsApiDelegate {

    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final DepositUseCase deposit;
    private final WithdrawUseCase withdraw;
    private final TransferUseCase transfer;
    private final AccountRestMapper mapper;

    public OperationsApiDelegateImpl(
            DepositUseCase deposit,
            WithdrawUseCase withdraw,
            TransferUseCase transfer,
            AccountRestMapper mapper
    ) {
        this.deposit = deposit;
        this.withdraw = withdraw;
        this.transfer = transfer;
        this.mapper = mapper;
    }

    @Override
    public Mono<ResponseEntity<OperationResponse>> deposit(
            UUID accountId,
            Mono<DepositRequest> request,
            ServerWebExchange exchange
    ) {
        return request
                .map(body -> new DepositCommand(body.getOperationId(), accountId, body.getAmount(),
                        body.getDescription()))
                .flatMap(deposit::execute)
                .map(this::toResponse);
    }

    @Override
    public Mono<ResponseEntity<OperationResponse>> withdraw(
            UUID accountId,
            Mono<WithdrawalRequest> request,
            ServerWebExchange exchange
    ) {
        return request
                .map(body -> new WithdrawCommand(body.getOperationId(), accountId, body.getAmount(),
                        body.getDescription()))
                .flatMap(withdraw::execute)
                .map(this::toResponse);
    }

    @Override
    public Mono<ResponseEntity<OperationResponse>> transfer(
            UUID accountId,
            Mono<TransferRequest> request,
            ServerWebExchange exchange
    ) {
        return request
                .map(body -> new TransferCommand(body.getOperationId(), accountId, body.getDestinationAccountId(),
                        body.getAmount(), body.getDescription()))
                .flatMap(transfer::execute)
                .map(this::toResponse);
    }

    private ResponseEntity<OperationResponse> toResponse(OperationResult result) {
        OperationResponse body = mapper.toResponse(result.operation());
        if (result.replayed()) {
            return ResponseEntity.ok().header(IDEMPOTENT_REPLAYED, "true").body(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }
}
