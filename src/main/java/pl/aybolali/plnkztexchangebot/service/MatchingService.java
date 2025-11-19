package pl.aybolali.plnkztexchangebot.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequest;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequestStatus;
import pl.aybolali.plnkztexchangebot.repository.ExchangeRequestRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MatchingService {

    private final ExchangeRequestRepository exchangeRequestRepository;
    private final RatingService ratingService;
    private final ExchangeRateService exchangeRateService;

    public List<ExchangeRequest> findMatchingOffers(Long userId, ExchangeRequest.Currency currency,
                                                    int limit, BigDecimal targetAmount) {
        log.info("Finding smart offers for currency {} (excluding user {}), target amount: {}", currency, userId, targetAmount);

        try {

            log.debug("User has {} and wants to exchange → searching for people who NEED {}",
                    currency == ExchangeRequest.Currency.PLN ? "KZT" : "PLN", currency);

            Pageable pageable = PageRequest.of(0, 1000);
            Page<ExchangeRequest> offersPage = exchangeRequestRepository.findActiveByCurrency(currency, pageable);
            List<ExchangeRequest> allOffers = offersPage.getContent();

            List<ExchangeRequest> smartOffers = allOffers.stream()
                    .filter(offer -> !offer.getUser().getId().equals(userId))
                    .sorted((offer1, offer2) -> {
                        double score1 = calculateOfferScore(offer1, targetAmount, currency);
                        double score2 = calculateOfferScore(offer2, targetAmount, currency);

                        int scoreCompare = Double.compare(score2, score1);

                        if (scoreCompare == 0) {
                            return Long.compare(offer1.getId(), offer2.getId());
                        }

                        return scoreCompare;
                    })
                    .limit(limit)
                    .collect(Collectors.toList());

            log.info("Smart matching: {} total candidates → {} top offers selected (user {} excluded)",
                    allOffers.size(), smartOffers.size(), userId);

            if (targetAmount != null && !smartOffers.isEmpty()) {
                smartOffers.forEach(offer -> {
                    double userRating = getUserRating(offer.getUser().getId());
                    double totalScore = calculateOfferScore(offer, targetAmount, currency);

                    log.debug("Selected: {} {}, User: {} ({}★), Score: {}",
                            offer.getAmountNeed(), offer.getCurrencyNeed(),
                            offer.getUser().getTelegramUsername(),
                            String.format("%.1f", userRating), String.format("%.1f", totalScore));
                });
            }

            return smartOffers;

        } catch (Exception e) {
            log.error("Error finding smart matching offers: {}", e.getMessage());
            return List.of();
        }
    }

    public List<ExchangeRequest> findMatchingOffers(Long userId, ExchangeRequest.Currency currency, int limit) {
        return findMatchingOffers(userId, currency, limit, null);
    }

    public List<ExchangeRequest> findCounterOffers(Long userId) {
        log.info("Finding counter offers for user: {}", userId);

        try {
            List<ExchangeRequest> userRequests = exchangeRequestRepository
                    .findByUserIdAndStatus(userId, ExchangeRequestStatus.ACTIVE);

            if (userRequests.isEmpty()) {
                log.info("User {} has no active requests", userId);
                return List.of();
            }

            ExchangeRequest userRequest = userRequests.get(0);

            ExchangeRequest.Currency oppositeCurrency = userRequest.getCurrencyNeed() == ExchangeRequest.Currency.PLN
                    ? ExchangeRequest.Currency.KZT
                    : ExchangeRequest.Currency.PLN;

            List<ExchangeRequest> counterOffers = findMatchingOffers(
                    userId, oppositeCurrency, 5, userRequest.getAmountNeed());

            log.info("Found {} smart counter offers for user {}", counterOffers.size(), userId);
            return counterOffers;

        } catch (Exception e) {
            log.error("Error finding counter offers: {}", e.getMessage());
            return List.of();
        }
    }

    private double calculateOfferScore(ExchangeRequest offer, BigDecimal targetAmount, ExchangeRequest.Currency targetCurrency) {
        double proximityScore = 58.0; // Базовый балл за близость (макс 58)
        double ratingScore = getRatingScore(offer.getUser().getId());
        double bonusScore = getBonusScore(offer);

        if (targetAmount != null) {
            BigDecimal comparableOfferAmount = offer.getAmountNeed();
            BigDecimal comparableTargetAmount = targetAmount;

            if (offer.getCurrencyNeed() != targetCurrency) {
                try {
                    if (offer.getCurrencyNeed() == ExchangeRequest.Currency.KZT && targetCurrency == ExchangeRequest.Currency.PLN) {
                        // KZT → PLN
                        BigDecimal kztToPln = exchangeRateService.getCurrentKZTtoPLNRate();
                        comparableOfferAmount = offer.getAmountNeed().multiply(kztToPln);
                    } else if (offer.getCurrencyNeed() == ExchangeRequest.Currency.PLN && targetCurrency == ExchangeRequest.Currency.KZT) {
                        // PLN → KZT
                        BigDecimal plnToKzt = exchangeRateService.getCurrentPLNtoKZTRate();
                        comparableOfferAmount = offer.getAmountNeed().multiply(plnToKzt);
                    }
                } catch (Exception e) {
                    log.debug("Error converting currencies for score calculation: {}", e.getMessage());
                }
            }

            double diff = Math.abs(comparableOfferAmount.subtract(comparableTargetAmount).doubleValue());
            double maxAmount = Math.max(comparableOfferAmount.doubleValue(), comparableTargetAmount.doubleValue());

            proximityScore = Math.max(58 - (diff / maxAmount * 58), 0);

            log.debug("Score calculation: offer={} {} vs target={} {} → comparable={} vs {} → proximity={}",
                    offer.getAmountNeed(), offer.getCurrencyNeed(),
                    targetAmount, targetCurrency,
                    comparableOfferAmount, comparableTargetAmount, proximityScore);
        }

        double totalScore = proximityScore + ratingScore + bonusScore;
        return Math.min(totalScore, 100.0);
    }

    private double getRatingScore(Long userId) {
        try {
            double userRating = getUserRating(userId);
            // Конвертируем рейтинг 0-5 в баллы 0-32
            double ratingScore = userRating * 6.4; // 5★ = 32 балла
            return Math.max(0, Math.min(ratingScore, 32));
        } catch (Exception e) {
            log.warn("Error getting rating for user {}: {}", userId, e.getMessage());
            return 3.2; // Средний балл если ошибка (0.5★ * 6.4)
        }
    }


    private double getBonusScore(ExchangeRequest offer) {
        double bonus = 0.0;

        try {
            // Бонус за количество успешных сделок (макс +7 баллов)
            int successfulDeals = offer.getUser().getSuccessfulDeals();
            bonus += Math.min(successfulDeals * 0.7, 7.0);

            // Бонус за свежесть запроса (макс +3 балла)
            long hoursAgo = java.time.Duration.between(offer.getCreatedAt(),
                    java.time.LocalDateTime.now()).toHours();
            if (hoursAgo <= 1) bonus += 3.0;
            else if (hoursAgo <= 6) bonus += 2.0;
            else if (hoursAgo <= 24) bonus += 1.0;

        } catch (Exception e) {
            log.debug("Error calculating bonus for offer {}: {}", offer.getId(), e.getMessage());
        }

        return Math.min(bonus, 10.0); // Максимум 10 бонусных баллов
    }

    private double getUserRating(Long userId) {
        try {
            Double actualRating = ratingService.getActualUserRating(userId);
            return actualRating != null ? actualRating : 0.0;
        } catch (Exception e) {
            log.warn("Error getting actual user rating for {}: {}", userId, e.getMessage());
            return 0.0;
        }
    }
}