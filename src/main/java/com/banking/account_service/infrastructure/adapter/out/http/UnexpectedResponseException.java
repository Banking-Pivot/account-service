package com.banking.account_service.infrastructure.adapter.out.http;

/**
 * A dependency answered something outside its contract: an unexpected status, an empty
 * body or a 404 that is not the documented "not found". It counts as a failure for the
 * circuit breaker and becomes {@code DependencyUnavailableException}.
 */
class UnexpectedResponseException extends RuntimeException {

    UnexpectedResponseException(String message) {
        super(message);
    }
}
