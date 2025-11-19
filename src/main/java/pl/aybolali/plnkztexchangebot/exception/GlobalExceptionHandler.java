package pl.aybolali.plnkztexchangebot.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.aybolali.plnkztexchangebot.dto.ApiResponseDTO;

/**
 * 🛡️ Глобальный обработчик исключений
 * Преобразует технические ошибки в понятные сообщения
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * ✅ Обработка валидации (@Valid)
     * Преобразует Field errors в понятные сообщения
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        // Берем ПЕРВУЮ ошибку валидации
        FieldError firstError = (FieldError) ex.getBindingResult().getAllErrors().get(0);
        String errorMessage = firstError.getDefaultMessage();

        log.warn("Validation failed: {}", errorMessage);

        return ResponseEntity
                .badRequest()
                .body(ApiResponseDTO.error(errorMessage));
    }

    /**
     * 💼 Обработка бизнес-ошибок
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleBusinessException(BusinessException ex) {
        log.warn("Business exception: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponseDTO.error(ex.getMessage()));
    }

    /**
     * 👤 Обработка "пользователь не найден"
     */
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleUserNotFoundException(UserNotFoundException ex) {
        log.warn("User not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponseDTO.error("Пользователь не найден"));
    }

    /**
     * ⏳ Обработка rate limit
     */
    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleRateLimitException(RateLimitException ex) {
        log.warn("Rate limit exceeded for user {}: {}", ex.getUserId(), ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponseDTO.error("Слишком много запросов. Пожалуйста, подождите немного"));
    }

    /**
     * ⭐ Обработка ошибок рейтинга
     */
    @ExceptionHandler(RatingException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleRatingException(RatingException ex) {
        log.warn("Rating exception: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponseDTO.error(ex.getMessage()));
    }

    /**
     * 🔒 Обработка ошибок доступа
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity
                .badRequest()
                .body(ApiResponseDTO.error(ex.getMessage()));
    }

    /**
     * ❌ Общий обработчик для всех остальных исключений
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponseDTO<Object>> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseDTO.error("Произошла внутренняя ошибка сервера"));
    }
}