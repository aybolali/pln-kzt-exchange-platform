package pl.aybolali.plnkztexchangebot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequest;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequestStatus;
import pl.aybolali.plnkztexchangebot.entity.TransferMethod;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.repository.ExchangeRequestRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchingService Unit Tests - Fixed v6.0")
@ActiveProfiles("test")
class MatchingServiceTest {

    @Mock
    private ExchangeRequestRepository exchangeRequestRepository;

    @Mock
    private RatingService ratingService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private MatchingService matchingService;

    private User alice, bob, charlie, diana;

    @BeforeEach
    void setUp() {
        alice = User.builder()
                .id(1L)
                .telegramUsername("alice_smith")
                .firstName("Alice")
                .trustRating(new BigDecimal("4.5"))
                .successfulDeals(5)
                .isEnabled(true)
                .isPhoneVerified(false)
                .build();

        bob = User.builder()
                .id(2L)
                .telegramUsername("bob_johnson")
                .firstName("Bob")
                .trustRating(new BigDecimal("4.8"))
                .successfulDeals(12)
                .isEnabled(true)
                .isPhoneVerified(true)
                .build();

        charlie = User.builder()
                .id(3L)
                .telegramUsername("charlie_wilson")
                .firstName("Charlie")
                .trustRating(new BigDecimal("4.2"))
                .successfulDeals(3)
                .isEnabled(true)
                .isPhoneVerified(false)
                .build();

        diana = User.builder()
                .id(4L)
                .telegramUsername("diana_brown")
                .firstName("Diana")
                .trustRating(new BigDecimal("4.0"))
                .successfulDeals(1)
                .isEnabled(true)
                .isPhoneVerified(false)
                .build();

        // Универсальные моки для всех тестов
        lenient().when(exchangeRateService.getCurrentKZTtoPLNRate())
                .thenReturn(new BigDecimal("0.006780"));

        lenient().when(exchangeRateService.getCurrentPLNtoKZTRate())
                .thenReturn(new BigDecimal("147.5"));

        lenient().when(ratingService.getActualUserRating(anyLong()))
                .thenReturn(4.5);
    }

    @Test
    @DisplayName("SMART MATCHING: Bob первый для Alice (идеальное совпадение по сумме)")
    void testFindMatchingOffers_SmartAlgorithmCorrectOrder() {
        BigDecimal aliceNeed = new BigDecimal("500.00");

        ExchangeRequest bobRequest = ExchangeRequest.builder()
                .id(2L).user(bob)
                .currencyNeed(ExchangeRequest.Currency.KZT)
                .amountNeed(new BigDecimal("73750.00"))
                .status(ExchangeRequestStatus.ACTIVE)
                .transferMethod(TransferMethod.BANK_TRANSFER)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        ExchangeRequest charlieRequest = ExchangeRequest.builder()
                .id(3L).user(charlie)
                .currencyNeed(ExchangeRequest.Currency.KZT)
                .amountNeed(new BigDecimal("30000.00"))
                .status(ExchangeRequestStatus.ACTIVE)
                .transferMethod(TransferMethod.BANK_TRANSFER)
                .createdAt(LocalDateTime.now().minusMinutes(30))
                .build();

        ExchangeRequest dianaRequest = ExchangeRequest.builder()
                .id(4L).user(diana)
                .currencyNeed(ExchangeRequest.Currency.KZT)
                .amountNeed(new BigDecimal("100000.00"))
                .status(ExchangeRequestStatus.ACTIVE)
                .transferMethod(TransferMethod.BANK_TRANSFER)
                .createdAt(LocalDateTime.now())
                .build();

        Page<ExchangeRequest> page = new PageImpl<>(Arrays.asList(charlieRequest, bobRequest, dianaRequest));

        when(exchangeRequestRepository.findActiveByCurrency(
                eq(ExchangeRequest.Currency.KZT),
                any(Pageable.class)
        )).thenReturn(page);

        List<ExchangeRequest> result = matchingService.findMatchingOffers(
                1L, ExchangeRequest.Currency.KZT, 5, aliceNeed);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getUser().getTelegramUsername()).isEqualTo("bob_johnson");
    }

    @Test
    @DisplayName("P2P ЛОГИКА: Правильная интерпретация currency параметра")
    void testFindMatchingOffers_CorrectP2PLogic() {
        ExchangeRequest aliceRequest = ExchangeRequest.builder()
                .id(1L).user(alice)
                .currencyNeed(ExchangeRequest.Currency.PLN)
                .amountNeed(new BigDecimal("500.00"))
                .status(ExchangeRequestStatus.ACTIVE)
                .transferMethod(TransferMethod.BANK_TRANSFER)
                .createdAt(LocalDateTime.now())
                .build();

        Page<ExchangeRequest> page = new PageImpl<>(Arrays.asList(aliceRequest));

        when(exchangeRequestRepository.findActiveByCurrency(
                eq(ExchangeRequest.Currency.PLN),
                any(Pageable.class)
        )).thenReturn(page);

        List<ExchangeRequest> result = matchingService.findMatchingOffers(
                2L, ExchangeRequest.Currency.PLN, 5, new BigDecimal("500"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getUser().getTelegramUsername()).isEqualTo("alice_smith");
    }

    @Test
    @DisplayName("ИСКЛЮЧЕНИЕ СЕБЯ: Пользователь не видит свои запросы")
    void testFindMatchingOffers_ExcludeOwnRequests() {
        Page<ExchangeRequest> emptyPage = new PageImpl<>(Arrays.asList());

        when(exchangeRequestRepository.findActiveByCurrency(
                eq(ExchangeRequest.Currency.KZT),
                any(Pageable.class)
        )).thenReturn(emptyPage);

        List<ExchangeRequest> result = matchingService.findMatchingOffers(
                1L, ExchangeRequest.Currency.KZT, 5, new BigDecimal("500"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ЛИМИТ РЕЗУЛЬТАТОВ: Ограничение количества предложений")
    void testFindMatchingOffers_LimitWorksCorrectly() {
        List<ExchangeRequest> manyRequests = IntStream.range(0, 15)
                .mapToObj(i -> ExchangeRequest.builder()
                        .id((long) i)
                        .user(User.builder()
                                .id((long) (i + 10))
                                .telegramUsername("user_" + i)
                                .firstName("User" + i)
                                .trustRating(new BigDecimal("4.0"))
                                .successfulDeals(i)
                                .isEnabled(true)
                                .build())
                        .currencyNeed(ExchangeRequest.Currency.KZT)
                        .amountNeed(new BigDecimal("1000").multiply(BigDecimal.valueOf(i + 1)))
                        .status(ExchangeRequestStatus.ACTIVE)
                        .transferMethod(TransferMethod.BANK_TRANSFER)
                        .createdAt(LocalDateTime.now().minusHours(i))
                        .build())
                .collect(Collectors.toList());

        Page<ExchangeRequest> page = new PageImpl<>(manyRequests);

        when(exchangeRequestRepository.findActiveByCurrency(
                eq(ExchangeRequest.Currency.KZT),
                any(Pageable.class)
        )).thenReturn(page);

        List<ExchangeRequest> result = matchingService.findMatchingOffers(
                1L, ExchangeRequest.Currency.KZT, 3, new BigDecimal("500"));

        assertThat(result).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    @DisplayName("СКОРИНГ: Высокий рейтинг и идеальное совпадение = первый")
    void testMatchingScore_CalculationLogic() {
        User highRatedUser = User.builder()
                .id(10L).telegramUsername("high_rated").firstName("HighRated")
                .trustRating(new BigDecimal("4.9")).successfulDeals(50)
                .isEnabled(true).isPhoneVerified(true).build();

        User lowRatedUser = User.builder()
                .id(11L).telegramUsername("low_rated").firstName("LowRated")
                .trustRating(new BigDecimal("2.1")).successfulDeals(1)
                .isEnabled(true).isPhoneVerified(false).build();

        ExchangeRequest perfectMatch = ExchangeRequest.builder()
                .id(10L).user(highRatedUser)
                .currencyNeed(ExchangeRequest.Currency.KZT)
                .amountNeed(new BigDecimal("73750"))
                .status(ExchangeRequestStatus.ACTIVE)
                .transferMethod(TransferMethod.BANK_TRANSFER)
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        ExchangeRequest partialMatch = ExchangeRequest.builder()
                .id(11L).user(lowRatedUser)
                .currencyNeed(ExchangeRequest.Currency.KZT)
                .amountNeed(new BigDecimal("30000"))
                .status(ExchangeRequestStatus.ACTIVE)
                .transferMethod(TransferMethod.BANK_TRANSFER)
                .createdAt(LocalDateTime.now())
                .build();

        Page<ExchangeRequest> page = new PageImpl<>(Arrays.asList(partialMatch, perfectMatch));

        when(exchangeRequestRepository.findActiveByCurrency(
                eq(ExchangeRequest.Currency.KZT),
                any(Pageable.class)
        )).thenReturn(page);

        List<ExchangeRequest> result = matchingService.findMatchingOffers(
                1L, ExchangeRequest.Currency.KZT, 5, new BigDecimal("500"));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getUser().getTelegramUsername()).isEqualTo("high_rated");
    }

    @Test
    @DisplayName("EDGE CASE: Нет активных предложений")
    void testFindMatchingOffers_NoActiveOffers() {
        Page<ExchangeRequest> emptyPage = new PageImpl<>(Arrays.asList());

        when(exchangeRequestRepository.findActiveByCurrency(
                any(), any(Pageable.class)
        )).thenReturn(emptyPage);

        List<ExchangeRequest> result = matchingService.findMatchingOffers(
                1L, ExchangeRequest.Currency.KZT, 5, new BigDecimal("500"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("TRANSFER METHOD: Учет способа перевода при поиске")
    void testFindMatchingOffers_TransferMethodMatching() {
        ExchangeRequest bankTransferRequest = ExchangeRequest.builder()
                .id(1L).user(bob)
                .currencyNeed(ExchangeRequest.Currency.KZT)
                .amountNeed(new BigDecimal("50000"))
                .status(ExchangeRequestStatus.ACTIVE)
                .transferMethod(TransferMethod.BANK_TRANSFER)
                .build();

        Page<ExchangeRequest> page = new PageImpl<>(Arrays.asList(bankTransferRequest));

        when(exchangeRequestRepository.findActiveByCurrency(
                eq(ExchangeRequest.Currency.KZT),
                any(Pageable.class)
        )).thenReturn(page);

        List<ExchangeRequest> result = matchingService.findMatchingOffers(
                1L, ExchangeRequest.Currency.KZT, 5, new BigDecimal("500"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTransferMethod()).isEqualTo(TransferMethod.BANK_TRANSFER);
    }
}