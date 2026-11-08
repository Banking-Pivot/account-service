package com.banking.account_service.application.exception;

/**
 * A service reached through the API Gateway failed, timed out or has its circuit open.
 * Different from an ineligibility answer, which is a successful response.
 */
public class DependencyUnavailableException extends RuntimeException {

    private final String dependency;

    public DependencyUnavailableException(String dependency, Throwable cause) {
        super(dependency + " is unavailable.", cause);
        this.dependency = dependency;
    }

    public String dependency() {
        return dependency;
    }
}
