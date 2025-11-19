package pl.aybolali.plnkztexchangebot.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.aybolali.plnkztexchangebot.dto.ApiResponseDTO;
import pl.aybolali.plnkztexchangebot.dto.CreateRatingDTO;
import pl.aybolali.plnkztexchangebot.dto.PagedResponseDTO;
import pl.aybolali.plnkztexchangebot.dto.RatingDTO;
import pl.aybolali.plnkztexchangebot.entity.Rating;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.mapper.RatingMapper;
import pl.aybolali.plnkztexchangebot.service.RatingService;
import pl.aybolali.plnkztexchangebot.service.UserService;

@RestController
@RequestMapping("/api/ratings")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class RatingController {

    private final RatingService ratingService;
    private final UserService userService;

    // ================================
    // ПУБЛИЧНЫЕ ENDPOINTS
    // ================================

    /**
     * Получить рейтинги пользователя
     * GET /api/ratings/user/123?page=0&size=10
     */
    @GetMapping("/user/{userId}")
    public ApiResponseDTO<PagedResponseDTO<RatingDTO>> getUserRatings(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("Getting ratings for user: {}, page: {}, size: {}", userId, page, size);

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Rating> ratings = ratingService.getUserRatings(userId, pageable);
            Page<RatingDTO> ratingDTOs = ratings.map(RatingMapper::toDTO);

            return ApiResponseDTO.success(PagedResponseDTO.of(ratingDTOs));

        } catch (Exception e) {
            log.error("Error getting ratings for user: " + userId, e);
            return ApiResponseDTO.error("Ошибка получения рейтингов пользователя");
        }
    }

    /**
     * Получить средний рейтинг пользователя (например: 4.96)
     * GET /api/ratings/user/123/average
     */
    @GetMapping("/user/{userId}/average")
    public ApiResponseDTO<Double> getUserAverageRating(@PathVariable Long userId) {
        log.info("Getting average rating for user: {}", userId);

        try {
            RatingService.UserRatingStats stats = ratingService.getUserRatingStats(userId);
            double averageRating = stats.averageRating();

            // Округляем до 2 знаков после запятой для красоты (например: 4.96)
            double roundedRating = Math.round(averageRating * 100.0) / 100.0;

            return ApiResponseDTO.success(roundedRating,
                    String.format("Средний рейтинг: %.2f из %d оценок", roundedRating, stats.totalRatings()));

        } catch (Exception e) {
            log.error("Error getting average rating for user: " + userId, e);
            return ApiResponseDTO.error("Ошибка получения среднего рейтинга");
        }
    }

    /**
     * Получить статистику рейтингов пользователя
     * GET /api/ratings/user/123/stats
     */
    @GetMapping("/user/{userId}/stats")
    public ApiResponseDTO<RatingService.UserRatingStats> getUserRatingStats(@PathVariable Long userId) {
        log.info("Getting rating stats for user: {}", userId);

        try {
            RatingService.UserRatingStats stats = ratingService.getUserRatingStats(userId);
            return ApiResponseDTO.success(stats, "Статистика рейтингов получена успешно");

        } catch (Exception e) {
            log.error("Error getting rating stats for user: " + userId, e);
            return ApiResponseDTO.error("Ошибка получения статистики рейтингов");
        }
    }

    /**
     * Получить рейтинги сделки
     * GET /api/ratings/deal/123?page=0&size=10
     */
    @GetMapping("/deal/{dealId}")
    public ApiResponseDTO<PagedResponseDTO<RatingDTO>> getDealRatings(
            @PathVariable Long dealId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("Getting ratings for deal: {}, page: {}, size: {}", dealId, page, size);

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<Rating> ratings = ratingService.getDealRatings(dealId, pageable);
            Page<RatingDTO> ratingDTOs = ratings.map(RatingMapper::toDTO);

            return ApiResponseDTO.success(PagedResponseDTO.of(ratingDTOs));

        } catch (Exception e) {
            log.error("Error getting ratings for deal: " + dealId, e);
            return ApiResponseDTO.error("Ошибка получения рейтингов сделки");
        }
    }

    // ================================
    // АВТОРИЗОВАННЫЕ ENDPOINTS
    // ================================

    /**
     * Создать оценку после сделки
     * POST /api/ratings
     * Body: {"dealId": 1, "rater": 2, "rating": 4.7}
     */
    @PostMapping
    public ApiResponseDTO<RatingDTO> createRating(
            @Valid @RequestBody CreateRatingDTO dto,
            Authentication auth) {

        String username = auth.getName();
        log.info("Creating rating for deal {} by user: {}", dto.dealId(), username);

        try {
            // ✅ ИСПРАВЛЕНО: Используем правильный метод с 2 параметрами
            Rating rating = ratingService.createRating(dto, username);

            RatingDTO responseDTO = RatingMapper.toDTO(rating);
            String message = String.format(
                    "%s оценил %s и сделку номер id: %d на %.1f из 5.0",
                    rating.getRater().getTelegramUsername(),
                    rating.getRatedUser().getTelegramUsername(),
                    rating.getDeal().getId(),
                    rating.getRating()
            );

            return ApiResponseDTO.success(responseDTO, message);

        } catch (RuntimeException e) {
            log.warn("Cannot create rating: {}", e.getMessage());
            return ApiResponseDTO.error(e.getMessage());

        } catch (Exception e) {
            log.error("Error creating rating for user: " + username, e);
            return ApiResponseDTO.error("Ошибка создания оценки");
        }
    }

    /**
     * Проверить оценил ли пользователь сделку
     * GET /api/ratings/check?dealId=123
     */
    @GetMapping("/check")
    public ApiResponseDTO<Boolean> checkUserRatedDeal(
            @RequestParam Long dealId,
            Authentication auth) {

        String username = auth.getName();
        log.info("Checking if user {} rated deal {}", username, dealId);

        try {
            User currentUser = getCurrentUser(auth);
            boolean hasRated = ratingService.existsByDealIdAndRaterId(dealId, currentUser.getId());

            String message = hasRated ? "Пользователь уже оценил эту сделку" : "Пользователь еще не оценил эту сделку";
            return ApiResponseDTO.success(hasRated, message);

        } catch (Exception e) {
            log.error("Error checking rating for user: " + username, e);
            return ApiResponseDTO.error("Ошибка проверки оценки");
        }
    }

    private User getCurrentUser(Authentication auth) {
        String username = auth.getName();
        return userService.findByTelegramUsername(username)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }
}