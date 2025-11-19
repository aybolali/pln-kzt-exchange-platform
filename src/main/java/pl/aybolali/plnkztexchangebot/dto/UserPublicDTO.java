package pl.aybolali.plnkztexchangebot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UserPublicDTO(
        Long id,
        String telegramUsername,
        String firstName,
        @JsonProperty("rating") BigDecimal trustRating,
        Integer successfulDeals,
        @JsonFormat(pattern = "HH:mm")
        LocalDateTime createdAt
) {}