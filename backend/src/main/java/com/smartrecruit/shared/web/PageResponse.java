package com.smartrecruit.shared.web;

import java.util.List;
import java.util.function.Function;

/**
 * Réponse paginée commune ({@code docs/api.md} §0) :
 * {@code { "content": [...], "page": 0, "size": 20, "totalElements": 45, "totalPages": 3 }}.
 * Format propre à l'API, indépendant de la classe {@code Page} de Spring Data (dont la forme JSON
 * n'est pas un contrat stable).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(List.copyOf(content), page, size, totalElements, totalPages);
    }

    public static <T, R> PageResponse<R> of(org.springframework.data.domain.Page<T> page, Function<T, R> mapper) {
        return of(page.getContent().stream().map(mapper).toList(), page.getNumber(), page.getSize(),
                page.getTotalElements());
    }

    /** Page demandée bornée : jamais négative ; taille entre 1 et 100 (une taille de 500 devient 100). */
    public static int page(int requested) {
        return Math.max(0, requested);
    }

    public static int size(int requested) {
        return requested <= 0 ? DEFAULT_SIZE : Math.min(requested, MAX_SIZE);
    }
}
