package pl.aybolali.plnkztexchangebot.telegram;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import pl.aybolali.plnkztexchangebot.dto.CreateRatingDTO;
import pl.aybolali.plnkztexchangebot.dto.ExchangeRateDTO;
import pl.aybolali.plnkztexchangebot.entity.*;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.exception.BusinessException;
import pl.aybolali.plnkztexchangebot.exception.UserNotFoundException;
import pl.aybolali.plnkztexchangebot.service.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static pl.aybolali.plnkztexchangebot.telegram.TelegramConstants.*;

@Service
@Slf4j
public class TelegramBotService {

    private final PLNKZTExchangeBot bot;
    private final UserService userService;
    private final ExchangeRequestService exchangeService;
    private final DealService dealService;
    private final ExchangeRateService exchangeRateService;
    private final TelegramMessageFormatter messageFormatter;
    private final RatingService ratingService;
    private final ConversationStateService conversationStateService;
    private final SimpleRateLimitService rateLimitService;

    public TelegramBotService(
            @Lazy PLNKZTExchangeBot bot,
            UserService userService,
            ExchangeRequestService exchangeService,
            DealService dealService,
            ExchangeRateService exchangeRateService,
            TelegramMessageFormatter messageFormatter,
            SimpleRateLimitService rateLimitService,
            RatingService ratingService, ConversationStateService conversationStateService) {

        this.bot = bot;
        this.userService = userService;
        this.exchangeService = exchangeService;
        this.dealService = dealService;
        this.exchangeRateService = exchangeRateService;
        this.messageFormatter = messageFormatter;
        this.ratingService = ratingService;
        this.conversationStateService = conversationStateService;
        this.rateLimitService = rateLimitService;
    }

    public void processUpdate(Update update) {
        long startTime = System.currentTimeMillis();
        Long userId = null;

        try {
            if (update.hasMessage()) {
                Message message = update.getMessage();
                userId = message.getFrom().getId();

                log.debug("Webhook received from user ID: {}", userId);

                if (message.hasContact()) {
                    handleContactReceived(message);
                } else if (message.hasText()) {
                    processTextMessageAsync(message);
                }
            }
        } catch (BusinessException e) {
            log.warn("Business error for user {}: {}", userId, e.getMessage());
            if (update.hasMessage()) {
                sendMessage(update.getMessage().getChatId(), e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error processing user {}: {}", userId, e.getMessage(), e);
            if (update.hasMessage()) {
                sendMessage(update.getMessage().getChatId(), messageFormatter.formatTechnicalError());
            }
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.debug("Webhook completed in {}ms for user {}", duration, userId);
        }
    }

    public void processTextMessageAsync(Message message) {
        String text = message.getText().trim();
        Long chatId = message.getChatId();
        org.telegram.telegrambots.meta.api.objects.User telegramUser = message.getFrom();
        Long telegramUserId = telegramUser.getId();

        log.debug("Processing: '{}' from user ID: {}", text, telegramUserId);

        // ⭐ ПРОВЕРКА RATE LIMIT
        if (!rateLimitService.checkLimit(telegramUserId, RATE_LIMIT_TELEGRAM_COMMAND)) {
            sendMessage(chatId, "⚠️ Слишком много команд. Подождите минуту.");
            return;
        }

        try {
            ConversationState state = conversationStateService.getState(telegramUserId);

            if (state != ConversationState.INITIAL && !text.startsWith("/")) {
                handleConversationState(chatId, telegramUserId, text, state);
                return;
            }

            if (text.startsWith(COMMAND_START)) {
                handleStartCommand(chatId, telegramUser);
                return;
            }

            if (text.startsWith(COMMAND_RATES)) {
                handleRatesCommand(chatId);
                return;
            }

            if (text.startsWith(COMMAND_HELP)) {
                handleHelpCommand(chatId);
                return;
            }

            if (!checkPhoneVerification(chatId, telegramUserId)) {
                return;
            }

            if (text.startsWith(COMMAND_MENU)) {
                handleMenuCommand(chatId, telegramUserId);
            } else if (text.startsWith(COMMAND_NEED)) {
                handleNeedCommand(chatId, telegramUserId);
            } else if (text.startsWith(COMMAND_SEARCH)) {
                handleSearchCommand(chatId, telegramUserId);
            } else if (text.startsWith(COMMAND_MY_REQUESTS)) {
                handleMyRequestsCommand(chatId, telegramUserId);
            } else if (text.startsWith(COMMAND_HISTORY)) {
                handleDealsHistoryCommand(chatId, telegramUserId);
            } else if (text.startsWith(COMMAND_PROFILE)) {
                handleProfileCommand(chatId, telegramUserId);
            } else {
                sendMessage(chatId, messageFormatter.formatUnknownCommand());
            }
        } catch (Exception e) {
            log.error("❌ Error: {}", e.getMessage(), e);
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }

    public void handleCallbackQuery(CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        Long chatId = callbackQuery.getMessage().getChatId();
        Long telegramUserId = callbackQuery.getFrom().getId();

        log.info("🖱️ Callback: '{}' from user {}", data, telegramUserId);

        if (!rateLimitService.checkLimit(telegramUserId, RATE_LIMIT_TELEGRAM_COMMAND)) {
            try {
                org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answer =
                        new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
                answer.setCallbackQueryId(callbackQuery.getId());
                answer.setText("⚠️ Слишком много действий. Подождите минуту.");
                answer.setShowAlert(true);
                bot.execute(answer);
            } catch (Exception e) {
                log.error("Error answering rate limit: {}", e.getMessage());
            }
            return;
        }

        try {
            org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answer =
                    new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackQuery.getId());
            bot.execute(answer);

            // ============================================
            // ОСНОВНОЕ МЕНЮ
            // ============================================

            if (CALLBACK_SHOW_MENU.equals(data)) {
                log.info("📋 Opening menu");
                handleMenuCommand(chatId, telegramUserId);
                return;
            }

            if (CALLBACK_CURRENT_PAGE.equals(data)) {
                // Информационная кнопка - игнорируем
                return;
            }

            // ============================================
            // ПОИСК И ЗАЯВКИ
            // ============================================

            if (data.startsWith(CALLBACK_SEARCH_CURRENCY)) {
                String currency = data.substring(CALLBACK_SEARCH_CURRENCY.length());
                handleSearchByCurrency(chatId, telegramUserId, currency);
                return;
            }

            if (data.startsWith(CALLBACK_RESPOND)) {
                Long requestId = Long.parseLong(data.substring(CALLBACK_RESPOND.length()));
                handleRespondToRequest(chatId, telegramUserId, requestId);
                return;
            }

            if (data.startsWith(CALLBACK_VIEW_REQUEST)) {
                Long requestId = Long.parseLong(data.substring(CALLBACK_VIEW_REQUEST.length()));
                handleViewRequest(chatId, telegramUserId, requestId);
                return;
            }

            // ============================================
            // СОЗДАНИЕ ЗАЯВОК
            // ============================================

            if (CALLBACK_SKIP_COMMENT.equals(data)) {
                handleSkipCommentCallback(chatId, telegramUserId);
                return;
            }

            if (CALLBACK_CANCEL_REQUEST.equals(data)) {
                handleCancelRequestCallback(chatId, telegramUserId);
                return;
            }

            if (CALLBACK_CONFIRM_REQUEST.equals(data)) {
                handleRequestConfirmation(chatId, telegramUserId);
                return;
            }

            if (CALLBACK_EDIT_REQUEST.equals(data)) {
                handleRequestEdit(chatId, telegramUserId);
                return;
            }

            // ============================================
            // УПРАВЛЕНИЕ ЗАЯВКАМИ
            // ============================================

            if (CALLBACK_SHOW_REQUEST_MANAGEMENT.equals(data)) {
                showRequestManagementOptions(chatId, telegramUserId);
                return;
            }

            if (CALLBACK_ACTION_EDIT_REQUEST.equals(data)) {
                showRequestListForEdit(chatId, telegramUserId);
                return;
            }

            if (CALLBACK_ACTION_CANCEL_REQUEST.equals(data)) {
                showRequestListForCancel(chatId, telegramUserId);
                return;
            }

            if (data.startsWith(CALLBACK_SELECT_CANCEL)) {
                int index = Integer.parseInt(data.substring(CALLBACK_SELECT_CANCEL.length()));
                handleCancelRequestByIndex(chatId, telegramUserId, index);
                return;
            }

            if (data.startsWith(CALLBACK_SELECT_EDIT)) {
                int index = Integer.parseInt(data.substring(CALLBACK_SELECT_EDIT.length()));
                startEditingRequest(chatId, telegramUserId, index);
                return;
            }

            if (data.startsWith(CALLBACK_CANCEL_REQUEST_BUTTON)) {
                Long requestId = Long.parseLong(data.substring(CALLBACK_CANCEL_REQUEST_BUTTON.length()));
                handleCancelRequestFromButton(chatId, telegramUserId, requestId);
                return;
            }

            // ============================================
            // СДЕЛКИ
            // ============================================

            if (data.startsWith(CALLBACK_CREATE_DEAL)) {
                Long requestId = Long.parseLong(data.substring(CALLBACK_CREATE_DEAL.length()));
                handleCreateDeal(chatId, telegramUserId, requestId);
                return;
            }

            if (CALLBACK_CONFIRM_DEAL_AUTO.equals(data)) {
                handleConfirmDealAutomatic(chatId, telegramUserId);
                return;
            }

            if (data.startsWith(CALLBACK_CONFIRM_COMPLETED_DEAL)) {
                Long requestId = Long.parseLong(data.substring(CALLBACK_CONFIRM_COMPLETED_DEAL.length()));
                handleConfirmCompletedDeal(chatId, telegramUserId, requestId);
                return;
            }

            if (data.startsWith(CALLBACK_AUTHOR_CONFIRM)) {
                try {
                    Long responderChatId = Long.parseLong(data.substring(CALLBACK_AUTHOR_CONFIRM.length()));
                    handleAuthorConfirmDeal(chatId, telegramUserId, responderChatId);
                } catch (NumberFormatException e) {
                    log.error("Invalid responder chat ID: {}", data, e);
                    sendMessage(chatId, messageFormatter.formatStaleDataError());
                }
                return;
            }

            // ============================================
            // ОЦЕНКИ
            // ============================================

            if (data.startsWith(CALLBACK_RATE_DEAL)) {
                String[] parts = data.split(":");
                Long dealId = Long.parseLong(parts[1]);
                Integer rating = Integer.parseInt(parts[2]);
                handleRateDeal(chatId, telegramUserId, dealId, rating);
                return;
            }

            if (data.startsWith(CALLBACK_SKIP_RATING)) {
                Long dealId = Long.parseLong(data.substring(CALLBACK_SKIP_RATING.length()));
                handleSkipRating(chatId, telegramUserId, dealId);
                return;
            }

            // ============================================
            // ИСТОРИЯ
            // ============================================

            if (data.startsWith(CALLBACK_VIEW_DEAL)) {
                Long dealId = Long.parseLong(data.substring(CALLBACK_VIEW_DEAL.length()));
                handleViewDeal(chatId, telegramUserId, dealId);
                return;
            }

            if (data.startsWith(CALLBACK_HISTORY_PAGE)) {
                int page = Integer.parseInt(data.substring(CALLBACK_HISTORY_PAGE.length()));
                handleDealsHistoryPage(chatId, telegramUserId, page);
                return;
            }

            // ============================================
            // ОСТАЛЬНЫЕ ОБРАБОТЧИКИ (старые callback'и)
            // ============================================

            if (data.startsWith(CALLBACK_MENU)) {
                handleMenuCallback(chatId, telegramUserId, data);
                return;
            }

            if (data.startsWith(CALLBACK_CURRENCY)) {
                handleCurrencyCallback(chatId, telegramUserId, data);
                return;
            }

            if (data.startsWith(CALLBACK_METHOD)) {
                handleTransferMethodCallback(chatId, telegramUserId, data);
                return;
            }

            // Если ничего не подошло - логируем
            log.warn("Unhandled callback data: {}", data);

        } catch (Exception e) {
            log.error("Error handling callback: {}", e.getMessage(), e);
        }
    }
    private void handleSearchByCurrency(Long chatId, Long telegramUserId, String currency) {
        log.info("User {} searching for {}", telegramUserId, currency);

        try {
            conversationStateService.setUserData(telegramUserId, "last_search_currency", currency);

            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            ExchangeRequest.Currency iWant = ExchangeRequest.Currency.valueOf(currency);
            ExchangeRequest.Currency theyWant = iWant.equals(ExchangeRequest.Currency.PLN)
                    ? ExchangeRequest.Currency.KZT
                    : ExchangeRequest.Currency.PLN;

            List<ExchangeRequest> allRequests = exchangeService.findActiveByCurrency(theyWant);

            List<ExchangeRequest> requests = allRequests.stream()
                    .filter(req -> !req.getUser().getId().equals(user.getId()))
                    .toList();

            if (requests.isEmpty()) {
                String message = messageFormatter.formatNoSearchResults();

                InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                        .addButton("🔄 Обновить поиск", CALLBACK_SEARCH_CURRENCY + currency)
                        .addButton("💰 Создать заявку", CALLBACK_MENU + "need")
                        .newRow()
                        .addButton("🏠 Меню", CALLBACK_SHOW_MENU)
                        .build();

                sendMessageWithKeyboard(chatId, message, keyboard);
                return;
            }

            BigDecimal rate;
            if (iWant.equals(ExchangeRequest.Currency.PLN)) {
                rate = exchangeRateService.getCurrentKZTtoPLNRate();
            } else {
                rate = exchangeRateService.getCurrentPLNtoKZTRate();
            }

            String withFlag = iWant.equals(ExchangeRequest.Currency.PLN) ? "🇵🇱 PLN" : "🇰🇿 KZT";
            String theyWantWithFlag = theyWant.equals(ExchangeRequest.Currency.PLN) ? "🇵🇱 PLN" : "🇰🇿 KZT";

            String message = messageFormatter.formatSearchResultsList(
                    requests,
                    theyWantWithFlag,
                    withFlag,
                    iWant,
                    rate);

            TelegramKeyboardBuilder builder = TelegramKeyboardBuilder.create();

            int limit = Math.min(requests.size(), MAX_SEARCH_RESULTS);
            for (int i = 0; i < limit; i++) {
                ExchangeRequest req = requests.get(i);
                builder.addButton("📋 #" + (i + 1), CALLBACK_RESPOND + req.getId());

                if ((i + 1) % BUTTONS_PER_ROW == 0 || i == limit - 1) {
                    builder.newRow();
                }
            }

            InlineKeyboardMarkup keyboard = builder
                    .addButton("🔄 Обновить", CALLBACK_SEARCH_CURRENCY + currency)
                    .addButton("🏠 Меню", CALLBACK_SHOW_MENU)
                    .build();

            sendMessageWithKeyboard(chatId, message, keyboard);

        } catch (Exception e) {
            log.error("Error in search by currency: {}", e.getMessage(), e);
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }
    private void handleStartCommand(Long chatId, org.telegram.telegrambots.meta.api.objects.User telegramUser) {
        Long telegramUserId = telegramUser.getId();
        String telegramUsername = telegramUser.getUserName();
        String firstName = telegramUser.getFirstName();
        String lastName = telegramUser.getLastName();

        log.info("Processing /start for user: ID={}, username={}", telegramUserId, telegramUsername);

        try {
            if (telegramUsername == null || telegramUsername.isBlank()) {
                log.warn("User {} has no username", telegramUserId);
                sendMessage(chatId, messageFormatter.formatUsernameRequired());
                return;
            }

            BigDecimal currentRate = new BigDecimal("147.50");
            try {
                currentRate = exchangeRateService.getCurrentPLNtoKZTRate();
            } catch (Exception e) {
                log.warn("Failed to fetch rate: {}", e.getMessage());
            }

            Optional<User> existingUser = userService.findByTelegramUserId(telegramUserId);

            if (existingUser.isPresent()) {
                User user = existingUser.get();
                boolean needsUpdate = false;

                if (!Objects.equals(user.getFirstName(), firstName)) {
                    user.setFirstName(firstName);
                    needsUpdate = true;
                }
                if (!Objects.equals(user.getLastName(), lastName)) {
                    user.setLastName(lastName);
                    needsUpdate = true;
                }
                if (!Objects.equals(user.getTelegramUsername(), telegramUsername)) {
                    user.setTelegramUsername(telegramUsername);
                    needsUpdate = true;
                }

                if (needsUpdate) {
                    userService.save(user);
                }

                if (user.getIsPhoneVerified()) {
                    String welcomeMessage = messageFormatter.formatVerifiedUserWelcome(user, currentRate);

                    InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                            .addButton("🏠 Открыть меню", CALLBACK_SHOW_MENU)
                            .build();

                    sendMessageWithKeyboard(chatId, welcomeMessage, keyboard);
                } else {
                    sendPhoneVerificationRequest(chatId, currentRate, user);
                }
            } else {
                log.info("Registering new user: ID={}, username=@{}", telegramUserId, telegramUsername);

                User newUser = userService.registerUser(telegramUserId, telegramUsername, firstName, lastName);

                sendPhoneVerificationRequest(chatId, currentRate, newUser);

                log.info("✅ User registered: ID={}", newUser.getId());
            }
        } catch (Exception e) {
            log.error("❌ Error in /start: {}", e.getMessage(), e);
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }

    }

    private void sendPhoneVerificationRequest(Long chatId, BigDecimal currentRate, User user) throws org.telegram.telegrambots.meta.exceptions.TelegramApiException {
        String message = messageFormatter.formatMandatoryPhoneVerificationRequest(user, currentRate);
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.setText(message);
        sendMessage.setParseMode("HTML");
        sendMessage.setReplyMarkup(createMandatoryShareContactKeyboard());
        bot.execute(sendMessage);
    }

    private void handleHelpCommand(Long chatId) {
        log.info("Processing /help");
        String helpMessage = messageFormatter.formatHelpMessage();
        sendMessage(chatId, helpMessage);
    }

    private void handleRatesCommand(Long chatId) {
        log.info("Processing /rates");

        try {
            BigDecimal plnToKztRate = exchangeRateService.getCurrentPLNtoKZTRate();
            BigDecimal kztToPlnRate = exchangeRateService.getCurrentKZTtoPLNRate();

            ExchangeRateDTO rates = new ExchangeRateDTO(
                    plnToKztRate,
                    kztToPlnRate,
                    LocalDateTime.now().toLocalDate().toString()
            );

            String message = messageFormatter.formatExchangeRates(rates);
            sendMessageWithKeyboard(chatId, message, createMenuButton());

        } catch (Exception e) {
            log.error("Error getting rates: {}", e.getMessage());
            sendMessage(chatId, messageFormatter.formatExchangeRateError());
        }
    }

    private void handleMenuCommand(Long chatId, Long telegramUserId) {
        log.info("Processing /menu for user {}", telegramUserId);

        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            String message = messageFormatter.formatMainMenu(user);

            InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                    .addButton("💰 Создать запрос", CALLBACK_MENU+"need")
                    .addButton("🔍 Поиск", CALLBACK_MENU+"search")
                    .newRow()
                    .addButton("📋 Мои заявки", CALLBACK_MENU+"my_requests")
                    .addButton("👤 Мой профиль", CALLBACK_MENU+"profile")
                    .newRow()
                    .addButton("💹 Курсы", CALLBACK_MENU+"rates")
                    .addButton("❓ Справка", CALLBACK_MENU+"help")
                    .newRow()
                    .addButton("📜 История обменов", CALLBACK_MENU+"history")
                    .build();

            sendMessageWithKeyboard(chatId, message, keyboard);
        } catch (Exception e) {
            log.error("Error in /menu: {}", e.getMessage());
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }

    private void handleNeedCommand(Long chatId, Long telegramUserId) {
        log.info("Starting /need for user {}", telegramUserId);

        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            conversationStateService.setState(telegramUserId, ConversationState.AWAITING_CURRENCY);

            InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                    .addButton("🇵🇱 PLN (Злоты)", CALLBACK_CURRENCY + PLN)
                    .addButton("🇰🇿 KZT (Тенге)", CALLBACK_CURRENCY + KZT)
                    .newRow()
                    .addButton("🏠 Главное меню", CALLBACK_SHOW_MENU)
                    .build();

            sendMessageWithKeyboard(chatId, messageFormatter.formatNeedStep1Currency(), keyboard);

        } catch (Exception e) {
            log.error("Error in /need: {}", e.getMessage());
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }
    private void handleSearchCommand(Long chatId, Long telegramUserId) {
        log.info("Processing /search for user {}", telegramUserId);

        try {
            if (!checkPhoneVerification(chatId, telegramUserId)) {
                return;
            }

            String lastSearchCurrency = conversationStateService.getUserData(telegramUserId, "last_search_currency");

            if (lastSearchCurrency != null && !lastSearchCurrency.equals("null")) {
                String message = messageFormatter.formatRepeatSearchOrChooseAnother(lastSearchCurrency);

                InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                        .addButton("🔄 Повторить (" + lastSearchCurrency + ")",
                                CALLBACK_SEARCH_CURRENCY + lastSearchCurrency)
                        .newRow()
                        .addButton("🇵🇱 Ищу PLN", CALLBACK_SEARCH_CURRENCY + PLN)
                        .addButton("🇰🇿 Ищу KZT", CALLBACK_SEARCH_CURRENCY + KZT)
                        .newRow()
                        .addButton("🏠 Меню", CALLBACK_SHOW_MENU)
                        .build();

                sendMessageWithKeyboard(chatId, message, keyboard);
            } else {
                showSearchCurrencySelection(chatId);
            }

        } catch (Exception e) {
            log.error("Error in /search: {}", e.getMessage());
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }

    private void showSearchCurrencySelection(Long chatId) {
        String message = messageFormatter.formatSearchCurrencySelection();

        InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                .addButton("🇵🇱 Ищу PLN", CALLBACK_SEARCH_CURRENCY + PLN)
                .addButton("🇰🇿 Ищу KZT", CALLBACK_SEARCH_CURRENCY + KZT)
                .newRow()
                .addButton("🏠 Меню", CALLBACK_SHOW_MENU)
                .build();

        sendMessageWithKeyboard(chatId, message, keyboard);
    }

    private void handleMenuCallback(Long chatId, Long telegramUserId, String data) {
        String action = data.substring(5);
        switch (action) {
            case "need" -> handleNeedCommand(chatId, telegramUserId);
            case "search" -> handleSearchCommand(chatId, telegramUserId);
            case "my_requests" -> handleMyRequestsCommand(chatId, telegramUserId);
            case "profile" -> handleProfileCommand(chatId, telegramUserId);
            case "history" -> handleDealsHistoryCommand(chatId, telegramUserId);
            case "rates" -> handleRatesCommand(chatId);
            case "help" -> handleHelpCommand(chatId);
            default -> sendMessage(chatId, messageFormatter.formatUnknownCommand());
        }
    }
    private void handleCurrencyCallback(Long chatId, Long telegramUserId, String data) {
        String currency = data.substring(9);
        conversationStateService.setUserData(telegramUserId, "currency", currency);
        conversationStateService.setState(telegramUserId, ConversationState.AWAITING_AMOUNT);
        sendMessage(chatId, messageFormatter.formatNeedStep2Amount(currency));
    }

    private void handleTransferMethodCallback(Long chatId, Long telegramUserId, String data) {
        String method = data.substring(CALLBACK_METHOD.length());
        conversationStateService.setUserData(telegramUserId, "method", method);
        conversationStateService.setState(telegramUserId, ConversationState.AWAITING_COMMENT);

        InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                .addButton("⏭️ Пропустить", CALLBACK_SKIP_COMMENT)
                .build();

        String methodName = messageFormatter.getTransferMethodName(method);
        String message = messageFormatter.formatNeedStep4Comment(methodName);

        sendMessageWithKeyboard(chatId, message, keyboard);
    }

    /**
     * Подтверждение автоматической сделки (когда есть активная заявка)
     */
    private void handleConfirmDealAutomatic(Long chatId, Long telegramUserId) {
        try {
            String requestIdStr = conversationStateService.getUserData(telegramUserId, "deal_request_id");
            String amountStr = conversationStateService.getUserData(telegramUserId, "deal_amount");
            String responderRequestIdStr = conversationStateService.getUserData(telegramUserId, "responder_request_id");

            Long targetRequestId = Long.parseLong(requestIdStr);
            BigDecimal dealAmount = new BigDecimal(amountStr);
            Long responderRequestId = responderRequestIdStr != null && !responderRequestIdStr.equals("null")
                    ? Long.parseLong(responderRequestIdStr)
                    : null;

            ExchangeRequest targetRequest = exchangeService.findByIdWithUser(targetRequestId);
            User responder = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
            User author = targetRequest.getUser();

            conversationStateService.setUserData(author.getTelegramUserId(), "pending_deal_data",
                    targetRequestId + ":" + dealAmount + ":" + (responderRequestId != null ? responderRequestId : "null"));
            conversationStateService.setUserData(author.getTelegramUserId(), "responder_user_id",
                    telegramUserId.toString());
            conversationStateService.setUserData(author.getTelegramUserId(), "responder_chat_id",
                    chatId.toString());

            conversationStateService.clearState(telegramUserId);

            // ⭐ ПРАВИЛЬНАЯ ЛОГИКА КОНВЕРТАЦИИ
            ExchangeRequest.Currency requestCurrency = targetRequest.getCurrencyNeed();  // Что хочет АВТОР получить
            ExchangeRequest.Currency oppositeCurrency = requestCurrency == ExchangeRequest.Currency.PLN
                    ? ExchangeRequest.Currency.KZT
                    : ExchangeRequest.Currency.PLN;

            // dealAmount - это сумма в валюте ЗАЯВКИ (то что автор ПОЛУЧИТ)
            BigDecimal authorWillReceive = dealAmount;  // Автор получит эту сумму

            // Рассчитываем сколько автор ОТДАСТ
            BigDecimal rate;
            BigDecimal authorWillGive;

            if (requestCurrency == ExchangeRequest.Currency.KZT) {
                // Автор хочет KZT, значит отдаст PLN
                rate = exchangeRateService.getCurrentKZTtoPLNRate();  // 1 KZT = 0.00681 PLN
                authorWillGive = dealAmount.multiply(rate);  // KZT → PLN
            } else {
                // Автор хочет PLN, значит отдаст KZT
                rate = exchangeRateService.getCurrentPLNtoKZTRate();  // 1 PLN = 146.8 KZT
                authorWillGive = dealAmount.multiply(rate);  // PLN → KZT
            }

            // ⭐ УВЕДОМЛЕНИЕ АВТОРУ
            String notification = messageFormatter.formatNewDealProposalNotification(
                    responder,
                    authorWillReceive,
                    requestCurrency,
                    authorWillGive,
                    oppositeCurrency
            );

            confirmExchange(chatId, telegramUserId, targetRequestId, dealAmount, targetRequest, author, requestCurrency, oppositeCurrency, authorWillReceive, authorWillGive, notification);

        } catch (Exception e) {
            log.error("Error confirming automatic deal: {}", e.getMessage(), e);
            conversationStateService.clearState(telegramUserId);
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }

    private void confirmExchange(Long chatId, Long telegramUserId, Long targetRequestId, BigDecimal dealAmount, ExchangeRequest targetRequest, User author, ExchangeRequest.Currency requestCurrency, ExchangeRequest.Currency oppositeCurrency, BigDecimal authorWillReceive, BigDecimal authorWillGive, String notification) {
        InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                .addButton("✅ Подтвердить обмен", CALLBACK_AUTHOR_CONFIRM + chatId)
                .build();

        sendMessageWithKeyboard(author.getTelegramUserId(), notification, keyboard);

        String message = messageFormatter.formatOfferSentConfirmationToResponder(author, authorWillGive, oppositeCurrency, authorWillReceive, requestCurrency);

        InlineKeyboardMarkup responderKeyboard = TelegramKeyboardBuilder.create()
                .addUrlButton("💬 Написать @" + author.getTelegramUsername(),
                        "https://t.me/" + author.getTelegramUsername())
                .build();

        sendMessageWithKeyboard(chatId, message, responderKeyboard);

        log.info("✅ Deal proposal sent: {} {} from user {} to request {}",
                dealAmount, targetRequest.getCurrencyNeed(), telegramUserId, targetRequestId);
    }

    /**
     * Автор подтверждает сделку - создаём Deal
     */
    @Transactional
    private void handleAuthorConfirmDeal(Long chatId, Long telegramUserId, Long responderChatId) {
        try {
            String callbackData = conversationStateService.getUserData(telegramUserId, "pending_deal_data");
            if (callbackData == null) {
                log.warn("No pending deal data for user {} - deal already completed or expired", telegramUserId);
                sendMessage(chatId, "✅ Сделка уже завершена ранее");
                return;
            }

            String[] parts = callbackData.split(":");
            Long targetRequestId = Long.parseLong(parts[0]);
            BigDecimal dealAmount = new BigDecimal(parts[1]);
            Long responderRequestId = parts[2].equals("null") ? null : Long.parseLong(parts[2]);

            User author = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException("Author not found"));

            User responder = userService.findByTelegramUserId(
                    conversationStateService.getUserData(telegramUserId, "responder_user_id") != null
                            ? Long.parseLong(conversationStateService.getUserData(telegramUserId, "responder_user_id"))
                            : null
            ).orElseThrow(() -> new UserNotFoundException("Responder not found"));

            ExchangeRequest targetRequest = exchangeService.findByIdWithUser(targetRequestId);

            // Создаём сделку
            Deal deal = dealService.createDealFromRequest(
                    targetRequestId,
                    responder.getId(),
                    dealAmount
            );

            log.info("✅ Deal created: ID={}, status=COMPLETED", deal.getId());

            // Загружаем обновлённую заявку автора
            ExchangeRequest updatedTargetRequest = exchangeService.findByIdWithUser(targetRequestId);

            // РАСЧЁТЫ ДЛЯ АВТОРА
            ExchangeRequest.Currency requestCurrency = targetRequest.getCurrencyNeed();
            ExchangeRequest.Currency oppositeCurrency = requestCurrency == ExchangeRequest.Currency.PLN
                    ? ExchangeRequest.Currency.KZT
                    : ExchangeRequest.Currency.PLN;

            BigDecimal authorReceived = dealAmount;

            BigDecimal rate;
            BigDecimal authorGave;

            if (requestCurrency == ExchangeRequest.Currency.KZT) {
                rate = exchangeRateService.getCurrentKZTtoPLNRate();
                authorGave = dealAmount.multiply(rate);
            } else {
                rate = exchangeRateService.getCurrentPLNtoKZTRate();
                authorGave = dealAmount.multiply(rate);
            }

            // ⭐ ПОЛУЧАЕМ ЗАЯВКУ RESPONDER'А ЕСЛИ ЕСТЬ
            ExchangeRequest responderRequest = null;
            if (responderRequestId != null) {
                try {
                    responderRequest = exchangeService.findByIdWithUser(responderRequestId);
                } catch (Exception e) {
                    log.warn("Responder request {} not found", responderRequestId);
                }
            }

            // УВЕДОМЛЕНИЕ АВТОРУ
            String authorMessage = messageFormatter.formatDealCompletionForAuthor(
                    deal,
                    authorReceived,
                    requestCurrency,
                    authorGave,
                    oppositeCurrency,
                    updatedTargetRequest
            );

            sendMessageWithKeyboard(chatId, authorMessage, createRatingKeyboard(deal.getId()));

            // УВЕДОМЛЕНИЕ ОТКЛИКНУВШЕМУСЯ
            String responderMessage = messageFormatter.formatDealCompletionForResponder(
                    deal,
                    authorGave,
                    oppositeCurrency,
                    authorReceived,
                    requestCurrency,
                    responderRequest  // ⭐ Передаём уже полученный объект
            );

            sendMessageWithKeyboard(responderChatId, responderMessage, createRatingKeyboard(deal.getId()));

            conversationStateService.clearState(telegramUserId);

            log.info("✅ Deal {} completed successfully. Author: {}, Provider: {}, Amount: {} {}",
                    deal.getId(), author.getTelegramUsername(), responder.getTelegramUsername(),
                    dealAmount, requestCurrency);

        } catch (Exception e) {
            log.error("Error confirming deal: {}", e.getMessage(), e);
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }

    /**
     * Создание клавиатуры для оценки (1-5 звёзд) + кнопка "Пропустить"
     */
    private InlineKeyboardMarkup createRatingKeyboard(Long dealId) {
        TelegramKeyboardBuilder builder = TelegramKeyboardBuilder.create();

        // Кнопки оценки (1-5 звёзд)
        for (int i = 1; i <= 5; i++) {
            builder.addButton(i + "⭐", CALLBACK_RATE_DEAL + dealId + ":" + i);
        }

        return builder.newRow()
                .addButton("⏭️Пропустить оценку", CALLBACK_SKIP_RATING + dealId)
                .build();
    }
    /**
     * Обработка оценки сделки
     */
    private void handleRateDeal(Long chatId, Long telegramUserId, Long dealId, Integer ratingValue) {
        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            // ⭐ ИСПОЛЬЗУЕМ СУЩЕСТВУЮЩИЙ createRating
            CreateRatingDTO dto = new CreateRatingDTO(dealId, BigDecimal.valueOf(ratingValue));
            Rating rating = ratingService.createRating(dto, user.getTelegramUsername());

            Deal deal = dealService.findByIdWithUsers(dealId);

            // Определяем кого оценили
            User ratedUser = deal.getRequester().getId().equals(user.getId())
                    ? deal.getProvider()
                    : deal.getRequester();

            String ratedUsername = ratedUser.getTelegramUsername();

            String message = messageFormatter.formatRatingThankYou(ratedUsername, ratingValue, dealId);

            InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                    .addButton("🏠 Меню", "show_menu")
                    .build();

            sendMessageWithKeyboard(chatId, message, keyboard);

            log.info("✅ Deal #{} rated with {} stars by @{}", dealId, ratingValue, user.getTelegramUsername());

        } catch (RuntimeException e) {
            String errorMessage = e.getMessage();
            if (errorMessage.contains("already exists")) {
                sendMessage(chatId, "❌ Вы уже оценили эту сделку");
            } else if (errorMessage.contains("not a participant")) {
                sendMessage(chatId, "❌ Вы не участвуете в этой сделке");
            } else if (errorMessage.contains("not found")) {
                sendMessage(chatId, "❌ Сделка не найдена");
            } else {
                sendMessage(chatId, "❌ Ошибка: " + errorMessage);
            }
            log.error("Error rating deal {}: {}", dealId, errorMessage);
        } catch (Exception e) {
            log.error("Unexpected error rating deal: {}", e.getMessage(), e);
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }

    /**
     * Пропуск оценки
     */
    private void handleSkipRating(Long chatId, Long telegramUserId, Long dealId) {
        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException("User not found"));

            boolean alreadyRated = ratingService.existsByDealIdAndRaterId(dealId, user.getId());
            if (alreadyRated) {
                sendMessage(chatId, "ℹ️ Вы уже оценили этот обмен");
                return;
            }

            Deal deal = dealService.findByIdWithUsers(dealId);

            if (!deal.isUserParticipant(user.getId())) {
                sendMessage(chatId, "❌ У вас нет доступа к этому обмену");
                return;
            }

            boolean isRequester = deal.getRequester().getId().equals(user.getId());

            // ⭐ НОВЫЙ УПРОЩЁННЫЙ ФОРМАТТЕР
            String message = messageFormatter.formatRatingSkipped(deal, isRequester);

            InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                    .addButton("🏠 Меню", CALLBACK_SHOW_MENU)
                    .build();

            sendMessageWithKeyboard(chatId, message, keyboard);

            log.info("User {} skipped rating for deal {}", user.getTelegramUsername(), dealId);

        } catch (Exception e) {
            log.error("Error skipping rating: {}", e.getMessage(), e);
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }

    private void handleSkipCommentCallback(Long chatId, Long telegramUserId) {
        createExchangeRequestFromData(chatId, telegramUserId, null);
    }

    private void handleCancelRequestCallback(Long chatId, Long telegramUserId) {
        conversationStateService.clearState(telegramUserId);
        sendMessage(chatId, messageFormatter.formatRequestCancelled());
    }

    private void handleRequestConfirmation(Long chatId, Long telegramUserId) {
        log.info("Request confirmed by user {}", telegramUserId);
        createExchangeRequestFromData(chatId, telegramUserId,
                conversationStateService.getUserData(telegramUserId, "comment"));
    }

    private void handleRequestEdit(Long chatId, Long telegramUserId) {
        log.info("Request edit by user {}", telegramUserId);
        conversationStateService.clearState(telegramUserId);
        handleNeedCommand(chatId, telegramUserId);
    }

    private void handleViewRequest(Long chatId, Long telegramUserId, Long requestId) {
        log.info("Viewing request {} by user {}", requestId, telegramUserId);
        try {
            ExchangeRequest request = exchangeService.findById(requestId);
            sendMessage(chatId, "Заявка #" + requestId + "\n" +
                    request.getAmountNeed() + " " + request.getCurrencyNeed());
        } catch (Exception e) {
            sendMessage(chatId, "❌ Заявка не найдена");
        }
    }

    private void handleCreateDeal(Long chatId, Long telegramUserId, Long requestId) {
        log.info("Creating deal for request {} by user {}", requestId, telegramUserId);
        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            ExchangeRequest request = exchangeService.findById(requestId);
            Deal deal = dealService.createDealFromRequest(requestId, user.getId(), request.getAmountNeed());

            sendMessage(chatId, "✅ Сделка создана! ID: " + deal.getId());
        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка создания сделки");
        }
    }

    private void handleCancelRequestFromButton(Long chatId, Long telegramUserId, Long requestId) {
        log.info("Cancelling request {} by user {}", requestId, telegramUserId);
        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            exchangeService.cancelExchangeRequest(requestId, user.getId());
            sendMessage(chatId, "✅ Заявка отменена");
        } catch (Exception e) {
            sendMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    private void handleConversationState(Long chatId, Long telegramUserId, String text, ConversationState state) {
        if (text.startsWith("/")) {
            log.info("User {} sent command while in state {}, clearing state", telegramUserId, state);
            conversationStateService.clearState(telegramUserId);
            return;
        }

        switch (state) {
            case AWAITING_AMOUNT -> handleAmountInput(chatId, telegramUserId, text);
            case AWAITING_COMMENT -> handleCommentInput(chatId, telegramUserId, text);
            case EDITING_EXCHANGE_REQUEST_AMOUNT -> handleEditAmountInput(chatId, telegramUserId, text);
            case AWAITING_DEAL_AMOUNT -> handleDealAmountInput(chatId, telegramUserId, text);
            default -> {
                log.warn("Unexpected state: {} for user {}", state, telegramUserId);
                conversationStateService.clearState(telegramUserId);
                sendMessage(chatId, "❌ Что-то пошло не так. Используйте /menu для возврата в главное меню.");
            }
        }
    }

    private void handleAmountInput(Long chatId, Long telegramUserId, String text) {
        try {
            String readyText = text.trim()
                    .replace(" ", "")
                    .replace(",", ".");
            BigDecimal amount = new BigDecimal(readyText);

            if (amount.compareTo(MIN_EXCHANGE_AMOUNT) < 0) {
                sendMessage(chatId, messageFormatter.formatAmountTooSmallError());
                return;
            }

            conversationStateService.setUserData(telegramUserId, "amount", amount.toString());
            conversationStateService.setState(telegramUserId, ConversationState.AWAITING_TRANSFER_METHOD);

            InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                    .addButton("🏦 Банковский перевод", CALLBACK_METHOD + "BANK_TRANSFER")
                    .newRow()
                    .addButton("💵 Наличные", CALLBACK_METHOD + "CASH")
                    .build();

            String currency = conversationStateService.getUserData(telegramUserId, "currency");
            String message = messageFormatter.formatNeedStep3TransferMethod(amount, currency);

            sendMessageWithKeyboard(chatId, message, keyboard);

        } catch (NumberFormatException e) {
            sendMessage(chatId, messageFormatter.formatInvalidAmountFormatError());
        }
    }
    /**
     * Показать только МОИ ЗАЯВКИ (без статистики)
     */
    private void handleMyRequestsCommand(Long chatId, Long telegramUserId) {
        log.info("Processing my_requests for user {}", telegramUserId);

        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            List<ExchangeRequest> activeRequests = exchangeService.getActiveByUserId(user.getId());

            String message = messageFormatter.formatUserStatus(user, activeRequests);
            InlineKeyboardMarkup keyboard = createStatusButtons(activeRequests.isEmpty(), activeRequests);
            sendMessageWithKeyboard(chatId, message, keyboard);

        } catch (Exception e) {
            log.error("Error in my_requests: {}", e.getMessage());
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }
    /**
     * Показать МОЙ ПРОФИЛЬ (статистика как в /start)
     */
    private void handleProfileCommand(Long chatId, Long telegramUserId) {
        log.info("Processing profile for user {}", telegramUserId);

        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            BigDecimal currentRate = exchangeRateService.getCurrentPLNtoKZTRate();

            String message = messageFormatter.formatUserProfile(user, currentRate);

            InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                    .addButton("🏠 Меню", "show_menu")
                    .build();

            sendMessageWithKeyboard(chatId, message, keyboard);

        } catch (Exception e) {
            log.error("Error in profile: {}", e.getMessage());
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }
    /**
     * Показать меню управления заявками (Редактировать / Отменить)
     */
    private void showRequestManagementOptions(Long chatId, Long telegramUserId) {
        log.info("Showing request management options for user {}", telegramUserId);

        InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                .addButton("✏️ Редактировать заявку", CALLBACK_ACTION_EDIT_REQUEST)
                .newRow()
                .addButton("❌ Отменить заявку", CALLBACK_ACTION_CANCEL_REQUEST)
                .newRow()
                .addButton("◀️ Назад", CALLBACK_MENU + "my_requests")
                .build();

        sendMessageWithKeyboard(chatId, "⚙️ <b>Управление заявками</b>\n\nВыберите действие:", keyboard);
    }

    /**
     * Показать список заявок для редактирования
     */
    private void showRequestListForEdit(Long chatId, Long telegramUserId) {
        log.info("Showing request list for edit for user {}", telegramUserId);

        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            List<ExchangeRequest> activeRequests = exchangeService.getActiveByUserId(user.getId());

            if (activeRequests.isEmpty()) {
                sendMessage(chatId, "У вас нет активных заявок");
                return;
            }

            String message = messageFormatter.formatSelectRequestToEdit(activeRequests);

            TelegramKeyboardBuilder builder = TelegramKeyboardBuilder.create();

            List<Long> requestIds = new ArrayList<>();

            for (int i = 0; i < activeRequests.size(); i++) {
                ExchangeRequest req = activeRequests.get(i);
                requestIds.add(req.getId());

                builder.addButton("✏️ Заявка #" + (i + 1), CALLBACK_SELECT_EDIT + i)
                        .newRow();
            }

            conversationStateService.setUserData(telegramUserId, "edit_request_list",
                    requestIds.stream().map(String::valueOf).collect(Collectors.joining(",")));

            InlineKeyboardMarkup keyboard = builder
                    .addButton("◀️ Назад", CALLBACK_SHOW_REQUEST_MANAGEMENT)
                    .build();

            sendMessageWithKeyboard(chatId, message, keyboard);

        } catch (Exception e) {
            log.error("Error showing edit list: {}", e.getMessage());
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }

    /**
     * Начать редактирование заявки
     */
    private void startEditingRequest(Long chatId, Long telegramUserId, int index) {
        try {
            String requestListStr = conversationStateService.getUserData(telegramUserId, "edit_request_list");
            if (requestListStr == null) {
                sendMessage(chatId, "❌ Ошибка: список заявок не найден");
                return;
            }

            List<Long> requestIds = Arrays.stream(requestListStr.split(","))
                    .map(Long::parseLong)
                    .toList();

            if (index < 0 || index >= requestIds.size()) {
                sendMessage(chatId, "❌ Ошибка: неверный номер заявки");
                return;
            }

            Long requestId = requestIds.get(index);
            ExchangeRequest request = exchangeService.findById(requestId);

            conversationStateService.setState(telegramUserId, ConversationState.EDITING_EXCHANGE_REQUEST_AMOUNT);
            conversationStateService.setUserData(telegramUserId, "edit_request_id", requestId.toString());

            sendMessage(chatId, "✏️ <b>Изменение суммы</b>\n\n" +
                    "Текущая сумма: <b>" + messageFormatter.formatAmount(request.getAmountNeed()) +
                    " " + request.getCurrencyNeed() + "</b>\n\n" +
                    "Введите новую сумму:");

        } catch (Exception e) {
            log.error("Error starting edit: {}", e.getMessage());
            sendMessage(chatId, "❌ Ошибка: заявка не найдена");
        }
    }

    /**
     * Обработка ввода новой суммы при редактировании
     */
    private void handleEditAmountInput(Long chatId, Long telegramUserId, String text) {
        try {
            BigDecimal newAmount = new BigDecimal(text.replace(",", "."));

            if (newAmount.compareTo(MIN_EXCHANGE_AMOUNT) < 0) {
                sendMessage(chatId, "❌ Минимальная сумма: 10");
                return;
            }

            String requestIdStr = conversationStateService.getUserData(telegramUserId, "edit_request_id");
            Long requestId = Long.parseLong(requestIdStr);

            ExchangeRequest oldRequest = exchangeService.findById(requestId);
            BigDecimal oldAmount = oldRequest.getAmountNeed();
            String currency = oldRequest.getCurrencyNeed().toString();

            exchangeService.updateExchangeRequest(requestId, newAmount, null);
            conversationStateService.clearState(telegramUserId);

            String message = messageFormatter.formatRequestUpdated(oldAmount, newAmount, currency);

            InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                    .addButton("📊 Мои заявки", CALLBACK_MENU+"my_requests")
                    .addButton("🏠 Меню", "show_menu")
                    .build();

            sendMessageWithKeyboard(chatId, message, keyboard);

            log.info("✅ Request {} updated: {} → {} {}", requestId, oldAmount, newAmount, currency);

        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат суммы. Введите число, например: 10000, 10 000 или 500,50");
        } catch (Exception e) {
            log.error("Error editing amount: {}", e.getMessage());
            conversationStateService.clearState(telegramUserId);
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }

    /**
     * Обработка ввода суммы для сделки (ручной ввод)
     */
    private void handleDealAmountInput(Long chatId, Long telegramUserId, String text) {
        try {
            // ⭐ УБИРАЕМ ПРОБЕЛЫ
            BigDecimal dealAmount = new BigDecimal(text.replace(" ", "").replace(",", "."));

            if (dealAmount.compareTo(MIN_EXCHANGE_AMOUNT) < 0) {
                sendMessage(chatId, "❌ Минимальная сумма: 10");
                return;
            }

            String requestIdStr = conversationStateService.getUserData(telegramUserId, "deal_request_id");
            Long requestId = Long.parseLong(requestIdStr);

            ExchangeRequest request = exchangeService.findByIdWithUser(requestId);

            if (dealAmount.compareTo(request.getAmountNeed()) > 0) {
                sendMessage(chatId, "❌ Сумма не может превышать " +
                        messageFormatter.formatAmount(request.getAmountNeed()) + " " + request.getCurrencyNeed());
                return;
            }

            User responder = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));
            User author = request.getUser();

            // ⭐ ПРАВИЛЬНАЯ ЛОГИКА КОНВЕРТАЦИИ
            ExchangeRequest.Currency requestCurrency = request.getCurrencyNeed();
            ExchangeRequest.Currency oppositeCurrency = requestCurrency == ExchangeRequest.Currency.PLN
                    ? ExchangeRequest.Currency.KZT
                    : ExchangeRequest.Currency.PLN;

            BigDecimal authorWillReceive = dealAmount;

            BigDecimal rate;
            BigDecimal authorWillGive;

            if (requestCurrency == ExchangeRequest.Currency.KZT) {
                rate = exchangeRateService.getCurrentKZTtoPLNRate();
                authorWillGive = dealAmount.multiply(rate);
            } else {
                rate = exchangeRateService.getCurrentPLNtoKZTRate();
                authorWillGive = dealAmount.multiply(rate);
            }

            conversationStateService.setUserData(author.getTelegramUserId(), "pending_deal_data",
                    requestId + ":" + dealAmount + ":null");
            conversationStateService.setUserData(author.getTelegramUserId(), "responder_user_id",
                    telegramUserId.toString());
            conversationStateService.setUserData(author.getTelegramUserId(), "responder_chat_id",
                    chatId.toString());

            conversationStateService.clearState(telegramUserId);

            String notification = messageFormatter.formatNewOfferNotificationToAuthor(responder, authorWillReceive, requestCurrency, authorWillGive, oppositeCurrency);

            confirmExchange(chatId, telegramUserId, requestId, dealAmount, request, author, requestCurrency, oppositeCurrency, authorWillReceive, authorWillGive, notification);

        } catch (NumberFormatException e) {
            sendMessage(chatId, "❌ Неверный формат суммы. Введите число, например: 10000, 10 000 или 500,50");
        } catch (Exception e) {
            log.error("Error processing deal amount: {}", e.getMessage());
            conversationStateService.clearState(telegramUserId);
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }

    private void handleCommentInput(Long chatId, Long telegramUserId, String text) {
        createExchangeRequestFromData(chatId, telegramUserId, text);
    }

    /**
     * Подтверждение завершённой сделки (после отклика)
     */
    private void handleConfirmCompletedDeal(Long chatId, Long telegramUserId, Long requestId) {
        try {
            User responder = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            String authorIdStr = conversationStateService.getUserData(telegramUserId, "pending_response_author_id");
            if (authorIdStr == null) {
                sendMessage(chatId, "❌ Ошибка: данные не найдены");
                return;
            }

            Long authorTelegramId = Long.parseLong(authorIdStr);

            // Показываем форму для ввода суммы
            conversationStateService.setState(telegramUserId, ConversationState.AWAITING_DEAL_AMOUNT);
            conversationStateService.setUserData(telegramUserId, "deal_request_id", requestId.toString());
            conversationStateService.setUserData(telegramUserId, "deal_author_telegram_id", Long.toString(authorTelegramId));

            ExchangeRequest request = exchangeService.findByIdWithUser(requestId);

            String message = messageFormatter.formatConfirmDealAmountRequest(request);

            sendMessage(chatId, message);

        } catch (Exception e) {
            log.error("Error confirming completed deal: {}", e.getMessage(), e);
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }
    private void createExchangeRequestFromData(Long chatId, Long telegramUserId, String comment) {
        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            String currency = conversationStateService.getUserData(telegramUserId, "currency");
            String amountStr = conversationStateService.getUserData(telegramUserId, "amount");
            String methodStr = conversationStateService.getUserData(telegramUserId, "method");

            BigDecimal amount = new BigDecimal(amountStr);
            TransferMethod method = TransferMethod.valueOf(methodStr);

            ExchangeRequest request = exchangeService.createExchangeRequest(
                    user.getId(),
                    currency,
                    amount,
                    method,
                    comment
            );

            conversationStateService.clearState(telegramUserId);

            BigDecimal rate = currency.equals(PLN)
                    ? exchangeRateService.getCurrentPLNtoKZTRate()
                    : exchangeRateService.getCurrentKZTtoPLNRate();

            String otherCurrency = currency.equals(PLN) ? KZT : PLN;
            BigDecimal equivalent = amount.multiply(rate);

            String methodName = messageFormatter.getTransferMethodName(methodStr);

            String successMessage = messageFormatter.formatExchangeRequestCreated(
                    amount,
                    currency,
                    equivalent,
                    otherCurrency,
                    methodName,
                    comment);

            sendMessage(chatId, successMessage);

            showMatchingOffers(chatId, user, request);

            log.info("✅ Request created: ID={}, user={}, {} {}",
                    request.getId(), user.getTelegramUsername(), amount, currency);

        } catch (BusinessException e) {  // ⭐ ЛОВИМ BusinessException ОТДЕЛЬНО!
            log.error("Business error creating request: {}", e.getMessage());
            conversationStateService.clearState(telegramUserId);
            sendMessage(chatId, "❌ " + e.getMessage());  // ⭐ ПОКАЗЫВАЕМ КОНКРЕТНУЮ ОШИБКУ!
        } catch (Exception e) {
            log.error("Error creating request: {}", e.getMessage(), e);
            conversationStateService.clearState(telegramUserId);
            sendMessage(chatId, messageFormatter.formatRequestCreationError());
        }
    }

    /**
     * Показать подходящие предложения после создания заявки
     */
    private void showMatchingOffers(Long chatId, User user, ExchangeRequest request) {
        try {
            // ⭐ ПРАВИЛЬНАЯ ЛОГИКА:
            // Если я создал "Нужно KZT" → ищу тех, кому "Нужно PLN"
            ExchangeRequest.Currency myNeed = request.getCurrencyNeed();
            ExchangeRequest.Currency theirNeed = myNeed.equals(ExchangeRequest.Currency.PLN)
                    ? ExchangeRequest.Currency.KZT
                    : ExchangeRequest.Currency.PLN;

            // Ищем заявки с ПРОТИВОПОЛОЖНОЙ валютой
            Pageable pageable = PageRequest.of(0, MAX_MATCHING_OFFERS);
            Page<ExchangeRequest> matchesPage = exchangeService.getRequestsByCurrency(theirNeed, pageable);

            List<ExchangeRequest> matches = matchesPage.getContent()
                    .stream()
                    .filter(req -> !req.getUser().getId().equals(user.getId()))
                    .limit(MAX_MATCHING_OFFERS)
                    .toList();

            if (matches.isEmpty()) {
                String message = messageFormatter.formatNoSearchResults();

                InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                        .addButton("🔍 Поиск", CALLBACK_MENU+"search")
                        .newRow()
                        .addButton("🏠 Меню", "show_menu")
                        .build();

                sendMessageWithKeyboard(chatId, message, keyboard);
                return;
            }

            // Получаем курс для конвертации
            BigDecimal rate;
            if (myNeed.equals(ExchangeRequest.Currency.PLN)) {
                rate = exchangeRateService.getCurrentKZTtoPLNRate();  // Их KZT → мой PLN
            } else {
                rate = exchangeRateService.getCurrentPLNtoKZTRate();  // Их PLN → мой KZT
            }

            String theyWantWithFlag = theirNeed.equals(ExchangeRequest.Currency.PLN) ? "🇵🇱 PLN" : "🇰🇿 KZT";
            String whatTheyHaveWithFlag = myNeed.equals(ExchangeRequest.Currency.PLN) ? "🇵🇱 PLN" : "🇰🇿 KZT";

            String message = messageFormatter.formatMatchingOffers(
                    matches,
                    theyWantWithFlag,
                    whatTheyHaveWithFlag,
                    myNeed,
                    rate
            );

            TelegramKeyboardBuilder builder = TelegramKeyboardBuilder.create();

            for (int i = 0; i < matches.size(); i++) {
                ExchangeRequest match = matches.get(i);
                builder.addButton("📋 Предложение #" + (i + 1), CALLBACK_RESPOND + match.getId());

                if ((i + 1) % 2 == 0 || i == matches.size() - 1) {
                    builder.newRow();
                }
            }

            InlineKeyboardMarkup keyboard = builder
                    .addButton("📊 Мои заявки", CALLBACK_MENU+"my_requests")
                    .addButton("🔍 Поиск", CALLBACK_MENU+"search")
                    .newRow()
                    .addButton("🏠 Меню", "show_menu")
                    .build();

            sendMessageWithKeyboard(chatId, message, keyboard);

        } catch (Exception e) {
            log.error("Error showing matching offers: {}", e.getMessage(), e);
        }
    }

    private boolean checkPhoneVerification(Long chatId, Long telegramUserId) {
        try {
            User user = userService.findByTelegramUserId(telegramUserId).orElse(null);

            if (user == null) {
                sendMessage(chatId, messageFormatter.formatUserNotFoundError());
                return false;
            }

            if (!user.getIsPhoneVerified()) {
                sendMessage(chatId, messageFormatter.formatVerificationRequired());
                return false;
            }

            return true;
        } catch (Exception e) {
            log.error("Error checking verification: {}", e.getMessage());
            return false;
        }
    }

    private void handleContactReceived(Message message) {
        Contact contact = message.getContact();
        Long telegramUserId = message.getFrom().getId();
        Long chatId = message.getChatId();

        log.info("📱 Contact received from user {}", telegramUserId);

        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            String phoneNumber = contact.getPhoneNumber();
            if (!phoneNumber.startsWith("+")) {
                phoneNumber = "+" + phoneNumber;
            }

            user.setPhone(phoneNumber);
            user.setIsPhoneVerified(true);
            userService.save(user);

            log.info("✅ Phone verified for user {}: {}", telegramUserId, phoneNumber);

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId.toString());
            sendMessage.setText(messageFormatter.formatVerificationSuccess(user));
            sendMessage.setParseMode("HTML");

            org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove removeKeyboard
                    = new org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove();
            removeKeyboard.setRemoveKeyboard(true);
            sendMessage.setReplyMarkup(removeKeyboard);

            bot.execute(sendMessage);
        } catch (Exception e) {
            log.error("❌ Error processing contact: {}", e.getMessage());
            sendMessage(chatId, messageFormatter.formatPhoneVerificationError());
        }
    }
    private void handleDealsHistoryCommand(Long chatId, Long telegramUserId) {
        handleDealsHistoryPage(chatId, telegramUserId, 0);  // Показываем первую страницу
    }

    private void handleDealsHistoryPage(Long chatId, Long telegramUserId, int page) {
        log.info("Processing history page {} for user {}", page, telegramUserId);

        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            Pageable pageable = PageRequest.of(page, HISTORY_PAGE_SIZE);
            Page<Deal> dealsPage = dealService.getFinishedUserDealsWithUsers(user.getId(), pageable);
            List<Deal> deals = dealsPage.getContent();

            if (deals.isEmpty() && page == 0) {
                String message = messageFormatter.formatNoHistory();

                InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                        .addButton("💰 Создать заявку", CALLBACK_MENU+"need")
                        .addButton("🔍 Поиск", CALLBACK_MENU+"search")
                        .newRow()
                        .addButton("🏠 Меню", "show_menu")
                        .build();

                sendMessageWithKeyboard(chatId, message, keyboard);
                return;
            }

            String message = messageFormatter.formatDealsHistoryPage(
                    dealsPage,
                    deals,
                    user,
                    page,
                    ratingService
            );

            TelegramKeyboardBuilder builder = TelegramKeyboardBuilder.create();

            for (int i = 0; i < deals.size(); i++) {
                Deal deal = deals.get(i);
                builder.addButton("📋 Обмен #" + deal.getId(), CALLBACK_VIEW_DEAL + deal.getId());

                if ((i + 1) % 2 == 0 || i == deals.size() - 1) {
                    builder.newRow();
                }
            }

            if (dealsPage.getTotalPages() > 1) {
                if (page > 0) {
                    builder.addButton("⬅️ Назад", CALLBACK_HISTORY_PAGE + (page - 1));
                }

                builder.addButton("📖 " + (page + 1) + "/" + dealsPage.getTotalPages(), "current_page");

                if (page < dealsPage.getTotalPages() - 1) {
                    builder.addButton("Вперёд ➡️", CALLBACK_HISTORY_PAGE + (page + 1));
                }

                builder.newRow();
            }

            InlineKeyboardMarkup keyboard = builder
                    .addButton("🏠 Меню", "show_menu")
                    .build();

            sendMessageWithKeyboard(chatId, message, keyboard);

        } catch (Exception e) {
            log.error("Error in history page {}: {}", page, e.getMessage(), e);
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }

    private void handleViewDeal(Long chatId, Long telegramUserId, Long dealId) {
        log.info("Viewing deal {} by user {}", dealId, telegramUserId);

        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            Deal deal = dealService.findByIdWithUsers(dealId);

            // Проверка доступа
            if (!deal.getRequester().getId().equals(user.getId()) &&
                    !deal.getProvider().getId().equals(user.getId())) {
                sendMessage(chatId, "❌ У вас нет доступа к этому обмену");
                return;
            }

            // Определяем роль
            boolean isRequester = deal.getRequester().getId().equals(user.getId());
            User counterparty = isRequester ? deal.getProvider() : deal.getRequester();

            // Рассчитываем конвертацию
            BigDecimal rate = deal.getCurrency() == ExchangeRequest.Currency.PLN
                    ? exchangeRateService.getCurrentPLNtoKZTRate()
                    : exchangeRateService.getCurrentKZTtoPLNRate();

            ExchangeRequest.Currency oppositeCurrency = deal.getCurrency() == ExchangeRequest.Currency.PLN
                    ? ExchangeRequest.Currency.KZT
                    : ExchangeRequest.Currency.PLN;

            BigDecimal received = deal.getAmount().multiply(rate);

            // Проверяем оценку
            boolean isRated = ratingService.existsByDealIdAndRaterId(dealId, user.getId());

            String message = messageFormatter.formatDealDetails(
                    deal,
                    user,
                    counterparty,
                    received,
                    oppositeCurrency,
                    isRated);

            TelegramKeyboardBuilder builder = TelegramKeyboardBuilder.create();

            if (!isRated) {
                for (int i = 1; i <= 5; i++) {
                    builder.addButton(i + " ⭐", CALLBACK_RATE_DEAL + dealId + ":" + i);
                }
                builder.newRow();
            }

            InlineKeyboardMarkup keyboard = builder
                    .addButton("◀️ Назад к истории", CALLBACK_MENU+"history")
                    .addButton("🏠 Меню", "show_menu")
                    .build();


            sendMessageWithKeyboard(chatId, message, keyboard);

        } catch (Exception e) {
            log.error("Error viewing deal: {}", e.getMessage());
            sendMessage(chatId, "❌ Обмен не найден");
        }
    }
    /**
     * Обработка отклика на заявку из поиска
     */
    @Transactional
    private void handleRespondToRequest(Long chatId, Long telegramUserId, Long requestId) {
        log.info("User {} responding to request {}", telegramUserId, requestId);

        try {
            User responder = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            log.info("Responder found: {}", responder.getTelegramUsername());

            // ⭐ ИСПОЛЬЗУЕМ findByIdWithUser С JOIN FETCH
            ExchangeRequest targetRequest = exchangeService.findByIdWithUser(requestId);
            User author = targetRequest.getUser();

            log.info("Target request found: ID={}, amount={}, currency={}, status={}",
                    requestId, targetRequest.getAmountNeed(), targetRequest.getCurrencyNeed(), targetRequest.getStatus());

            // ⭐ ПРОВЕРКА: АКТИВНА ЛИ ЗАЯВКА?
            if (!targetRequest.isActive()) {
                sendMessage(chatId, "❌ Эта заявка больше не активна");
                log.warn("Request {} is not active", requestId);
                return;
            }

            // ⭐ ПРОВЕРКА: НЕ СВОЯ ЛИ ЗАЯВКА?
            if (author.getId().equals(responder.getId())) {
                sendMessage(chatId, "❌ Вы не можете откликнуться на свою заявку");
                log.warn("User {} tried to respond to own request {}", telegramUserId, requestId);
                return;
            }

            // ⭐ ПРОВЕРЯЕМ: ЕСТЬ ЛИ У ОТКЛИКАЮЩЕГОСЯ АКТИВНАЯ ЗАЯВКА С ПРОТИВОПОЛОЖНОЙ ВАЛЮТОЙ
            ExchangeRequest.Currency oppositeCurrency = targetRequest.getCurrencyNeed().equals(ExchangeRequest.Currency.PLN)
                    ? ExchangeRequest.Currency.KZT
                    : ExchangeRequest.Currency.PLN;

            List<ExchangeRequest> responderRequests = exchangeService.getActiveByUserId(responder.getId())
                    .stream()
                    .filter(req -> req.getCurrencyNeed().equals(oppositeCurrency))
                    .toList();

            if (!responderRequests.isEmpty()) {
                // ============================================
                // ⭐ СЦЕНАРИЙ 1: ЕСТЬ АКТИВНАЯ ЗАЯВКА - АВТОМАТИЧЕСКИЙ РАСЧЁТ
                // ============================================
                ExchangeRequest responderRequest = responderRequests.get(0);

                // ⭐ ПРАВИЛЬНАЯ ЛОГИКА КОНВЕРТАЦИИ:
                ExchangeRequest.Currency myCurrency = responderRequest.getCurrencyNeed();
                ExchangeRequest.Currency hisCurrency = targetRequest.getCurrencyNeed();

                // Определяем правильный курс
                BigDecimal rate;
                if (myCurrency == ExchangeRequest.Currency.KZT && hisCurrency == ExchangeRequest.Currency.PLN) {
                    rate = exchangeRateService.getCurrentKZTtoPLNRate();
                } else if (myCurrency == ExchangeRequest.Currency.PLN && hisCurrency == ExchangeRequest.Currency.KZT) {
                    rate = exchangeRateService.getCurrentPLNtoKZTRate();
                } else {
                    log.error("Same currency in automatic calculation!");
                    sendMessage(chatId, "❌ Ошибка: валюты совпадают");
                    return;
                }

                // Конвертируем МОЮ сумму в ЕГО валюту
                BigDecimal calculatedAmount = responderRequest.getAmountNeed().multiply(rate);
                BigDecimal maxAmount = targetRequest.getAmountNeed();
                BigDecimal proposedAmount = calculatedAmount.min(maxAmount);

                log.info("💡 Calculation: {} {} * {} = {} {} (max: {} {})",
                        responderRequest.getAmountNeed(), myCurrency, rate,
                        calculatedAmount, hisCurrency, maxAmount, hisCurrency);

                conversationStateService.setState(telegramUserId, ConversationState.AWAITING_DEAL_CONFIRMATION);
                conversationStateService.setUserData(telegramUserId, "deal_request_id", requestId.toString());
                conversationStateService.setUserData(telegramUserId, "deal_amount", proposedAmount.toString());
                conversationStateService.setUserData(telegramUserId, "responder_request_id", responderRequest.getId().toString());

                String message = messageFormatter.formatExchangeOfferAutoCalculated(
                        responderRequest,
                        targetRequest,
                        author,
                        calculatedAmount,
                        maxAmount,
                        proposedAmount);

                InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                        .addButton("✅ Подтвердить", CALLBACK_CONFIRM_DEAL_AUTO)
                        .addButton("❌ Отмена", CALLBACK_MENU + "search")
                        .build();


                sendMessageWithKeyboard(chatId, message, keyboard);

                log.info("✅ [SCENARIO 1] Automatic calculation: user {} → {} {}",
                        telegramUserId, proposedAmount, targetRequest.getCurrencyNeed());

            } else {

                // ⭐ СЦЕНАРИЙ 2: НЕТ АКТИВНОЙ ЗАЯВКИ - СРАЗУ ВВОД СУММЫ
                conversationStateService.setState(telegramUserId, ConversationState.AWAITING_DEAL_AMOUNT);
                conversationStateService.setUserData(telegramUserId, "deal_request_id", requestId.toString());
                conversationStateService.setUserData(telegramUserId, "deal_author_telegram_id", author.getTelegramUserId().toString());

                String message = messageFormatter.formatExchangeOfferManual(targetRequest, author);

                sendMessage(chatId, message);

                log.info("✅ [SCENARIO 2] Amount input requested: user {} → request {}",
                        telegramUserId, requestId);
            }

        } catch (RuntimeException e) {
            log.error("Error responding to request {}: {}", requestId, e.getMessage(), e);
            sendMessage(chatId, "❌ Ошибка: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error responding to request {}: {}", requestId, e.getMessage(), e);
            sendMessage(chatId, "❌ Ошибка: заявка не найдена");
        }
    }

    private InlineKeyboardMarkup createStatusButtons(boolean noRequests, List<ExchangeRequest> requests) {

        TelegramKeyboardBuilder builder = TelegramKeyboardBuilder.create();

        if (noRequests) {
            builder.addButton("💰 Создать заявку", CALLBACK_MENU+"need");
        } else {
            builder.addButton("⚙️ Управление заявками", CALLBACK_SHOW_REQUEST_MANAGEMENT)
                    .newRow()
                    .addButton("➕ Создать ещё", CALLBACK_MENU+"need")
                    .addButton("🔍 Поиск", CALLBACK_MENU+"search");
        }

        return builder.newRow()
                .addButton("🏠 Меню", "show_menu")
                .build();
    }

    private InlineKeyboardMarkup createMenuButton() {
        return TelegramKeyboardBuilder.create()
                .addButton("🏠 Меню", CALLBACK_SHOW_MENU)
                .build();
    }

    private ReplyKeyboardMarkup createMandatoryShareContactKeyboard() {
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);
        keyboard.setOneTimeKeyboard(true);

        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();

        KeyboardButton button = new KeyboardButton();
        button.setText("📱 Поделиться номером телефона");
        button.setRequestContact(true);
        row.add(button);

        rows.add(row);
        keyboard.setKeyboard(rows);

        return keyboard;
    }

    /**
     * Показать список заявок для отмены - ПО ИНДЕКСУ, НЕ ПО ID!
     */
    private void showRequestListForCancel(Long chatId, Long telegramUserId) {
        log.info("Showing request list for cancel for user {}", telegramUserId);

        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            List<ExchangeRequest> activeRequests = exchangeService.getActiveByUserId(user.getId());

            if (activeRequests.isEmpty()) {
                sendMessage(chatId, "У вас нет активных заявок");
                return;
            }

            String message = messageFormatter.formatSelectRequestToCancel(activeRequests);

            TelegramKeyboardBuilder builder = TelegramKeyboardBuilder.create();

            List<Long> requestIds = new ArrayList<>();

            for (int i = 0; i < activeRequests.size(); i++) {
                ExchangeRequest req = activeRequests.get(i);
                requestIds.add(req.getId());

                builder.addButton("❌ Заявка #" + (i + 1), CALLBACK_SELECT_CANCEL + i)
                        .newRow();
            }

            conversationStateService.setUserData(telegramUserId, "cancel_request_list",
                    requestIds.stream().map(String::valueOf).collect(Collectors.joining(",")));

            InlineKeyboardMarkup keyboard = builder
                    .addButton("◀️ Назад", CALLBACK_SHOW_REQUEST_MANAGEMENT)
                    .build();

            sendMessageWithKeyboard(chatId, message, keyboard);

        } catch (Exception e) {
            log.error("Error showing request list: {}", e.getMessage());
            sendMessage(chatId, messageFormatter.formatTechnicalError());
        }
    }
    /**
     * Отменить заявку по ИНДЕКСУ в списке - СРАЗУ БЕЗ ПОДТВЕРЖДЕНИЯ
     */
    private void handleCancelRequestByIndex(Long chatId, Long telegramUserId, int index) {
        try {
            User user = userService.findByTelegramUserId(telegramUserId)
                    .orElseThrow(() -> new UserNotFoundException(USER_NOT_FOUND));

            String requestListStr = conversationStateService.getUserData(telegramUserId, "cancel_request_list");

            if (requestListStr == null) {
                sendMessage(chatId, "❌ Ошибка: список заявок не найден");
                return;
            }

            List<Long> requestIds = Arrays.stream(requestListStr.split(","))
                    .map(Long::parseLong)
                    .toList();

            if (index < 0 || index >= requestIds.size()) {
                sendMessage(chatId, "❌ Ошибка: неверный номер заявки");
                return;
            }

            Long requestId = requestIds.get(index);

            ExchangeRequest request = exchangeService.findById(requestId);
            String amount = messageFormatter.formatAmount(request.getAmountNeed());
            String currency = request.getCurrencyNeed().toString();
            String method = messageFormatter.getTransferMethodName(request.getTransferMethod().name());
            String notes = request.getNotes();

            exchangeService.cancelExchangeRequest(requestId, user.getId());

            conversationStateService.clearState(telegramUserId);

            String message = messageFormatter.formatRequestCancelled(index, amount, currency, method, notes);

            InlineKeyboardMarkup keyboard = TelegramKeyboardBuilder.create()
                    .addButton("💰 Создать новую заявку", CALLBACK_MENU+"need")
                    .newRow()
                    .addButton("📊 Мои заявки", CALLBACK_MENU+"my_requests")
                    .addButton("🏠 Меню", "show_menu")
                    .build();

            sendMessageWithKeyboard(chatId, message, keyboard);

            log.info("✅ Request #{} (ID={}) cancelled by user {}", index + 1, requestId, user.getTelegramUsername());

        } catch (Exception e) {
            log.error("Error canceling by index: {}", e.getMessage());
            sendMessage(chatId, "❌ Ошибка: " + e.getMessage());
        }
    }

    private void sendMessage(Long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("HTML");
            bot.execute(message);
            log.debug("Message sent to {}", chatId);
        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage());
        }
    }

    private void sendMessageWithKeyboard(Long chatId, String text, InlineKeyboardMarkup keyboard) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("HTML");
            message.setReplyMarkup(keyboard);
            bot.execute(message);
            log.debug("Keyboard message sent to {}", chatId);
        } catch (Exception e) {
            log.error("Error sending keyboard message: {}", e.getMessage());
        }
    }

}