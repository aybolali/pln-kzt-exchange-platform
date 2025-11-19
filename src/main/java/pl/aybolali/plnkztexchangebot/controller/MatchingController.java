package pl.aybolali.plnkztexchangebot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.aybolali.plnkztexchangebot.dto.ApiResponseDTO;
import pl.aybolali.plnkztexchangebot.dto.ExchangeRequestDTO;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequest;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.mapper.ExchangeRequestMapper;
import pl.aybolali.plnkztexchangebot.service.ExchangeRateService;
import pl.aybolali.plnkztexchangebot.service.MatchingService;
import pl.aybolali.plnkztexchangebot.service.UserService;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/matching")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class MatchingController {

    private final MatchingService matchingService;
    private final UserService userService;
    private final ExchangeRateService exchangeRateService;


    @GetMapping("/search")
    public ApiResponseDTO<List<ExchangeRequestDTO>> searchOffers(
            @RequestParam ExchangeRequest.Currency currency,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(required = false) BigDecimal targetAmount,
            Authentication auth) {

        try {
            User currentUser = getCurrentUser(auth);

            List<ExchangeRequest> offers = matchingService.findMatchingOffers(
                    currentUser.getId(), currency, limit, targetAmount);

            List<ExchangeRequestDTO> offerDTOs = offers.stream()
                    .map(ExchangeRequestMapper::toDTO)
                    .toList();

            String message = buildSearchMessage(currency, targetAmount, offers.size());

            return ApiResponseDTO.success(offerDTOs, message);

        } catch (Exception e) {
            log.error("Error searching offers", e);
            return ApiResponseDTO.error("Ошибка поиска предложений");
        }
    }

    @GetMapping("/my-matches")
    public ApiResponseDTO<List<ExchangeRequestDTO>> getMyMatches(Authentication auth) {
        try {
            User currentUser = getCurrentUser(auth);

            List<ExchangeRequest> matches = matchingService.findCounterOffers(currentUser.getId());

            List<ExchangeRequestDTO> matchDTOs = matches.stream()
                    .map(ExchangeRequestMapper::toDTO)
                    .toList();

            String message = String.format("Найдено %d встречных предложений", matches.size());

            return ApiResponseDTO.success(matchDTOs, message);

        } catch (Exception e) {
            log.error("Error getting matches", e);
            return ApiResponseDTO.error("Ошибка получения встречных предложений");
        }
    }

    private User getCurrentUser(Authentication auth) {
        return userService.findByTelegramUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }

    /**
     * Создает информативное сообщение с актуальным курсом валют
     */
    private String buildSearchMessage(ExchangeRequest.Currency currency, BigDecimal targetAmount, int foundCount) {
        try {
            if (targetAmount != null) {
                // Получаем актуальный курс для информации
                BigDecimal currentRate = (currency == ExchangeRequest.Currency.PLN) ?
                        exchangeRateService.getCurrentKZTtoPLNRate() :
                        exchangeRateService.getCurrentPLNtoKZTRate();

                String rateInfo = String.format("курс %s→%s: %.6f",
                        currency == ExchangeRequest.Currency.PLN ? "KZT" : "PLN",
                        currency,
                        currentRate.doubleValue());

                return String.format("Найдено %d ближайших предложений к %s %s (%s)",
                        foundCount, targetAmount, currency, rateInfo);
            } else {
                return String.format("Найдено %d доступных предложений", foundCount);
            }
        } catch (Exception e) {
            log.debug("Error getting rate for message: {}", e.getMessage());
            return String.format("Найдено %d предложений", foundCount);
        }
    }
}