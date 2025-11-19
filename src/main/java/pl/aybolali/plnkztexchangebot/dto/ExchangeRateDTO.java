package pl.aybolali.plnkztexchangebot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * DTO для курсов валют PLN/KZT
 */
@Builder
public record ExchangeRateDTO(
        @JsonFormat(shape = JsonFormat.Shape.NUMBER)
        BigDecimal plnToKzt,              // Курс PLN → KZT (например: 145.75)

        @JsonFormat(shape = JsonFormat.Shape.NUMBER)
        BigDecimal kztToPln,              // Курс KZT → PLN (например: 0.006872)

        String date                       // Дата курса (например: "2025-09-01")
) {}