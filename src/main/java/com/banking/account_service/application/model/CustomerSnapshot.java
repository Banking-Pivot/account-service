package com.banking.account_service.application.model;

import com.banking.account_service.domain.model.CustomerProfile;
import com.banking.account_service.domain.model.CustomerType;

import java.util.Objects;
import java.util.UUID;

/** What Account Service needs from a Customer Service record. */
public record CustomerSnapshot(UUID id, CustomerType type, CustomerProfile profile, boolean active) {

    public CustomerSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(profile, "profile");
    }
}
