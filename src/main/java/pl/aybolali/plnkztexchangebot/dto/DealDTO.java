package pl.aybolali.plnkztexchangebot.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import pl.aybolali.plnkztexchangebot.entity.DealStatus;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequest;
import pl.aybolali.plnkztexchangebot.entity.TransferMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO для сделок
 */
public record DealDTO(
        Long id,
        UserPublicDTO requester,
        UserPublicDTO provider,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER)
        BigDecimal amount,
        ExchangeRequest.Currency currency,
        @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.#")
        BigDecimal exchangeRate,
        TransferMethod transferMethod,
        DealStatus status,
        @JsonFormat(pattern = "HH:mm")
        LocalDateTime createdAt,
        @JsonFormat(pattern = "HH:mm")
        LocalDateTime finishedAt,

        // ДОПОЛНИТЕЛЬНЫЕ ПОЛЯ для UI:
        @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.#")
        BigDecimal convertedAmount,      // deal.getConvertedAmount()
        ExchangeRequest.Currency oppositeCurrency          // deal.getOppositeCurrency()
) {}