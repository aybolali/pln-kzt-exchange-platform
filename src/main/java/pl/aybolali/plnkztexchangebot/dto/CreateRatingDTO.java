package pl.aybolali.plnkztexchangebot.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * DTO для создания рейтинга
 *
 * @param dealId - ID сделки, которую оцениваем
 * @param rating - оценка от 1.0 до 5.0 (с одним знаком после запятой)
 */
public record CreateRatingDTO(
        @NotNull(message = "Укажите ID сделки")
        @Positive(message = "ID сделки должен быть положительным числом")
        Long dealId,

        @NotNull(message = "Укажите оценку")
        @DecimalMin(value = "1.0", message = "Оценка должна быть от 1.0 до 5.0")
        @DecimalMax(value = "5.0", message = "Оценка должна быть от 1.0 до 5.0")
        @Digits(integer = 1, fraction = 1, message = "Оценка должна иметь максимум 1 знак после запятой (например: 4.5)")
        BigDecimal rating
) {}