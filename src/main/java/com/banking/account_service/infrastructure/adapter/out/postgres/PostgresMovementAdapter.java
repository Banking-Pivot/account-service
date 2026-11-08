package com.banking.account_service.infrastructure.adapter.out.postgres;

import com.banking.account_service.application.model.ResultPage;
import com.banking.account_service.application.port.out.MovementReadPort;
import com.banking.account_service.application.port.out.MovementWritePort;
import com.banking.account_service.application.query.ListAccountMovementsQuery;
import com.banking.account_service.domain.model.Movement;
import org.springframework.data.domain.Sort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.data.relational.core.query.Criteria.where;
import static org.springframework.data.relational.core.query.Query.query;

/** Account history on PostgreSQL: appends on the write side, pages and ranges on the read side. */
@Component
public class PostgresMovementAdapter implements MovementWritePort, MovementReadPort {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "sequenceNumber");
    private static final Sort OLDEST_FIRST = Sort.by(Sort.Direction.ASC, "sequenceNumber");

    private final R2dbcEntityTemplate template;

    public PostgresMovementAdapter(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    public Mono<Void> append(Movement movement) {
        return template.insert(MovementRow.from(movement)).then();
    }

    @Override
    public Mono<ResultPage<Movement>> findPage(ListAccountMovementsQuery request) {
        Criteria criteria = where("accountId").is(request.accountId());
        if (request.from() != null) {
            criteria = criteria.and("businessDate").greaterThanOrEquals(request.from());
        }
        if (request.to() != null) {
            criteria = criteria.and("businessDate").lessThanOrEquals(request.to());
        }
        Query page = query(criteria)
                .sort(NEWEST_FIRST)
                .offset(request.pageRequest().offset())
                .limit(request.pageRequest().size());
        return Mono.zip(
                        template.select(page, MovementRow.class).map(MovementRow::toDomain).collectList(),
                        template.count(query(criteria), MovementRow.class))
                .map(result -> ResultPage.of(result.getT1(), request.pageRequest(), result.getT2()));
    }

    @Override
    public Mono<BigDecimal> closingBalanceBefore(UUID accountId, LocalDate date) {
        return template.select(MovementRow.class)
                .matching(query(where("accountId").is(accountId).and("businessDate").lessThan(date))
                        .sort(NEWEST_FIRST)
                        .limit(1))
                .first()
                .map(MovementRow::balanceAfter);
    }

    @Override
    public Flux<Movement> findBetween(UUID accountId, LocalDate from, LocalDate to) {
        return template.select(MovementRow.class)
                .matching(query(where("accountId").is(accountId).and("businessDate").between(from, to))
                        .sort(OLDEST_FIRST))
                .all()
                .map(MovementRow::toDomain);
    }
}
