package pl.aybolali.plnkztexchangebot.mapper;

import org.springframework.stereotype.Component;
import pl.aybolali.plnkztexchangebot.dto.RatingDTO;
import pl.aybolali.plnkztexchangebot.entity.Rating;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 🔄 RATING MAPPER: Конвертеры Entity ↔ DTO для Rating
 *
 * Централизованное место для всех преобразований Rating.
 * Используется в системе рейтингов пользователей.
 */
@Component
public class RatingMapper {

    /**
     * 📤 ENTITY TO DTO: Rating → RatingDTO
     *
     * Основной метод конвертации рейтинга
     */
    public static RatingDTO toDTO(Rating rating) {
        if (rating == null) return null;

        return new RatingDTO(
                rating.getId(),
                rating.getDeal().getId(),
                UserMapper.toPublicDTO(rating.getRater()),
                UserMapper.toPublicDTO(rating.getRatedUser()),
                rating.getRating(),
                rating.getCreatedAt()
        );
    }

    /**
     * 📤 LIST CONVERSION: List<Rating> → List<RatingDTO>
     *
     * Удобный метод для конвертации списков рейтингов
     */
    public static List<RatingDTO> toDTOList(List<Rating> ratings) {
        if (ratings == null) return Collections.emptyList();

        return ratings.stream()
                .map(RatingMapper::toDTO)
                .collect(Collectors.toList());
    }
}