package com.tus.inventoryservice.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PagedResponse<T>(
        List<T> data,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T, R> PagedResponse<R> of(Page<T> page, Function<T, R> mapper) {
        List<R> data = page.getContent().stream()
                .map(mapper)
                .toList();
        return new PagedResponse<>(
                data,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
