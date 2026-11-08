package com.banking.account_service.infrastructure.adapter.out.postgres;

import com.banking.account_service.application.model.AccountView;
import com.banking.account_service.application.model.ResultPage;
import com.banking.account_service.application.port.out.AccountReadPort;
import com.banking.account_service.application.query.ListAccountsQuery;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

/** Read side of accounts on PostgreSQL. Reads never lock. */
@Component
public class PostgresAccountReadAdapter implements AccountReadPort {

    private final R2dbcEntityTemplate template;
    private final AccountParticipantsLoader participants;

    public PostgresAccountReadAdapter(R2dbcEntityTemplate template, AccountParticipantsLoader participants) {
        this.template = template;
        this.participants = participants;
    }

    @Override
    public Mono<AccountView> findById(UUID accountId) {
        return template.selectOne(query(where("id").is(accountId)), AccountRow.class)
                .flatMap(participants::restore)
                .map(AccountView::from);
    }

    @Override
    public Mono<Boolean> existsById(UUID accountId) {
        return template.exists(query(where("id").is(accountId)), AccountRow.class);
    }

    @Override
    public Mono<ResultPage<AccountView>> findPage(ListAccountsQuery request) {
        return filter(request).flatMap(criteria -> criteria
                .map(found -> Mono.zip(
                                template.select(query(found)
                                                .sort(Sort.by("id"))
                                                .offset(request.pageRequest().offset())
                                                .limit(request.pageRequest().size()), AccountRow.class)
                                        .collectList()
                                        .flatMap(participants::restore),
                                template.count(query(found), AccountRow.class))
                        .map(page -> ResultPage.of(page.getT1().stream().map(AccountView::from).toList(),
                                request.pageRequest(), page.getT2())))
                .orElseGet(() -> Mono.just(ResultPage.of(List.of(), request.pageRequest(), 0))));
    }

    /**
     * Filters of the query. Empty when the customer filter matches no account, so the page
     * is empty without querying accounts.
     */
    private Mono<Optional<Criteria>> filter(ListAccountsQuery request) {
        List<Criteria> filters = new ArrayList<>();
        if (request.product() != null) {
            filters.add(where("product").is(request.product().name()));
        }
        if (request.status() != null) {
            filters.add(where("status").is(request.status().name()));
        }
        if (request.customerId() == null) {
            return Mono.just(Optional.of(Criteria.from(filters)));
        }
        return template.select(AccountHolderRow.class)
                .matching(query(where("customerId").is(request.customerId())))
                .all()
                .map(AccountHolderRow::accountId)
                .collectList()
                .map(accountIds -> {
                    if (accountIds.isEmpty()) {
                        return Optional.empty();
                    }
                    filters.add(where("id").in(accountIds));
                    return Optional.of(Criteria.from(filters));
                });
    }
}
