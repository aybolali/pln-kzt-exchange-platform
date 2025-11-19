package pl.aybolali.plnkztexchangebot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * Стандартная обертка для API ответов - чтобы сохранить образ response api json
 * Обеспечивает единообразие: {"success": true, "data": {...}, "message": "OK"} - избегает от типов как просто "error" - сохраняет форму json единообразие
 * в случае "error" с ApiResponse будет отображаться как {"success": false, "data": null, "message": "error"}
 */
public record ApiResponseDTO<T>(
        boolean success,
        T data,
        String message,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
        LocalDateTime timestamp
) {
    public static <T> ApiResponseDTO<T> success(T data) {
        return new ApiResponseDTO<>(true, data, null, LocalDateTime.now());
    }

    public static <T> ApiResponseDTO<T> success(T data, String message) {
        return new ApiResponseDTO<>(true, data, message, LocalDateTime.now());
    }

    public static <T> ApiResponseDTO<T> error(String message) {
        return new ApiResponseDTO<>(false, null, message, LocalDateTime.now());
    }
}