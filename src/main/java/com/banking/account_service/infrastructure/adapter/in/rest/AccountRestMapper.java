package com.banking.account_service.infrastructure.adapter.in.rest;

import com.banking.account_service.application.command.OpenAccountCommand;
import com.banking.account_service.application.command.UpdateAccountCommand;
import com.banking.account_service.application.model.AccountBalanceView;
import com.banking.account_service.application.model.AccountView;
import com.banking.account_service.application.model.DailyAverageBalanceView;
import com.banking.account_service.application.model.MonthlyUsageView;
import com.banking.account_service.application.model.ProductReport;
import com.banking.account_service.application.model.RecordedOperation;
import com.banking.account_service.application.model.ResultPage;
import com.banking.account_service.application.query.ListAccountsQuery;
import com.banking.account_service.application.query.PageRequest;
import com.banking.account_service.domain.model.Movement;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountBalanceResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountPageResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountProduct;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountStatus;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountType;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.CurrencyCode;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.CustomerProfile;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.CustomerType;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.DailyAverageBalanceResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.MonthlyUsage;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.MovementPageResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.MovementResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.MovementTotals;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.MovementType;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.OpenAccountRequest;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.OperationResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.OperationType;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.ProductReportLine;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.ProductReportResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.TransferKind;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.UpdateAccountRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebInputException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Translates between generated REST DTOs and application records, and between revisions
 * and strong ETags ({@code "3"}). Generated enums and domain enums share names, so
 * conversion goes through {@code name()}.
 */
@Component
public class AccountRestMapper {

    private static final Pattern STRONG_NUMERIC_ETAG = Pattern.compile("^\"([0-9]+)\"$");

    public OpenAccountCommand toCommand(OpenAccountRequest request) {
        return new OpenAccountCommand(
                convert(request.getProduct(), com.banking.account_service.domain.model.AccountProduct.class),
                convert(request.getCurrency(), com.banking.account_service.domain.model.CurrencyCode.class),
                request.getPrimaryHolderId(),
                toList(request.getHolderIds()),
                toList(request.getAuthorizedSignerIds()),
                request.getInitialDeposit());
    }

    public UpdateAccountCommand toCommand(UUID accountId, long expectedVersion, UpdateAccountRequest request) {
        return new UpdateAccountCommand(accountId, toList(request.getHolderIds()),
                toList(request.getAuthorizedSignerIds()), expectedVersion);
    }

    public ListAccountsQuery toQuery(
            Integer page,
            Integer size,
            UUID customerId,
            AccountProduct product,
            AccountStatus status
    ) {
        return new ListAccountsQuery(PageRequest.of(page, size), customerId,
                convert(product, com.banking.account_service.domain.model.AccountProduct.class),
                convert(status, com.banking.account_service.domain.model.AccountStatus.class));
    }

    public AccountResponse toResponse(AccountView view) {
        return new AccountResponse()
                .id(view.id())
                .product(convert(view.product(), AccountProduct.class))
                .type(convert(view.type(), AccountType.class))
                .currency(convert(view.currency(), CurrencyCode.class))
                .primaryHolderId(view.primaryHolderId())
                .customerType(convert(view.customerType(), CustomerType.class))
                .customerProfile(convert(view.customerProfile(), CustomerProfile.class))
                .holderIds(view.holderIds())
                .authorizedSignerIds(view.signerIds())
                .balance(view.balance())
                .status(convert(view.status(), AccountStatus.class))
                .openedAt(utc(view.openedAt()))
                .updatedAt(utc(view.updatedAt()))
                .closedAt(utc(view.closedAt()));
    }

    public AccountPageResponse toAccountPage(ResultPage<AccountView> page) {
        return new AccountPageResponse()
                .items(page.items().stream().map(this::toResponse).toList())
                .page(page.page())
                .size(page.size())
                .totalElements(page.totalElements())
                .totalPages(page.totalPages());
    }

    public AccountBalanceResponse toResponse(AccountBalanceView view) {
        return new AccountBalanceResponse()
                .accountId(view.accountId())
                .product(convert(view.product(), AccountProduct.class))
                .currency(convert(view.currency(), CurrencyCode.class))
                .balance(view.balance())
                .status(convert(view.status(), AccountStatus.class))
                .asOf(utc(view.asOf()))
                .monthlyUsage(toResponse(view.usage()))
                .monthlyMaintenanceFee(view.monthlyMaintenanceFee())
                .operationDayOfMonth(view.operationDayOfMonth())
                .nextOperationDate(view.nextOperationDate());
    }

    private MonthlyUsage toResponse(MonthlyUsageView usage) {
        return new MonthlyUsage()
                .period(usage.period().toString())
                .movementsUsed(usage.movementsUsed())
                .freeMovements(usage.freeMovements())
                .freeMovementsRemaining(usage.freeMovementsRemaining())
                .movementLimit(usage.movementLimit())
                .movementsRemaining(usage.movementsRemaining())
                .nextMovementFee(usage.nextMovementFee());
    }

    public DailyAverageBalanceResponse toResponse(DailyAverageBalanceView view) {
        return new DailyAverageBalanceResponse()
                .accountId(view.accountId())
                .currency(convert(view.currency(), CurrencyCode.class))
                .period(view.period().toString())
                .fromDate(view.fromDate())
                .toDate(view.toDate())
                .daysCounted(view.daysCounted())
                .averageDailyBalance(view.average())
                .requiredMinimum(view.requiredMinimum())
                .meetsRequirement(view.meetsRequirement());
    }

    public MovementPageResponse toMovementPage(ResultPage<Movement> page) {
        return new MovementPageResponse()
                .items(page.items().stream().map(this::toResponse).toList())
                .page(page.page())
                .size(page.size())
                .totalElements(page.totalElements())
                .totalPages(page.totalPages());
    }

    private MovementResponse toResponse(Movement movement) {
        return new MovementResponse()
                .id(movement.id())
                .accountId(movement.accountId())
                .operationId(movement.operationId())
                .sequence(movement.sequence())
                .type(convert(movement.type(), MovementType.class))
                .amount(movement.amount())
                .fee(movement.fee())
                .balanceAfter(movement.balanceAfter())
                .counterpartyAccountId(movement.counterpartyAccountId())
                .description(movement.description())
                .occurredAt(utc(movement.occurredAt()))
                .businessDate(movement.businessDate());
    }

    /** The leg of the account in the request path. */
    public OperationResponse toResponse(RecordedOperation operation) {
        return new OperationResponse()
                .operationId(operation.operationId())
                .type(convert(operation.type(), OperationType.class))
                .status(OperationResponse.StatusEnum.COMPLETED)
                .accountId(operation.accountId())
                .counterpartyAccountId(operation.counterpartyAccountId())
                .transferKind(convert(operation.transferKind(), TransferKind.class))
                .amount(operation.amount())
                .fee(operation.fee())
                .balanceAfter(operation.balanceAfter())
                .currency(convert(operation.currency(), CurrencyCode.class))
                .description(operation.description())
                .occurredAt(utc(operation.occurredAt()))
                .businessDate(operation.businessDate());
    }

    public ProductReportResponse toResponse(ProductReport report) {
        return new ProductReportResponse()
                .from(report.from())
                .to(report.to())
                .businessZone(report.businessZone().getId())
                .generatedAt(utc(report.generatedAt()))
                .products(report.lines().stream().map(this::toResponse).toList());
    }

    private ProductReportLine toResponse(ProductReport.Line line) {
        return new ProductReportLine()
                .product(convert(line.product(), AccountProduct.class))
                .currency(convert(line.currency(), CurrencyCode.class))
                .accountsOpened(line.accountsOpened())
                .accountsClosed(line.accountsClosed())
                .activeAccountsAtEnd(line.activeAccountsAtEnd())
                .closingBalanceTotal(line.closingBalanceTotal())
                .totalFees(line.totalFees())
                .movements(line.movements().stream()
                        .map(totals -> new MovementTotals()
                                .type(convert(totals.type(), MovementType.class))
                                .count(totals.count())
                                .totalAmount(totals.totalAmount())
                                .totalFees(totals.totalFees()))
                        .toList());
    }

    /** Strong entity tag for a revision, quoted as the contract requires. */
    public String toETag(long version) {
        return "\"" + version + "\"";
    }

    /**
     * Reads the revision from If-Match.
     *
     * @return the revision, or empty when the tag is well formed but can never match one
     * this API issued (leading zeros such as {@code "03"}, or a value beyond the long
     * range): the caller must answer 412, not 400.
     * @throws ServerWebInputException when the header is not one quoted number. The
     *                                 generated controller already validates the pattern; this is a guard.
     */
    public OptionalLong toExpectedVersion(String ifMatch) {
        var matcher = ifMatch == null ? null : STRONG_NUMERIC_ETAG.matcher(ifMatch);
        if (matcher == null || !matcher.matches()) {
            throw new ServerWebInputException("Malformed If-Match header.");
        }
        try {
            long version = Long.parseLong(matcher.group(1));
            return toETag(version).equals(ifMatch) ? OptionalLong.of(version) : OptionalLong.empty();
        } catch (NumberFormatException beyondLongRange) {
            return OptionalLong.empty();
        }
    }

    private static <E extends Enum<E>> E convert(Enum<?> value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value.name());
    }

    private static List<UUID> toList(Collection<UUID> ids) {
        return ids == null ? List.of() : new ArrayList<>(ids);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
