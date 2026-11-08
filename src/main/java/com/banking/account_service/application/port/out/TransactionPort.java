package com.banking.account_service.application.port.out;

import reactor.core.publisher.Mono;

import java.util.function.Supplier;

/**
 * Runs work in one database transaction. Any error rolls everything back, so an operation
 * is written completely or not at all.
 */
public interface TransactionPort {

    <T> Mono<T> inTransaction(Supplier<Mono<T>> work);
}
