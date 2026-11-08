package com.banking.account_service.domain.model;

import com.banking.account_service.domain.exception.AccountRuleViolationException;
import com.banking.account_service.domain.exception.RuleViolation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountParticipantsTest {

    private final UUID primary = UUID.randomUUID();

    @Test
    void omittedHoldersMeanThePrimaryHolderOnly() {
        AccountParticipants participants = AccountParticipants.of(primary, null, null);

        assertThat(participants.holderIds()).containsExactly(primary);
        assertThat(participants.signerIds()).isEmpty();
        participants.requireAllowedFor(CustomerType.PERSONAL);
    }

    @Test
    void primaryHolderComesFirstAndTheRestIsOrderIndependent() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        AccountParticipants one = AccountParticipants.of(primary, List.of(a, primary, b), List.of());
        AccountParticipants other = AccountParticipants.of(primary, List.of(b, a, primary), null);

        assertThat(one).isEqualTo(other);
        assertThat(one.holderIds().get(0)).isEqualTo(primary);
        assertThat(one.customerIds()).hasSize(3).first().isEqualTo(primary);
    }

    @Test
    void thePrimaryHolderMustBeAmongTheHolders() {
        assertInvalid(() -> AccountParticipants.of(primary, List.of(UUID.randomUUID()), null));
    }

    @Test
    void aCustomerCannotBeHolderAndSigner() {
        UUID both = UUID.randomUUID();
        assertInvalid(() -> AccountParticipants.of(primary, List.of(primary, both), List.of(both)));
    }

    @Test
    void listsAreLimitedToTen() {
        List<UUID> eleven = new ArrayList<>(IntStream.range(0, 10).mapToObj(i -> UUID.randomUUID()).toList());
        eleven.add(primary);
        assertInvalid(() -> AccountParticipants.of(primary, eleven, null));
    }

    @Test
    void personalAccountsRejectCoHoldersAndSigners() {
        AccountParticipants withSigner = AccountParticipants.of(primary, null, List.of(UUID.randomUUID()));

        assertInvalid(() -> withSigner.requireAllowedFor(CustomerType.PERSONAL));
        withSigner.requireAllowedFor(CustomerType.BUSINESS);
    }

    private static void assertInvalid(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(AccountRuleViolationException.class)
                .satisfies(error -> assertThat(((AccountRuleViolationException) error).violation())
                        .isEqualTo(RuleViolation.INVALID_PARTICIPANTS));
    }
}
