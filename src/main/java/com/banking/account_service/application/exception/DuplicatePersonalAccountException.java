package com.banking.account_service.application.exception;

/** A personal customer already has an ACTIVE account of the same type. */
public class DuplicatePersonalAccountException extends RuntimeException {

    public DuplicatePersonalAccountException(Throwable cause) {
        super("The customer already has an active account of this type.", cause);
    }
}
