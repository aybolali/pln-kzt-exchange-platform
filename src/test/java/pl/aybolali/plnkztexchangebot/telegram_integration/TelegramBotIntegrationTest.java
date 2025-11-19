package pl.aybolali.plnkztexchangebot.telegram_integration;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import pl.aybolali.plnkztexchangebot.dto.CreateRatingDTO;
import pl.aybolali.plnkztexchangebot.entity.*;
import pl.aybolali.plnkztexchangebot.exception.BusinessException;
import pl.aybolali.plnkztexchangebot.repository.*;
import pl.aybolali.plnkztexchangebot.service.*;
import pl.aybolali.plnkztexchangebot.telegram.PLNKZTExchangeBot;
import pl.aybolali.plnkztexchangebot.telegram.TelegramBotService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Telegram Bot Integration Tests (Fixed)")
@ActiveProfiles("test")
class TelegramBotIntegrationTest {

    @Autowired private UserService userService;
    @Autowired private ExchangeRequestService exchangeRequestService;
    @Autowired private DealService dealService;
    @Autowired private MatchingService matchingService;
    @Autowired private RatingService ratingService;
    @Autowired private TelegramBotService telegramBotService;

    @Autowired private UserRepository userRepository;
    @Autowired private ExchangeRequestRepository exchangeRequestRepository;
    @Autowired private DealRepository dealRepository;
    @Autowired private RatingRepository ratingRepository;

    @MockitoBean private PLNKZTExchangeBot telegramBot;
    @MockitoBean private ExchangeRateService exchangeRateService;
    @MockitoBean private SimpleRateLimitService rateLimitingService;

    @BeforeEach
    void setUp() throws TelegramApiException {
        when(rateLimitingService.checkLimit(anyLong(), anyString())).thenReturn(true);
        mockExchangeRates();
        mockTelegramBot();
    }

    @Test
    @Order(1)
    @DisplayName("INTEGRATION: Complete user registration cycle")
    void testUserRegistrationFullCycle() throws InterruptedException {
        // Given: New users register via Telegram
        registerUserViaBot(123456L, "alice_smith", "Alice");
        registerUserViaBot(789012L, "bob_johnson", "Bob");
        registerUserViaBot(345678L, "charlie_wilson", "Charlie");
        registerUserViaBot(901234L, "diana_brown", "Diana");

        Thread.sleep(500);

        // Then: Users created in database
        User alice = userRepository.findByTelegramUsername("alice_smith").orElseThrow();

        assertThat(alice.getTelegramUserId()).isEqualTo(123456L);  // ⭐ ПРОВЕРКА telegramUserId
        assertThat(alice.getTelegramUsername()).isEqualTo("alice_smith");
        assertThat(alice.getFirstName()).isEqualTo("Alice");

        List<User> allUsers = userRepository.findAll();
        assertThat(allUsers).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    @Order(2)
    @DisplayName("INTEGRATION: Exchange request creation")
    void testExchangeRequestCreationFullCycle() {
        User alice = userRepository.findByTelegramUsername("alice_smith").orElseThrow();
        User bob = userRepository.findByTelegramUsername("bob_johnson").orElseThrow();

        ExchangeRequest aliceRequest = exchangeRequestService.createExchangeRequest(
                alice.getId(),
                "PLN",
                new BigDecimal("500.00"),
                TransferMethod.BANK_TRANSFER,
                "Need PLN for university"
        );

        ExchangeRequest bobRequest = exchangeRequestService.createExchangeRequest(
                bob.getId(),
                "KZT",
                new BigDecimal("73750.00"),
                TransferMethod.BANK_TRANSFER,
                "Business expansion"
        );

        assertThat(aliceRequest.getId()).isNotNull();
        assertThat(aliceRequest.getStatus()).isEqualTo(ExchangeRequestStatus.ACTIVE);
        assertThat(bobRequest.getId()).isNotNull();
    }

    @Test
    @Order(3)
    @DisplayName("INTEGRATION: Smart matching")
    void testSmartMatchingWithRealDatabase() {
        User alice = userRepository.findByTelegramUsername("alice_smith").orElseThrow();

        List<ExchangeRequest> matches = matchingService.findMatchingOffers(
                alice.getId(),
                ExchangeRequest.Currency.KZT,
                5,
                new BigDecimal("500.00")
        );

        assertThat(matches).isNotEmpty();
        assertThat(matches.get(0).getUser().getTelegramUsername()).isEqualTo("bob_johnson");
    }

    @Test
    @Order(4)
    @DisplayName("INTEGRATION: Complete deal cycle")
    void testCompleteDealCycleAliceBob() {
        User alice = userRepository.findByTelegramUsername("alice_smith").orElseThrow();
        User bob = userRepository.findByTelegramUsername("bob_johnson").orElseThrow();

        List<ExchangeRequest> aliceRequests = exchangeRequestRepository
                .findByUserIdAndStatus(alice.getId(), ExchangeRequestStatus.ACTIVE);
        assertThat(aliceRequests).isNotEmpty();

        Deal deal = dealService.createDealFromRequest(
                aliceRequests.get(0).getId(),
                bob.getId(),
                new BigDecimal("500.00")
        );

        assertThat(deal.getId()).isNotNull();
        assertThat(deal.getStatus()).isEqualTo(DealStatus.COMPLETED);
        assertThat(deal.getRequester().getId()).isEqualTo(alice.getId());
        assertThat(deal.getProvider().getId()).isEqualTo(bob.getId());

        User updatedAlice = userRepository.findById(alice.getId()).orElseThrow();
        User updatedBob = userRepository.findById(bob.getId()).orElseThrow();

        assertThat(updatedAlice.getSuccessfulDeals()).isGreaterThan(0);
        assertThat(updatedBob.getSuccessfulDeals()).isGreaterThan(0);
    }

    @Test
    @Order(5)
    @DisplayName("INTEGRATION: Rating system")
    void testRatingSystem() {
        User alice = userRepository.findByTelegramUsername("alice_smith").orElseThrow();
        User bob = userRepository.findByTelegramUsername("bob_johnson").orElseThrow();

        ExchangeRequest ratingTestRequest = exchangeRequestService.createExchangeRequest(
                alice.getId(),
                "PLN",
                new BigDecimal("250.00"),
                TransferMethod.BANK_TRANSFER,
                "For rating test"
        );

        Deal deal = dealService.createDealFromRequest(
                ratingTestRequest.getId(),
                bob.getId(),
                new BigDecimal("250.00")
        );

        Deal savedDeal = dealRepository.findById(deal.getId())
                .orElseThrow(() -> new AssertionError("Deal not saved to database!"));

        assertThat(savedDeal).isNotNull();
        assertThat(savedDeal.getStatus()).isEqualTo(DealStatus.COMPLETED);

        CreateRatingDTO aliceRating = new CreateRatingDTO(
                deal.getId(),
                new BigDecimal("5.0")
        );

        ratingService.createRating(aliceRating, "alice_smith");

        List<Rating> ratings = ratingRepository.findAll();
        assertThat(ratings).isNotEmpty();
        assertThat(ratings.get(0).getRating()).isEqualByComparingTo(new BigDecimal("5.0"));
    }

    @Test
    @Order(6)
    @DisplayName("INTEGRATION: Validation tests")
    void testEdgeCasesAndValidations() {
        User alice = userRepository.findByTelegramUsername("alice_smith").orElseThrow();

        // Test 1: Negative amount should fail with "Amount must be positive"
        assertThatThrownBy(() -> exchangeRequestService.createExchangeRequest(
                alice.getId(),
                "PLN",
                new BigDecimal("-100"),
                TransferMethod.BANK_TRANSFER,
                "Invalid negative"
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Минимальная сумма: 10");

        // Test 2: Zero amount should fail
        assertThatThrownBy(() -> exchangeRequestService.createExchangeRequest(
                alice.getId(),
                "PLN",
                BigDecimal.ZERO,
                TransferMethod.BANK_TRANSFER,
                "Zero amount"
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Минимальная сумма: 10");

        // Test 3: Amount below minimum (5 < 10)
        assertThatThrownBy(() -> exchangeRequestService.createExchangeRequest(
                alice.getId(),
                "PLN",
                new BigDecimal("5"),
                TransferMethod.BANK_TRANSFER,
                "Too small"
        )).isInstanceOf(BusinessException.class)
                .hasMessageContaining("Минимальная сумма: 10");
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    /**
     * ⭐ ИЗМЕНЕНО: Добавлен telegramUserId как первый параметр
     */
    private void registerUserViaBot(Long telegramUserId, String username, String firstName)
            throws InterruptedException {
        // ⭐ ИЗМЕНЕНО: порядок параметров
        userService.registerUser(telegramUserId, username, firstName, null);
        Thread.sleep(50);
    }

    private void mockExchangeRates() {
        when(exchangeRateService.getCurrentPLNtoKZTRate())
                .thenReturn(new BigDecimal("147.50"));
        when(exchangeRateService.getCurrentKZTtoPLNRate())
                .thenReturn(new BigDecimal("0.006780"));
    }

    private void mockTelegramBot() throws TelegramApiException {
        when(telegramBot.execute(any(SendMessage.class)))
                .thenReturn(mock(Message.class));
    }
}