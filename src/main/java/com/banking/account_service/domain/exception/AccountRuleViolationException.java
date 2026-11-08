package com.banking.account_service.domain.exception;

/**
 * A business rule rejected the request. Messages name rules, fields and IDs only,
 * never free text supplied by the client.
 */
public class AccountRuleViolationException extends RuntimeException {

    private final RuleViolation violation;

    public AccountRuleViolationException(RuleViolation violation, String message) {
        super(message);
        this.violation = violation;
    }

    public RuleViolation violation() {
        return violation;
    }
}
