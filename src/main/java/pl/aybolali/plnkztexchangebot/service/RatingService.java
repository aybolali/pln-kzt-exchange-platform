package pl.aybolali.plnkztexchangebot.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pl.aybolali.plnkztexchangebot.dto.CreateRatingDTO;
import pl.aybolali.plnkztexchangebot.entity.Deal;
import pl.aybolali.plnkztexchangebot.entity.Rating;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.repository.DealRepository;
import pl.aybolali.plnkztexchangebot.repository.RatingRepository;
import pl.aybolali.plnkztexchangebot.repository.UserRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RatingService {
    private final RatingRepository ratingRepository;
    private final DealRepository dealRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public Page<Rating> getDealRatings(Long dealId, Pageable pageable) {
        return ratingRepository.findByDealIdOrderByCreatedAtDesc(dealId, pageable);
    }

    public Page<Rating> getUserRatings(Long userId, Pageable pageable) {
        return ratingRepository.findByRatedUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    public UserRatingStats getUserRatingStats(Long userId) {
        Double averageRating = ratingRepository.getAverageRatingByUserId(userId);
        Long totalRatings = ratingRepository.countRatingsByUserId(userId);

        return new UserRatingStats(
                userId,
                averageRating != null ? averageRating : 0.0,
                totalRatings != null ? totalRatings : 0L
        );
    }

    @Transactional
    public Rating createRating(CreateRatingDTO dto, String raterUsername) {
        log.info("Creating rating for deal {} by user {}", dto.dealId(), raterUsername);

        Deal deal = dealRepository.findById(dto.dealId())
                .orElseThrow(() -> new RuntimeException("Deal not found with id: " + dto.dealId()));

        User rater = userRepository.findByTelegramUsername(raterUsername)
                .orElseThrow(() -> new RuntimeException("Rater not found: " + raterUsername));


        if (!deal.isUserParticipant(rater.getId())) {
            throw new RuntimeException("User is not a participant in this deal");
        }

        User ratedUser;
        if (deal.getRequester().getId().equals(rater.getId())) {
            ratedUser = deal.getProvider();
        } else {
            ratedUser = deal.getRequester();
        }

        if (ratingRepository.existsByDealIdAndRaterId(dto.dealId(), rater.getId())) {
            throw new RuntimeException("Rating already exists for this deal by this user");
        }

        Rating rating = Rating.builder()
                .deal(deal)
                .rater(rater)
                .ratedUser(ratedUser)
                .rating(dto.rating())
                .createdAt(LocalDateTime.now())
                .build();

        Rating savedRating = ratingRepository.save(rating);

        updateUserTrustRating(ratedUser.getId());

        log.info("Rating created: {} stars for user {} in deal {}",
                dto.rating(), ratedUser.getTelegramUsername(), dto.dealId());

        return savedRating;
    }

    private void updateUserTrustRating(Long userId) {
        try {
            UserRatingStats stats = getUserRatingStats(userId);
            if (stats.totalRatings() > 0) {
                userService.updateTrustRating(userId, stats.averageRating());

                log.info("Updated trust_rating for user {}: {} (based on {} ratings)",
                        userId, stats.averageRating(), stats.totalRatings());
            }
        } catch (Exception e) {
            log.error("Failed to update trust_rating for user {}: {}", userId, e.getMessage());
        }
    }

    public boolean existsByDealIdAndRaterId(Long dealId, Long raterId) {
        return ratingRepository.existsByDealIdAndRaterId(dealId, raterId);
    }


    public Double getActualUserRating(Long userId) {
        UserRatingStats stats = getUserRatingStats(userId);
        return stats.averageRating();
    }
    public record UserRatingStats(Long userId, Double averageRating, Long totalRatings) {}
}