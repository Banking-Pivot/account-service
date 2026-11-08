package com.banking.account_service.infrastructure.adapter.in.rest;

import com.banking.account_service.application.model.ResultPage;
import com.banking.account_service.application.query.PageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ServerWebInputException;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountRestMapperTest {

    private final AccountRestMapper mapper = new AccountRestMapper();

    @Test
    void etagIsTheQuotedRevision() {
        assertThat(mapper.toETag(0)).isEqualTo("\"0\"");
        assertThat(mapper.toExpectedVersion("\"42\"")).isEqualTo(OptionalLong.of(42));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"00\"", "\"042\"", "\"9223372036854775808\""})
    void wellFormedTagsThatNoRevisionCanEqualAreUnmatchable(String tag) {
        assertThat(mapper.toExpectedVersion(tag)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"*", "W/\"1\"", "1", "\"1\",\"2\"", "\"-1\""})
    void malformedTagsAreRejected(String tag) {
        assertThatThrownBy(() -> mapper.toExpectedVersion(tag)).isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void totalPagesIsTheCeilingAndZeroWhenEmpty() {
        assertThat(ResultPage.of(List.of(), PageRequest.of(0, 20), 0).totalPages()).isZero();
        assertThat(ResultPage.of(List.of(), PageRequest.of(0, 2), 4).totalPages()).isEqualTo(2);
        assertThat(ResultPage.of(List.of(), PageRequest.of(0, 2), 5).totalPages()).isEqualTo(3);
    }

    @Test
    void everyDomainValueExistsInTheContract() {
        assertSameNames(com.banking.account_service.domain.model.AccountProduct.class,
                com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountProduct.class);
        assertSameNames(com.banking.account_service.domain.model.AccountType.class,
                com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountType.class);
        assertSameNames(com.banking.account_service.domain.model.AccountStatus.class,
                com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountStatus.class);
        assertSameNames(com.banking.account_service.domain.model.CurrencyCode.class,
                com.banking.account_service.infrastructure.adapter.in.rest.generated.model.CurrencyCode.class);
        assertSameNames(com.banking.account_service.domain.model.CustomerType.class,
                com.banking.account_service.infrastructure.adapter.in.rest.generated.model.CustomerType.class);
        assertSameNames(com.banking.account_service.domain.model.CustomerProfile.class,
                com.banking.account_service.infrastructure.adapter.in.rest.generated.model.CustomerProfile.class);
        assertSameNames(com.banking.account_service.domain.model.TransferKind.class,
                com.banking.account_service.infrastructure.adapter.in.rest.generated.model.TransferKind.class);
        assertSameNames(com.banking.account_service.domain.model.MovementType.class,
                com.banking.account_service.infrastructure.adapter.in.rest.generated.model.MovementType.class);
        // OPENING_DEPOSIT is created by the service and never answered as an operation.
        assertThat(names(com.banking.account_service.infrastructure.adapter.in.rest.generated.model.OperationType.class))
                .containsExactly("DEPOSIT", "WITHDRAWAL", "TRANSFER");
    }

    private static void assertSameNames(Class<? extends Enum<?>> domain, Class<? extends Enum<?>> contract) {
        assertThat(names(contract)).as(domain.getSimpleName()).containsExactlyElementsOf(names(domain));
    }

    private static List<String> names(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).toList();
    }
}
