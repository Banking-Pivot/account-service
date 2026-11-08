package com.banking.account_service.application.model;

import com.banking.account_service.application.query.PageRequest;

import java.util.List;

/** One page of results with the total count, observed at nearly the same time. */
public record ResultPage<T>(List<T> items, int page, int size, long totalElements, long totalPages) {

    public ResultPage {
        items = List.copyOf(items);
    }

    /** Builds the page; totalPages is the ceiling of total / size and zero when empty. */
    public static <T> ResultPage<T> of(List<T> items, PageRequest request, long totalElements) {
        long totalPages = totalElements == 0 ? 0 : (totalElements + request.size() - 1) / request.size();
        return new ResultPage<>(items, request.page(), request.size(), totalElements, totalPages);
    }
}
