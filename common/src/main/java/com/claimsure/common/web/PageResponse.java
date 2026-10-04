package com.claimsure.common.web;

import java.util.List;
import java.util.function.Function;

/** Stable, framework-independent paging envelope for list endpoints. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> of(List<S> items, int page, int size, long total, Function<S, T> mapper) {
        return new PageResponse<>(items.stream().map(mapper).toList(), page, size, total,
                size == 0 ? 0 : (int) Math.ceil((double) total / size));
    }
}
