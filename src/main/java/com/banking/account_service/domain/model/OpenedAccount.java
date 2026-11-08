package com.banking.account_service.domain.model;

import java.util.Optional;

/** A new account and, when the initial deposit is positive, its OPENING_DEPOSIT movement. */
public record OpenedAccount(Account account, Optional<Movement> openingMovement) {
}
