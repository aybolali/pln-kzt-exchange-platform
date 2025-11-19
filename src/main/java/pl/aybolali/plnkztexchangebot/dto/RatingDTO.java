package pl.aybolali.plnkztexchangebot.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RatingDTO(
        Long id,
        Long dealId,
        UserPublicDTO rater,
        UserPublicDTO ratedUser,
        BigDecimal rating,
        LocalDateTime createdAt
) {}