package pl.aybolali.plnkztexchangebot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Собственный профиль пользователя (GET /api/users/me)
 */
public record UserProfileDTO(
        Long id,
        String telegramUsername,
        String firstName,
        String lastName,
        String phone,
        @JsonProperty("rating") BigDecimal trustRating,
        Integer successfulDeals,
        Boolean isPhoneVerified,
        Boolean isEnabled,
        @JsonFormat(pattern = "HH:mm")
        LocalDateTime createdAt
) {}