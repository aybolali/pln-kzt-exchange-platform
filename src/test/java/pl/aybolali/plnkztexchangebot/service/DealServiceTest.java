package pl.aybolali.plnkztexchangebot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import pl.aybolali.plnkztexchangebot.entity.*;
import pl.aybolali.plnkztexchangebot.repository.DealRepository;
import pl.aybolali.plnkztexchangebot.repository.ExchangeRequestRepository;
import pl.aybolali.plnkztexchangebot.repository.UserRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class DealServiceTest {

    @Mock private DealRepository dealRepository;
    @Mock private UserRepository userRepository;
    @Mock private ExchangeRequestRepository exchangeRequestRepository;
    @Mock private ExchangeRequestService exchangeRequestService;
    @Mock private UserService userService;
    @Mock private ExchangeRateService exchangeRateService;

    @InjectMocks private DealService dealService;

    @Test
    void createDealFromRequest_ShouldCreateValidDeal() {
        Long requestId = 1L;
        Long providerId = 2L;
        BigDecimal dealAmount = new BigDecimal("500");

        User requester = createUser(1L, "requester");
        User provider = createUser(2L, "provider");
        ExchangeRequest request = createExchangeRequest(requester);

        when(exchangeRequestService.findById(requestId)).thenReturn(request);
        when(userRepository.findById(providerId)).thenReturn(Optional.of(provider));
        when(exchangeRateService.getCurrentPLNtoKZTRate()).thenReturn(new BigDecimal("147.5"));

        ExchangeRequest providerRequest = createExchangeRequest(provider);
        providerRequest.setCurrencyNeed(ExchangeRequest.Currency.KZT);
        when(exchangeRequestRepository.findActiveRequestByUserAndCurrency(
                eq(providerId), eq(ExchangeRequest.Currency.KZT)
        )).thenReturn(Optional.of(providerRequest));

        Deal expectedDeal = Deal.builder()
                .requester(requester)
                .provider(provider)
                .amount(dealAmount)
                .currency(ExchangeRequest.Currency.PLN)
                .status(DealStatus.COMPLETED)
                .build();

        when(dealRepository.save(any(Deal.class))).thenReturn(expectedDeal);

        Deal result = dealService.createDealFromRequest(requestId, providerId, dealAmount);

        assertNotNull(result);
        assertEquals(requester, result.getRequester());
        assertEquals(provider, result.getProvider());
        assertEquals(dealAmount, result.getAmount());
        assertEquals(DealStatus.COMPLETED, result.getStatus());

        verify(exchangeRequestService).findById(requestId);
        verify(userRepository).findById(providerId);
        verify(dealRepository).save(any(Deal.class));
    }

    @Test
    void createDealFromRequest_ShouldThrowExceptionWhenUserTriesToDealWithSelf() {
        Long requestId = 1L;
        Long providerId = 1L;
        BigDecimal dealAmount = new BigDecimal("500");

        User user = createUser(1L, "sameuser");
        ExchangeRequest request = createExchangeRequest(user);

        when(exchangeRequestService.findById(requestId)).thenReturn(request);
        when(userRepository.findById(providerId)).thenReturn(Optional.of(user));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> dealService.createDealFromRequest(requestId, providerId, dealAmount)
        );

        assertEquals("User cannot create deal with themselves", exception.getMessage());
        verify(dealRepository, never()).save(any());
    }

    private User createUser(Long id, String username) {
        return User.builder()
                .id(id)
                .telegramUsername(username)
                .firstName("Test")
                .trustRating(new BigDecimal("5.0"))
                .successfulDeals(0)
                .isEnabled(true)
                .build();
    }

    private ExchangeRequest createExchangeRequest(User user) {
        return ExchangeRequest.builder()
                .id(1L)
                .user(user)
                .currencyNeed(ExchangeRequest.Currency.PLN)
                .amountNeed(new BigDecimal("1000"))
                .status(ExchangeRequestStatus.ACTIVE)
                .transferMethod(TransferMethod.BANK_TRANSFER)
                .build();
    }
}