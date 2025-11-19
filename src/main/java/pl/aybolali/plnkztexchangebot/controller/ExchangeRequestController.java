package pl.aybolali.plnkztexchangebot.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.aybolali.plnkztexchangebot.dto.*;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequest;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequestStatus;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.mapper.ExchangeRequestMapper;
import pl.aybolali.plnkztexchangebot.service.ExchangeRequestService;
import pl.aybolali.plnkztexchangebot.service.UserService;

@RestController
@RequestMapping("/api/exchange-requests")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ExchangeRequestController {

    private final ExchangeRequestService exchangeRequestService;
    private final UserService userService;

    @GetMapping
    public ApiResponseDTO<PagedResponseDTO<ExchangeRequestDTO>> getAllRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ExchangeRequest.Currency currency) {

        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<ExchangeRequest> requests = currency != null ?
                    exchangeRequestService.getRequestsByCurrency(currency, pageable) :
                    exchangeRequestService.getAllActiveRequests(pageable);

            Page<ExchangeRequestDTO> requestDTOs = requests.map(ExchangeRequestMapper::toDTO);
            String message = currency != null ?
                    String.format("Активных запросов %s: %d", currency, requests.getTotalElements()) :
                    String.format("Активных запросов на обмен: %d", requests.getTotalElements());

            return ApiResponseDTO.success(PagedResponseDTO.of(requestDTOs), message);

        } catch (Exception e) {
            log.error("Error getting exchange requests", e);
            return ApiResponseDTO.error("Ошибка получения запросов обмена");
        }
    }

    @GetMapping("/{id}")
    public ApiResponseDTO<ExchangeRequestDTO> getRequestById(@PathVariable Long id) {
        try {
            ExchangeRequest request = exchangeRequestService.findById(id);
            ExchangeRequestDTO responseDTO = ExchangeRequestMapper.toDTO(request);
            return ApiResponseDTO.success(responseDTO);
        } catch (RuntimeException e) {
            return ApiResponseDTO.error("Запрос не найден");
        } catch (Exception e) {
            log.error("Error getting exchange request by id: {}", id, e);
            return ApiResponseDTO.error("Ошибка получения запроса");
        }
    }

    @PostMapping
    public ApiResponseDTO<ExchangeRequestDTO> createRequest(
            @Valid @RequestBody CreateExchangeRequestDTO dto, Authentication auth) {

        try {
            User user = getCurrentUser(auth);
            ExchangeRequest request = exchangeRequestService.createExchangeRequest(
                    user.getId(), dto.currencyNeed(), dto.amountNeed(), dto.transferMethod(), dto.notes());

            ExchangeRequestDTO responseDTO = ExchangeRequestMapper.toDTO(request);
            return ApiResponseDTO.success(responseDTO, "Запрос успешно создан");

        } catch (Exception e) {
            log.error("Error creating exchange request", e);
            return ApiResponseDTO.error("Ошибка создания запроса");
        }
    }

    @PutMapping("/{id}")
    public ApiResponseDTO<ExchangeRequestDTO> updateRequest(
            @PathVariable Long id, @Valid @RequestBody UpdateExchangeRequestDTO dto, Authentication auth) {

        try {
            User currentUser = getCurrentUser(auth);
            ExchangeRequest request = exchangeRequestService.findById(id);

            if (!request.getUser().getId().equals(currentUser.getId())) {
                return ApiResponseDTO.error("Нет прав для редактирования");
            }

            if (dto.amountNeed() != null && !request.isActive()) {
                return ApiResponseDTO.error("Невозможно обновить сумму");
            }

            request = exchangeRequestService.updateExchangeRequest(request.getId(), dto.amountNeed(), dto.notes());
            ExchangeRequestDTO responseDTO = ExchangeRequestMapper.toDTO(request);

            return ApiResponseDTO.success(responseDTO, "Запрос успешно обновлен");

        } catch (RuntimeException e) {
            return ApiResponseDTO.error("Запрос не найден");
        } catch (Exception e) {
            log.error("Error updating exchange request: {}", id, e);
            return ApiResponseDTO.error("Ошибка обновления запроса");
        }
    }

    @PutMapping("/{id}/cancel")
    public ApiResponseDTO<ExchangeRequestDTO> cancelRequest(@PathVariable Long id, Authentication auth) {
        try {
            User currentUser = getCurrentUser(auth);
            ExchangeRequest cancelledRequest = exchangeRequestService.cancelExchangeRequest(id, currentUser.getId());

            ExchangeRequestDTO responseDTO = ExchangeRequestMapper.toDTO(cancelledRequest);
            String message = String.format("Запрос на %s %s отменен",
                    cancelledRequest.getAmountNeed(), cancelledRequest.getCurrencyNeed());

            return ApiResponseDTO.success(responseDTO, message);

        } catch (IllegalArgumentException e) {
            return ApiResponseDTO.error("Можете отменить только свои запросы");
        } catch (IllegalStateException e) {
            return ApiResponseDTO.error("Запрос уже завершен или отменен");
        } catch (Exception e) {
            log.error("Error cancelling exchange request: {}", id, e);
            return ApiResponseDTO.error("Ошибка отмены запроса");
        }
    }

    @GetMapping("/my")
    public ApiResponseDTO<PagedResponseDTO<ExchangeRequestDTO>> getMyRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {

        try {
            User currentUser = getCurrentUser(auth);
            Pageable pageable = PageRequest.of(page, size);

            Page<ExchangeRequest> requests = exchangeRequestService.getUserRequests(currentUser.getId(), pageable);
            Page<ExchangeRequestDTO> requestDTOs = requests.map(ExchangeRequestMapper::toDTO);

            long activeCount = requests.getContent().stream()
                    .mapToLong(req -> req.getStatus() == ExchangeRequestStatus.ACTIVE ? 1 : 0).sum();

            String message = String.format("Ваших запросов: %d, активных: %d",
                    requests.getTotalElements(), activeCount);

            return ApiResponseDTO.success(PagedResponseDTO.of(requestDTOs), message);

        } catch (Exception e) {
            log.error("Error getting user requests: {}", auth.getName(), e);
            return ApiResponseDTO.error("Ошибка получения ваших запросов");
        }
    }

    private User getCurrentUser(Authentication auth) {
        return userService.findByTelegramUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }
}