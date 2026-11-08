package com.banking.account_service.application.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Full replacement of holders and signers, conditioned on the revision the client read. */
public record UpdateAccountCommand(UUID accountId, List<UUID> holderIds, List<UUID> signerIds, long expectedVersion) {

    public UpdateAccountCommand {
        Objects.requireNonNull(accountId, "accountId");
        holderIds = holderIds == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(holderIds));
        signerIds = signerIds == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(signerIds));
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion cannot be negative");
        }
    }
}
