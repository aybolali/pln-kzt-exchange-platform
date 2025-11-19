package pl.aybolali.plnkztexchangebot.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.service.*;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramBotServiceTest {

    @Mock
    private PLNKZTExchangeBot bot;

    @Mock
    private UserService userService;

    @Mock
    private ExchangeRequestService exchangeRequestService;

    @Mock
    private DealService dealService;

    @Mock
    private MatchingService matchingService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @Mock
    private SimpleRateLimitService rateLimitService;

    @Mock
    private TelegramMessageFormatter messageFormatter;

    @Mock
    private ConversationStateService conversationStateService;  // ⭐ ДОБАВЛЕНО!

    @InjectMocks
    private TelegramBotService telegramBotService;

    private User testUser;
    private org.telegram.telegrambots.meta.api.objects.User telegramUser;
    private Message message;
    private Update update;

    @BeforeEach
    void setUp() throws TelegramApiException {
        // Mock telegram user
        telegramUser = mock(org.telegram.telegrambots.meta.api.objects.User.class);
        lenient().when(telegramUser.getId()).thenReturn(123456789L);
        lenient().when(telegramUser.getUserName()).thenReturn("testuser");
        lenient().when(telegramUser.getFirstName()).thenReturn("Test");
        lenient().when(telegramUser.getLastName()).thenReturn("User");

        // Mock message
        message = mock(Message.class);
        lenient().when(message.getChatId()).thenReturn(123456789L);
        lenient().when(message.getFrom()).thenReturn(telegramUser);
        lenient().when(message.getText()).thenReturn("/start");
        lenient().when(message.hasText()).thenReturn(true);

        Chat chat = mock(Chat.class);
        lenient().when(chat.getId()).thenReturn(123456789L);
        lenient().when(message.getChat()).thenReturn(chat);

        // Mock update
        update = mock(Update.class);
        lenient().when(update.hasMessage()).thenReturn(true);
        lenient().when(update.getMessage()).thenReturn(message);

        // Mock test user
        testUser = User.builder()
                .id(1L)
                .telegramUserId(123456789L)
                .telegramUsername("testuser")
                .firstName("Test")
                .lastName("User")
                .isPhoneVerified(true)
                .isEnabled(true)
                .build();

        // Mock bot execute
        lenient().when(bot.execute(any(SendMessage.class))).thenReturn(message);

        // Mock rate limiting
        lenient().when(rateLimitService.checkLimit(anyLong(), anyString())).thenReturn(true);

        // Mock exchange rates
        lenient().when(exchangeRateService.getCurrentPLNtoKZTRate()).thenReturn(new BigDecimal("147.50"));
        lenient().when(exchangeRateService.getCurrentKZTtoPLNRate()).thenReturn(new BigDecimal("0.006780"));

        // ⭐ Mock ConversationStateService
        lenient().when(conversationStateService.getState(anyLong())).thenReturn(ConversationState.INITIAL);

        // Mock formatters
        lenient().when(messageFormatter.formatUsernameRequired()).thenReturn("Username required");
        lenient().when(messageFormatter.formatUserNotFoundError()).thenReturn("User not found");
        lenient().when(messageFormatter.formatVerificationRequired()).thenReturn("Verification required");
        lenient().when(messageFormatter.formatTechnicalError()).thenReturn("Technical error");
        lenient().when(messageFormatter.formatVerifiedUserWelcome(any(), any())).thenReturn("Welcome");
        lenient().when(messageFormatter.formatMandatoryPhoneVerificationRequest(any(), any())).thenReturn("Verify phone");
        lenient().when(messageFormatter.formatHelpMessage()).thenReturn("Help");
        lenient().when(messageFormatter.formatExchangeRates(any())).thenReturn("Rates");
        lenient().when(messageFormatter.formatUnknownCommand()).thenReturn("Unknown command");
    }

    @Test
    void testStartCommand_NewUser() throws TelegramApiException {
        // Given
        when(userService.findByTelegramUserId(123456789L)).thenReturn(Optional.empty());
        when(userService.registerUser(eq(123456789L), eq("testuser"), eq("Test"), eq("User"))).thenReturn(testUser);

        // When
        telegramBotService.processUpdate(update);

        // Then
        verify(userService).findByTelegramUserId(123456789L);
        verify(userService).registerUser(eq(123456789L), eq("testuser"), eq("Test"), eq("User"));
        verify(bot, atLeastOnce()).execute(any(SendMessage.class));
    }

    @Test
    void testStartCommand_ExistingUser_NotVerified() throws TelegramApiException {
        // Given
        User unverifiedUser = User.builder()
                .id(1L)
                .telegramUserId(123456789L)
                .telegramUsername("testuser")
                .firstName("Test")
                .isPhoneVerified(false)
                .isEnabled(true)
                .build();

        when(userService.findByTelegramUserId(123456789L)).thenReturn(Optional.of(unverifiedUser));

        // When
        telegramBotService.processUpdate(update);

        // Then
        verify(userService).findByTelegramUserId(123456789L);
        verify(userService, never()).registerUser(anyLong(), anyString(), anyString(), anyString());
        verify(bot, atLeastOnce()).execute(any(SendMessage.class));
    }

    @Test
    void testStartCommand_ExistingUser_Verified() throws TelegramApiException {
        // Given
        when(userService.findByTelegramUserId(123456789L)).thenReturn(Optional.of(testUser));

        // When
        telegramBotService.processUpdate(update);

        // Then
        verify(userService).findByTelegramUserId(123456789L);
        verify(userService, never()).registerUser(anyLong(), anyString(), anyString(), anyString());
        verify(bot, atLeastOnce()).execute(any(SendMessage.class));
    }

    @Test
    void testStartCommand_UserWithoutUsername() throws TelegramApiException {
        // Given
        lenient().when(telegramUser.getUserName()).thenReturn(null);

        // When
        telegramBotService.processUpdate(update);

        // Then
        verify(userService, never()).findByTelegramUserId(anyLong());
        verify(userService, never()).registerUser(anyLong(), anyString(), anyString(), anyString());
        verify(bot, atLeastOnce()).execute(any(SendMessage.class));
    }

    @Test
    void testHelpCommand() throws TelegramApiException {
        // Given
        when(message.getText()).thenReturn("/help");

        // When
        telegramBotService.processUpdate(update);

        // Then
        verify(bot, atLeastOnce()).execute(any(SendMessage.class));
    }

    @Test
    void testRatesCommand() throws TelegramApiException {
        // Given
        when(message.getText()).thenReturn("/rates");

        // When
        telegramBotService.processUpdate(update);

        // Then
        verify(exchangeRateService, atLeastOnce()).getCurrentPLNtoKZTRate();
        verify(bot, atLeastOnce()).execute(any(SendMessage.class));
    }

    @Test
    void testStatusCommand_RequiresVerification() throws TelegramApiException {
        // Given
        User unverifiedUser = User.builder()
                .id(1L)
                .telegramUserId(123456789L)
                .telegramUsername("testuser")
                .firstName("Test")
                .isPhoneVerified(false)
                .build();

        when(message.getText()).thenReturn("/status");
        when(userService.findByTelegramUserId(123456789L)).thenReturn(Optional.of(unverifiedUser));

        // When
        telegramBotService.processUpdate(update);

        // Then
        verify(userService, atLeastOnce()).findByTelegramUserId(123456789L);
        verify(bot, atLeastOnce()).execute(any(SendMessage.class));
    }

    @Test
    void testNeedCommand_RequiresVerification() throws TelegramApiException {
        // Given
        when(message.getText()).thenReturn("/need");
        lenient().when(userService.findByTelegramUserId(123456789L)).thenReturn(Optional.empty());

        // When
        telegramBotService.processUpdate(update);

        // Then
        verify(bot, atLeastOnce()).execute(any(SendMessage.class));
    }
}