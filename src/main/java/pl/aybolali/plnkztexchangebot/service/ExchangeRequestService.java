package pl.aybolali.plnkztexchangebot.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequest;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequestStatus;
import pl.aybolali.plnkztexchangebot.entity.TransferMethod;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.exception.BusinessException;
import pl.aybolali.plnkztexchangebot.repository.ExchangeRequestRepository;
import pl.aybolali.plnkztexchangebot.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;

/**
 * Сервис для работы с заявками на обмен валют
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRequestService {

    private final ExchangeRequestRepository exchangeRequestRepository;
    private final UserRepository userRepository;

    @Transactional
    public Page<ExchangeRequest> getAllActiveRequests(Pageable pageable) {
        return exchangeRequestRepository.findAllActiveRequests(pageable);
    }

    @Transactional
    public List<ExchangeRequest> getActiveByUserId(Long userId) {
        return exchangeRequestRepository.findByUserIdAndStatus(userId, ExchangeRequestStatus.ACTIVE);
    }

    @Transactional
    public Page<ExchangeRequest> getUserRequests(Long userId, Pageable pageable) {
        return exchangeRequestRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public Page<ExchangeRequest> getRequestsByCurrency(ExchangeRequest.Currency currency, Pageable pageable) {
        return exchangeRequestRepository.findActiveByCurrency(currency, pageable);
    }


    public ExchangeRequest findById(Long id) {
        return exchangeRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Exchange request not found: " + id));
    }

    @Transactional
    public ExchangeRequest createExchangeRequest(Long userId, String currency, BigDecimal amount,
                                                 TransferMethod transferMethod, String comment) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<ExchangeRequest> activeRequests = getActiveByUserId(userId);
        if (activeRequests.size() >= 5) {
            throw new BusinessException("У вас уже есть 5 активных заявок. Закройте одну из существующих через /my_requests");
        }

        if (amount.compareTo(BigDecimal.TEN) < 0) {
            throw new BusinessException("Минимальная сумма: 10");
        }

        if (!currency.equals("PLN") && !currency.equals("KZT")) {
            throw new BusinessException("Неверная валюта. Используйте PLN или KZT");
        }

        ExchangeRequest request = ExchangeRequest.builder()
                .user(user)
                .currencyNeed(ExchangeRequest.Currency.valueOf(currency))  // Валюта которую хочет получить
                .amountNeed(amount)      // Сумма которую хочет получить
                .status(ExchangeRequestStatus.ACTIVE)
                .transferMethod(transferMethod)
                .notes(comment != null ? comment.trim() : null)
                .build();

        ExchangeRequest savedRequest = exchangeRequestRepository.save(request);

        log.info("✅ Created exchange request: user={}, wants {} {}, method={}",
                user.getTelegramUsername(), amount, currency, transferMethod);

        return savedRequest;
    }

    @Transactional
    public ExchangeRequest updateExchangeRequest(Long requestId, BigDecimal newAmount, String newComment) {
        ExchangeRequest request = findById(requestId);

        if (!request.getStatus().equals(ExchangeRequestStatus.ACTIVE)) {
            throw new IllegalStateException("Нельзя изменить неактивную заявку");
        }

        if (newAmount != null) {
            if (newAmount.compareTo(BigDecimal.TEN) < 0) {
                throw new IllegalArgumentException("Минимальная сумма: 10");
            }
            request.setAmountNeed(newAmount);
        }

        if (newComment != null) {
            request.setNotes(newComment.trim());
        }

        ExchangeRequest updated = exchangeRequestRepository.save(request);
        log.info("Updated exchange request: ID={}", requestId);

        return updated;
    }

    @Transactional
    public ExchangeRequest findByIdWithUser(Long id) {
        return exchangeRequestRepository.findByIdWithUser(id)
                .orElseThrow(() -> new RuntimeException("Заявка не найдена с ID: " + id));
    }

    @Transactional
    public ExchangeRequest cancelExchangeRequest(Long requestId, Long userId) {
        ExchangeRequest request = findById(requestId);

        if (!request.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Вы не можете отменить чужую заявку");
        }

        if (!request.getStatus().equals(ExchangeRequestStatus.ACTIVE)) {
            throw new IllegalStateException("Заявка уже завершена");
        }

        request.setStatus(ExchangeRequestStatus.CANCELLED);
        request.setFinishedAt(LocalDateTime.now());

        ExchangeRequest cancelled = exchangeRequestRepository.save(request);
        log.info("Cancelled exchange request: ID={}, user={}", requestId, userId);

        return cancelled;
    }

    @Transactional
    public void updateAfterPartialDeal(Long requestId, BigDecimal dealAmount) {
        ExchangeRequest request = findById(requestId);

        if (request.getAmountNeed().compareTo(dealAmount) < 0) {
            throw new IllegalArgumentException("Сумма сделки больше чем доступно в заявке");
        }

        BigDecimal remainingAmount = request.getAmountNeed().subtract(dealAmount);
        request.setAmountNeed(remainingAmount);

        if (remainingAmount.compareTo(BigDecimal.TEN) < 0) {
            log.info("Auto-closing small remainder: {} {}", remainingAmount, request.getCurrencyNeed());
            request.setAmountNeed(BigDecimal.ZERO);
            request.setStatus(ExchangeRequestStatus.COMPLETED);
            request.setFinishedAt(LocalDateTime.now());
        }

        save(request);

        log.info("Updated request {} after deal: remaining = {} {}",
                requestId, request.getAmountNeed(), request.getCurrencyNeed());
    }

    public void save(ExchangeRequest request) {
        exchangeRequestRepository.save(request);
    }

    @Transactional
    public List<ExchangeRequest> findActiveByCurrency(ExchangeRequest.Currency currency) {
        return exchangeRequestRepository.findByCurrencyNeedAndStatusOrderByCreatedAtDesc(
                currency,
                ExchangeRequestStatus.ACTIVE
        );
    }
}