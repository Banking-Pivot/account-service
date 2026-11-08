package com.banking.account_service.infrastructure.adapter.in.rest;

import com.banking.account_service.application.exception.AccountConcurrentModificationException;
import com.banking.account_service.application.exception.AccountNotFoundException;
import com.banking.account_service.application.exception.DependencyUnavailableException;
import com.banking.account_service.application.exception.DuplicatePersonalAccountException;
import com.banking.account_service.application.exception.InvalidQueryException;
import com.banking.account_service.application.exception.OperationIdConflictException;
import com.banking.account_service.domain.exception.AccountClosedException;
import com.banking.account_service.domain.exception.AccountRuleViolationException;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.MissingRequestValueException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.stream.Collectors;

/**
 * Maps every failure to the contract's ErrorResponse with a stable code.
 *
 * <p>Also receives errors raised before the delegate runs (415 from request mapping, 428
 * from a missing If-Match, 400 from parameter validation), because WebFlux routes dispatch
 * errors to {@code @ExceptionHandler} methods. Messages never echo free text from the
 * request, such as descriptions.
 */
@Slf4j
@RestControllerAdvice
public class AccountExceptionHandler {

    private final Clock clock;

    public AccountExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(AccountNotFoundException exception, ServerWebExchange exchange) {
        return error(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "The account does not exist.", exchange);
    }

    @ExceptionHandler(AccountConcurrentModificationException.class)
    public ResponseEntity<ErrorResponse> handleVersionConflict(
            AccountConcurrentModificationException exception,
            ServerWebExchange exchange
    ) {
        return error(HttpStatus.PRECONDITION_FAILED, "ACCOUNT_VERSION_CONFLICT",
                "The account changed. Read it again before retrying.", exchange);
    }

    @ExceptionHandler(AccountClosedException.class)
    public ResponseEntity<ErrorResponse> handleClosed(AccountClosedException exception, ServerWebExchange exchange) {
        return error(HttpStatus.CONFLICT, "ACCOUNT_CLOSED", "The account is closed.", exchange);
    }

    @ExceptionHandler(OperationIdConflictException.class)
    public ResponseEntity<ErrorResponse> handleOperationIdConflict(
            OperationIdConflictException exception,
            ServerWebExchange exchange
    ) {
        return error(HttpStatus.CONFLICT, "OPERATION_ID_CONFLICT",
                "The operationId was already used with different data.", exchange);
    }

    @ExceptionHandler(DuplicatePersonalAccountException.class)
    public ResponseEntity<ErrorResponse> handleDuplicatePersonalAccount(
            DuplicatePersonalAccountException exception,
            ServerWebExchange exchange
    ) {
        return error(HttpStatus.CONFLICT, "PERSONAL_ACCOUNT_ALREADY_EXISTS",
                "The customer already has an active account of this type.", exchange);
    }

    @ExceptionHandler(AccountRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(
            AccountRuleViolationException exception,
            ServerWebExchange exchange
    ) {
        // Domain messages name rules, IDs and configured values only.
        return error(HttpStatus.UNPROCESSABLE_CONTENT, exception.violation().name(), exception.getMessage(), exchange);
    }

    @ExceptionHandler(DependencyUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleDependencyUnavailable(
            DependencyUnavailableException exception,
            ServerWebExchange exchange
    ) {
        log.warn("Dependency unavailable. requestId={}, dependency={}, causeType={}",
                exchange.getRequest().getId(), exception.dependency(),
                exception.getCause() == null ? null : exception.getCause().getClass().getName());
        return error(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                exception.dependency() + " is unavailable. Nothing was written; retry later.", exchange);
    }

    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<ErrorResponse> handleInvalidQuery(InvalidQueryException exception, ServerWebExchange exchange) {
        return invalidRequest(exception.getMessage(), exchange);
    }

    @ExceptionHandler(MissingRequestValueException.class)
    public ResponseEntity<ErrorResponse> handleMissingValue(
            MissingRequestValueException exception,
            ServerWebExchange exchange
    ) {
        if (HttpHeaders.IF_MATCH.equalsIgnoreCase(exception.getName())) {
            return error(HttpStatus.PRECONDITION_REQUIRED, "IF_MATCH_REQUIRED",
                    "If-Match header is required. Send the ETag from your last read.", exchange);
        }
        return invalidRequest("The request is missing a required value.", exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleInvalidBody(WebExchangeBindException exception, ServerWebExchange exchange) {
        String fields = exception.getFieldErrors().stream()
                .map(FieldError::getField)
                .distinct()
                .sorted()
                .collect(Collectors.joining(", "));
        return invalidRequest("Invalid or missing fields: " + fields + ".", exchange);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(ServerWebInputException exception, ServerWebExchange exchange) {
        return invalidRequest("The request is malformed or contains invalid, unknown or missing fields.", exchange);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            ServerWebExchange exchange
    ) {
        boolean ifMatch = exception.getConstraintViolations().stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().endsWith(".ifMatch"));
        return ifMatch ? invalidIfMatch(exchange) : invalidRequest("The request contains invalid parameters.", exchange);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(
            HandlerMethodValidationException exception,
            ServerWebExchange exchange
    ) {
        if (exception.isForReturnValue()) {
            return unexpected(exception, exchange);
        }
        boolean ifMatch = exception.getParameterValidationResults().stream()
                .anyMatch(result -> isIfMatchHeader(result.getMethodParameter()));
        return ifMatch ? invalidIfMatch(exchange) : invalidRequest("The request contains invalid parameters.", exchange);
    }

    @ExceptionHandler(UnsupportedMediaTypeStatusException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            UnsupportedMediaTypeStatusException exception,
            ServerWebExchange exchange
    ) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Request body must use application/json.", exchange);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleHttpError(ResponseStatusException exception, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null || status.is5xxServerError()) {
            return unexpected(exception, exchange);
        }
        return error(status, status.name(), status.getReasonPhrase() + ".", exchange);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, ServerWebExchange exchange) {
        return unexpected(exception, exchange);
    }

    private ResponseEntity<ErrorResponse> unexpected(Exception exception, ServerWebExchange exchange) {
        // No payloads or SQL messages in the log; the type is enough to correlate with the request.
        log.error("Unhandled account request failure. requestId={}, exceptionType={}",
                exchange.getRequest().getId(), exception.getClass().getName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The request could not be completed.", exchange);
    }

    private ResponseEntity<ErrorResponse> invalidIfMatch(ServerWebExchange exchange) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_IF_MATCH",
                "If-Match must be exactly one quoted numeric ETag, for example \"3\".", exchange);
    }

    private ResponseEntity<ErrorResponse> invalidRequest(String message, ServerWebExchange exchange) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", message, exchange);
    }

    private static boolean isIfMatchHeader(MethodParameter parameter) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        return header != null && HttpHeaders.IF_MATCH.equalsIgnoreCase(
                header.value().isEmpty() ? header.name() : header.value());
    }

    private ResponseEntity<ErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            ServerWebExchange exchange
    ) {
        ErrorResponse response = new ErrorResponse()
                .code(code)
                .message(message)
                .timestamp(OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC))
                .path(exchange.getRequest().getPath().value())
                .traceId(exchange.getRequest().getId());
        return ResponseEntity.status(status).body(response);
    }
}
