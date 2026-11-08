package com.banking.account_service.domain.model;

import com.banking.account_service.domain.exception.AccountRuleViolationException;
import com.banking.account_service.domain.exception.RuleViolation;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Holders and authorized signers of an account. Customer IDs are references to
 * Customer Service.
 *
 * <p>The primary holder is always the first holder. The other holders and the signers
 * are kept sorted by ID, so two equal sets of participants are equal records whatever
 * order the client used.
 */
public record AccountParticipants(UUID primaryHolderId, List<UUID> holderIds, List<UUID> signerIds) {

    /** Maximum holders and maximum signers per account. */
    public static final int MAX_PER_LIST = 10;

    private static final Comparator<UUID> STABLE_ORDER = Comparator.comparing(UUID::toString);

    public AccountParticipants {
        if (primaryHolderId == null) {
            throw invalid("The primary holder is required.");
        }
        holderIds = normalizedHolders(primaryHolderId, holderIds);
        signerIds = sorted(signerIds, "authorized signers");
        if (holderIds.size() > MAX_PER_LIST || signerIds.size() > MAX_PER_LIST) {
            throw invalid("An account accepts at most " + MAX_PER_LIST + " holders and "
                    + MAX_PER_LIST + " authorized signers.");
        }
        Set<UUID> holders = new HashSet<>(holderIds);
        if (signerIds.stream().anyMatch(holders::contains)) {
            throw invalid("A customer cannot be both holder and authorized signer.");
        }
    }

    /**
     * Builds participants from a request. Null or empty holders mean the primary holder
     * only; null signers mean none.
     */
    public static AccountParticipants of(UUID primaryHolderId, Collection<UUID> holderIds, Collection<UUID> signerIds) {
        if (primaryHolderId == null) {
            throw invalid("The primary holder is required.");
        }
        List<UUID> holders = holderIds == null || holderIds.isEmpty()
                ? List.of(primaryHolderId)
                : List.copyOf(requireNoNulls(holderIds, "holders"));
        List<UUID> signers = signerIds == null
                ? List.of()
                : List.copyOf(requireNoNulls(signerIds, "authorized signers"));
        return new AccountParticipants(primaryHolderId, holders, signers);
    }

    /**
     * Checks the rules that depend on the customer type: a PERSONAL account has exactly one
     * holder, the primary holder, and no authorized signers.
     */
    public void requireAllowedFor(CustomerType customerType) {
        if (customerType == CustomerType.PERSONAL && (holderIds.size() != 1 || !signerIds.isEmpty())) {
            throw invalid("A personal account has only the primary holder and no authorized signers.");
        }
    }

    /** Every referenced customer, primary holder first. */
    public Set<UUID> customerIds() {
        return Stream.concat(holderIds.stream(), signerIds.stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<UUID> normalizedHolders(UUID primaryHolderId, List<UUID> holderIds) {
        List<UUID> holders = sorted(holderIds, "holders");
        if (!holders.contains(primaryHolderId)) {
            throw invalid("The primary holder must be one of the holders.");
        }
        return Stream.concat(
                Stream.of(primaryHolderId),
                holders.stream().filter(id -> !id.equals(primaryHolderId))
        ).toList();
    }

    private static List<UUID> sorted(List<UUID> ids, String name) {
        Collection<UUID> checked = requireNoNulls(ids == null ? List.of() : ids, name);
        if (new HashSet<>(checked).size() != checked.size()) {
            throw invalid("The " + name + " cannot repeat a customer.");
        }
        return checked.stream().sorted(STABLE_ORDER).toList();
    }

    private static Collection<UUID> requireNoNulls(Collection<UUID> ids, String name) {
        if (ids.stream().anyMatch(Objects::isNull)) {
            throw invalid("The " + name + " cannot contain empty IDs.");
        }
        return ids;
    }

    private static AccountRuleViolationException invalid(String message) {
        return new AccountRuleViolationException(RuleViolation.INVALID_PARTICIPANTS, message);
    }
}
