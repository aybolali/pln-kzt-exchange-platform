package pl.aybolali.plnkztexchangebot.dto;

import jakarta.validation.constraints.*;
import pl.aybolali.plnkztexchangebot.entity.TransferMethod;
import java.math.BigDecimal;

/**
 * Создание сделки
 */
public record CreateDealDTO(
        @NotNull(message = "Request ID обязателен")
        Long requestId,
        @NotNull(message = "Сумма обязательна")
        @Positive(message = "Сумма должна быть положительной")
        BigDecimal amount,

        TransferMethod transferMethod
) {}