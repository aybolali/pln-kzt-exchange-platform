package pl.aybolali.plnkztexchangebot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.aybolali.plnkztexchangebot.dto.*;
import pl.aybolali.plnkztexchangebot.entity.Deal;
import pl.aybolali.plnkztexchangebot.entity.DealStatus;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequest;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.mapper.DealMapper;
import pl.aybolali.plnkztexchangebot.service.DealService;
import pl.aybolali.plnkztexchangebot.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/deals")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class DealController {

    private final DealService dealService;
    private final UserService userService;

    @GetMapping("/{id}")
    public ApiResponseDTO<DealDTO> getDealById(@PathVariable Long id) {
        try {
            Deal deal = dealService.findById(id);
            DealDTO dealDTO = DealMapper.toDTO(deal);
            return ApiResponseDTO.success(dealDTO);
        } catch (RuntimeException e) {
            return ApiResponseDTO.error("Сделка не найдена");
        } catch (Exception e) {
            log.error("Error getting deal by id: {}", id, e);
            return ApiResponseDTO.error("Ошибка получения сделки");
        }
    }

    /**
     * ⭐ ОБНОВЛЕНО: Создает СРАЗУ COMPLETED deal
     * Вызывается из Telegram бота после подтверждения
     */
    @PostMapping
    public ApiResponseDTO<DealDTO> createDeal(@Valid @RequestBody CreateDealDTO dto, Authentication auth) {
        try {
            User provider = getCurrentUser(auth);

            Deal deal = dealService.createDealFromRequest(
                    dto.requestId(), provider.getId(), dto.amount());

            DealDTO responseDTO = DealMapper.toDTO(deal);

            String message = buildDealMessage(deal);

            return ApiResponseDTO.success(responseDTO, message);

        } catch (IllegalArgumentException e) {
            log.warn("Deal creation failed: {}", e.getMessage());
            return ApiResponseDTO.error(e.getMessage());
        } catch (Exception e) {
            log.error("Error creating deal for user: {}", auth.getName(), e);
            return ApiResponseDTO.error("Ошибка создания сделки");
        }
    }

    @GetMapping("/my")
    public ApiResponseDTO<PagedResponseDTO<DealsListDTO>> getMyDeals(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        try {
            User currentUser = getCurrentUser(auth);
            Pageable pageable = PageRequest.of(page, size);

            Page<Deal> deals = dealService.getUserDeals(currentUser.getId(), pageable);
            Page<DealsListDTO> dealDTOs = deals.map(deal ->
                    DealsListDTO.fromDeal(deal, currentUser.getId()));

            long total = deals.getTotalElements();

            String message = String.format("Всего завершенных сделок: %d", total);

            return ApiResponseDTO.success(PagedResponseDTO.of(dealDTOs), message);

        } catch (Exception e) {
            log.error("Error getting user deals for: {}", auth.getName(), e);
            return ApiResponseDTO.error("Ошибка получения ваших сделок");
        }
    }

    private String buildDealMessage(Deal deal) {
        // Форматируем суммы и валюты с жирным шрифтом
        String providerGave = String.format("**%.2f %s**",
                deal.getAmount().doubleValue(),
                deal.getCurrency().name());

        String providerReceived = String.format("%.2f %s**",
                deal.getConvertedAmount().doubleValue(),
                deal.getOppositeCurrency().name());

        String requesterGave = String.format("%.2f %s**",
                deal.getConvertedAmount().doubleValue(),
                deal.getOppositeCurrency().name());

        String requesterReceived = String.format("**%.2f %s**",
                deal.getAmount().doubleValue(),
                deal.getCurrency().name());

        return String.format(
                "🎉 **Обмен завершен!**\n\n" +
                        "💰 %s отдал(а): %s\n" +
                        "✅ %s получил(а): %s\n\n" +
                        "💰 %s отдал(а): %s\n" +
                        "✅ %s получил(а): %s\n\n" +
                        "📊 Курс: **%.2f**\n" +
                        "🔗 ID сделки: **#%d**",

                deal.getProvider().getTelegramUsername(),
                providerGave,
                deal.getProvider().getTelegramUsername(),
                providerReceived,

                deal.getRequester().getTelegramUsername(),
                requesterGave,
                deal.getRequester().getTelegramUsername(),
                requesterReceived,

                deal.getExchangeRate().doubleValue(),
                deal.getId()
        );
    }

    private User getCurrentUser(Authentication auth) {
        return userService.findByTelegramUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }
}