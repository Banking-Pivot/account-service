package com.banking.account_service.application.command;

import com.banking.account_service.domain.model.AccountProduct;
import com.banking.account_service.domain.model.CurrencyCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Request to open an account.
 *
 * @param holderIds empty means the primary holder only
 * @param signerIds authorized signers, empty for none
 */
public record OpenAccountCommand(
        AccountProduct product,
        CurrencyCode currency,
        UUID primaryHolderId,
        List<UUID> holderIds,
        List<UUID> signerIds,
        BigDecimal initialDeposit
) {

    public OpenAccountCommand {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(currency, "currency");
        holderIds = holderIds == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(holderIds));
        signerIds = signerIds == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(signerIds));
    }
}
