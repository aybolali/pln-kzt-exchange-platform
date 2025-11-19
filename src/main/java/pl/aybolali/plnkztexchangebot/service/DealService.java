package pl.aybolali.plnkztexchangebot.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pl.aybolali.plnkztexchangebot.entity.*;
import pl.aybolali.plnkztexchangebot.repository.DealRepository;
import pl.aybolali.plnkztexchangebot.repository.ExchangeRequestRepository;
import pl.aybolali.plnkztexchangebot.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DealService {

    private final DealRepository dealRepository;
    private final ExchangeRequestRepository exchangeRequestRepository;
    private final UserRepository userRepository;
    private final ExchangeRequestService exchangeRequestService;
    private final UserService userService;
    private final ExchangeRateService exchangeRateService;

    private BigDecimal roundToCurrency(BigDecimal value) {
        return BigDecimal.valueOf(Math.round(value.doubleValue() * 100.0) / 100.0);
    }

    public Page<Deal> getUserDeals(Long userId, Pageable pageable) {
        return dealRepository.findAllUserDeals(userId, pageable);
    }

    public Page<Deal> getFinishedUserDeals(Long userId, Pageable pageable) {
        return dealRepository.findFinishedUserDeals(userId, pageable);
    }

    @Transactional
    public Deal findById(Long id) {
        return dealRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Сделка не найдена с ID: " + id));
    }


    @Transactional
    public Deal createDealFromRequest(Long requestId, Long providerId, BigDecimal dealAmount) {
        ExchangeRequest request = exchangeRequestService.findById(requestId);
        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found"));
        User requester = request.getUser();

        if (requester.getId().equals(provider.getId())) {
            throw new IllegalArgumentException("User cannot create deal with themselves");
        }
        if (!request.isActive()) {
            throw new IllegalArgumentException("Cannot create deal for inactive request");
        }

        ExchangeRequest.Currency dealCurrency = request.getCurrencyNeed();

        BigDecimal exchangeRate;

        if (dealCurrency == ExchangeRequest.Currency.PLN) {
            // Provider отдает PLN → курс PLN→KZT
            exchangeRate = exchangeRateService.getCurrentPLNtoKZTRate();
        } else {
            // Provider отдает KZT → курс KZT→PLN
            exchangeRate = exchangeRateService.getCurrentKZTtoPLNRate();
        }

        // Проверяем: полный или частичный обмен?
        BigDecimal actualReceivedAmount = dealAmount.min(request.getAmountNeed());
        boolean isFullExchange = actualReceivedAmount.compareTo(request.getAmountNeed()) >= 0;

        Deal deal = Deal.builder()
                .requester(requester)
                .provider(provider)
                .amount(dealAmount)
                .currency(dealCurrency)
                .exchangeRate(exchangeRate)
                .transferMethod(request.getTransferMethod())
                .status(DealStatus.COMPLETED)
                .createdAt(request.getCreatedAt())
                .finishedAt(LocalDateTime.now())
                .build();

        Deal savedDeal = dealRepository.save(deal);
        dealRepository.flush();

        if (isFullExchange) {
            log.info("Full exchange completed. Marking request {} as COMPLETED", request.getId());
            request.setStatus(ExchangeRequestStatus.COMPLETED);
            request.setFinishedAt(LocalDateTime.now());
            exchangeRequestRepository.save(request);
        } else {
            log.info("Partial exchange. Updating request {} amount", request.getId());
            exchangeRequestService.updateAfterPartialDeal(request.getId(), actualReceivedAmount);
        }

        updateCounterpartyRequest(provider.getId(), dealAmount, dealCurrency);

        userService.updateUserStatsAfterDeal(requester.getId());
        userService.updateUserStatsAfterDeal(provider.getId());

        log.info("Completed deal created: {} between {} and {}",
                savedDeal.getId(), requester.getTelegramUsername(), provider.getTelegramUsername());

        return savedDeal;
    }

    private void updateCounterpartyRequest(Long providerId, BigDecimal dealAmount,
                                           ExchangeRequest.Currency dealCurrency) {
        try {
            log.info("Updating counterparty request for provider: {}", providerId);

            ExchangeRequest.Currency oppositeCurrency = dealCurrency == ExchangeRequest.Currency.KZT
                    ? ExchangeRequest.Currency.PLN : ExchangeRequest.Currency.KZT;

            Optional<ExchangeRequest> counterRequest = exchangeRequestRepository
                    .findActiveRequestByUserAndCurrency(providerId, oppositeCurrency);

            if (counterRequest.isPresent()) {
                ExchangeRequest request = counterRequest.get();
                log.info("Found counterparty request ID: {} (wants {} {})",
                        request.getId(), request.getAmountNeed(), oppositeCurrency);

                BigDecimal receivedAmount;
                if (dealCurrency == ExchangeRequest.Currency.KZT) {
                    // Provider отдал KZT → получил PLN
                    BigDecimal kztToPLNRate = exchangeRateService.getCurrentKZTtoPLNRate();
                    receivedAmount = roundToCurrency(dealAmount.multiply(kztToPLNRate));
                    log.info("Conversion: {} KZT * {} = {} PLN", dealAmount, kztToPLNRate, receivedAmount);
                } else {
                    // Provider отдал PLN → получил KZT
                    BigDecimal plnToKZTRate = exchangeRateService.getCurrentPLNtoKZTRate();
                    receivedAmount = roundToCurrency(dealAmount.multiply(plnToKZTRate));
                    log.info("Conversion: {} PLN * {} = {} KZT", dealAmount, plnToKZTRate, receivedAmount);
                }

                boolean isFullExchange = receivedAmount.compareTo(request.getAmountNeed()) >= 0;
                BigDecimal actualReceivedAmount = receivedAmount.min(request.getAmountNeed());

                log.info("Provider wanted: {} {}, received: {} {}, actual: {} {}, isFullExchange: {}",
                        request.getAmountNeed(), oppositeCurrency,
                        receivedAmount, oppositeCurrency,
                        actualReceivedAmount, oppositeCurrency,
                        isFullExchange);

                if (isFullExchange) {
                    log.info("✅ Full exchange completed. Marking request {} as COMPLETED", request.getId());
                    request.setStatus(ExchangeRequestStatus.COMPLETED);
                    request.setFinishedAt(LocalDateTime.now());
                    exchangeRequestRepository.save(request);
                    exchangeRequestRepository.flush();
                } else {
                    log.info("📝 Partial exchange. Updating request {} amount", request.getId());
                    exchangeRequestService.updateAfterPartialDeal(request.getId(), actualReceivedAmount);
                }

                log.info("✅ Successfully updated counterparty request");
            } else {
                log.info("ℹ️ No counterparty request found for provider {}", providerId);
            }
        } catch (Exception e) {
            log.error("❌ Failed to update counterparty request: {}", e.getMessage(), e);
        }
    }

    public Deal save(Deal deal) {
        return dealRepository.save(deal);
    }

    @Transactional
    public Deal findByIdWithUsers(Long id) {
        return dealRepository.findByIdWithUsers(id)
                .orElseThrow(() -> new RuntimeException("Deal not found: " + id));
    }

    @Transactional
    public Page<Deal> getFinishedUserDealsWithUsers(Long userId, Pageable pageable) {
        return dealRepository.findFinishedUserDealsWithUsers(userId, pageable);
    }
}