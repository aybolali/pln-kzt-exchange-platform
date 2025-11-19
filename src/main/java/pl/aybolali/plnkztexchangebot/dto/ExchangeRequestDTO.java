package pl.aybolali.plnkztexchangebot.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequestStatus;
import pl.aybolali.plnkztexchangebot.entity.TransferMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO для запросов обмена
 */
public record ExchangeRequestDTO(
        Long id,
        UserPublicDTO user,
        String currencyNeed,
        BigDecimal amountNeed,
        ExchangeRequestStatus status,
        String notes,
        TransferMethod transferMethod,
        @JsonFormat(pattern = "HH:mm")
        LocalDateTime createdAt,
        @JsonFormat(pattern = "HH:mm")
        LocalDateTime updatedAt,
        @JsonFormat(pattern = "HH:mm")
        LocalDateTime finishedAt,

        // ДОПОЛНИТЕЛЬНЫЕ ПОЛЯ для UI (могут быть null в списках):
        Integer hoursAgo                 // Вычисляемое для UI ("3 часа назад")
) {}