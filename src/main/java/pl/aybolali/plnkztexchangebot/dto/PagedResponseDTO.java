package pl.aybolali.plnkztexchangebot.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Пагинированные ответы для больших списков
 * Вместо 1000 записей сразу → 20 записей + навигация
 */
public record PagedResponseDTO<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
    public static <T> PagedResponseDTO<T> of(Page<T> page) {
        return new PagedResponseDTO<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()
        );
    }
}
