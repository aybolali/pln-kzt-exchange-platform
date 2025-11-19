package pl.aybolali.plnkztexchangebot.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Обновление запроса
 */
public record UpdateExchangeRequestDTO(
        @Positive
        BigDecimal amountNeed,

        @Size(max = 500)
        String notes
) {}