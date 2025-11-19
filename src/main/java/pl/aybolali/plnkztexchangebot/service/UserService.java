package pl.aybolali.plnkztexchangebot.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.repository.DealRepository;
import pl.aybolali.plnkztexchangebot.repository.RatingRepository;
import pl.aybolali.plnkztexchangebot.repository.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final DealRepository dealRepository;
    private final RatingRepository ratingRepository;

    public Optional<User> findByTelegramUserId(Long telegramUserId) {
        return userRepository.findByTelegramUserId(telegramUserId);
    }

    public Optional<User> findByTelegramUsername(String telegramUsername) {
        return userRepository.findByTelegramUsername(telegramUsername);
    }

    @Transactional
    public User registerUser(Long telegramUserId, String telegramUsername, String firstName, String lastName) {

        Optional<User> existing = userRepository.findByTelegramUserId(telegramUserId);
        if (existing.isPresent()) {
            log.debug("User already exists: {}", telegramUserId);
            return existing.get();
        }

        if (telegramUsername == null || telegramUsername.isBlank()) {
            throw new IllegalArgumentException("Telegram username is required");
        }

        User user = User.builder()
                .telegramUserId(telegramUserId)
                .telegramUsername(telegramUsername)
                .firstName(firstName)
                .lastName(lastName)
                .trustRating(BigDecimal.ZERO)
                .successfulDeals(0)
                .isPhoneVerified(false)
                .isEnabled(true)
                .build();

        User savedUser = save(user);
        log.info("Registered new user: ID={}, username={}", telegramUserId, telegramUsername);
        return savedUser;
    }

    public List<User> getAllUsersSorted() {
        return userRepository.findAllActiveUsers()
                .stream()
                .sorted((u1, u2) -> {
                    double score1 = calculateUserScore(u1.getId());
                    double score2 = calculateUserScore(u2.getId());
                    return Double.compare(score2, score1); // От лучших к худшим
                })
                .collect(Collectors.toList());
    }

    private Double getActualRating(Long userId) {
        Double rating = ratingRepository.getAverageRatingByUserId(userId);
        return rating != null ? rating : 0.0;
    }

    private Long getCompletedDealsCount(Long userId) {
        return dealRepository.countCompletedByUserId(userId);
    }

    private double calculateUserScore(Long userId) {
        Double rating = getActualRating(userId);
        Long deals = getCompletedDealsCount(userId);

        // Защита от случайных 5★ у новичков + разумный бонус за опыт
        double experienceBonus = Math.min(deals * 0.1, 2.0);  // максимум +2.0

        return rating + experienceBonus;
    }

    @Transactional
    public void updateUserStatsAfterDeal(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        // ✅ СЧИТАЕМ РЕАЛЬНОЕ количество сделок из БД!
        Long actualDealsCount = getCompletedDealsCount(userId);
        user.setSuccessfulDeals(actualDealsCount.intValue());

        // Обновляем trust_rating из реальных оценок
        Double actualRating = getActualRating(userId);
        user.setTrustRating(BigDecimal.valueOf(actualRating));

        userRepository.save(user);
        log.info("Updated stats for user {}: deals={}, rating={}",
                userId, user.getSuccessfulDeals(), user.getTrustRating());
    }

    @Transactional
    public void updateTrustRating(Long userId, Double averageRating) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        BigDecimal roundedRating = BigDecimal.valueOf(Math.round(averageRating * 100.0) / 100.0);

        user.setTrustRating(roundedRating);
        userRepository.save(user);

        log.debug("Updated trust_rating for user {}: {}", userId, roundedRating);
    }

    public User save(User user) {
        log.debug("Saving user: {}", user.getTelegramUsername());
        return userRepository.save(user);
    }
}