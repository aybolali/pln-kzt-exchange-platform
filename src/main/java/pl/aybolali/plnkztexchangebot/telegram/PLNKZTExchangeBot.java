package pl.aybolali.plnkztexchangebot.telegram;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 🤖 ГЛАВНЫЙ КЛАСС TELEGRAM БОТА - Long Polling Mode
 *
 * Long Polling = бот сам постоянно запрашивает обновления у Telegram
 * (каждые несколько секунд спрашивает: "Есть новые сообщения?")

 *
 * Отвечает за:
 * - Подключение к Telegram Bot API
 * - Получение сообщений от пользователей (Long Polling)
 * - Установку команд бота в меню Telegram
 * - Передачу сообщений в TelegramBotService для обработки
 */
@Component
@Slf4j
public class PLNKZTExchangeBot extends TelegramLongPollingBot {

    private final String botToken;
    private final String botUsername;
    private final TelegramBotService telegramBotService;

    /**
     * Конструктор с injection токена и username из application.yml
     */
    public PLNKZTExchangeBot(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.username}") String botUsername,
            TelegramBotService telegramBotService) {

        super(botToken); // Передаем токен в родительский класс
        this.botToken = botToken;
        this.botUsername = botUsername;
        this.telegramBotService = telegramBotService;

        log.info("PLN-KZT Exchange Bot component initialized");
        log.info("Username: @{}", botUsername);
    }

    /**
     * 📨 Получение обновлений от Telegram (Long Polling)
     *
     * Этот метод вызывается каждый раз когда:
     * - Пользователь отправляет сообщение
     * - Пользователь отправляет команду
     * - Пользователь нажимает кнопку
     * - Пользователь делится контактом (телефоном)
     */
    @Override
    public void onUpdateReceived(Update update) {
        try {
            // Логируем только важные обновления
            if (update.hasMessage() && update.getMessage().hasText()) {
                log.debug("📨 Message from @{}: {}",
                        extractUsername(update),
                        update.getMessage().getText());
            } else if (update.hasCallbackQuery()) {
                log.debug("🖱️ Callback from @{}: {}",
                        extractUsername(update),
                        update.getCallbackQuery().getData());
            } else if (update.hasMessage() && update.getMessage().hasContact()) {
                log.debug("📱 Contact shared from @{}", extractUsername(update));
            }

            // ⭐ ОБРАБОТКА CALLBACK QUERY (НАЖАТИЯ КНОПОК)
            if (update.hasCallbackQuery()) {
                telegramBotService.handleCallbackQuery(update.getCallbackQuery());
                return;
            }

            // ОБРАБОТКА ОБЫЧНЫХ СООБЩЕНИЙ И КОМАНД
            telegramBotService.processUpdate(update);

        } catch (Exception e) {
            log.error("❌ Error processing update {}: {}",
                    update.getUpdateId(), e.getMessage(), e);
            // Не пробрасываем исключение выше чтобы не сломать бота
        }
    }

    /**
     * 🏷️ Возвращает username бота (обязательный метод)
     */
    @Override
    public String getBotUsername() {
        return botUsername;
    }

    /**
     * 🚀 Вызывается когда бот успешно зарегистрирован в Telegram
     * Устанавливаем команды бота в меню
     */
    @Override
    public void onRegister() {
        super.onRegister();
        log.info("✅ Bot @{} registered with Telegram", botUsername);
    }
    /**
     * 🔍 Извлекает username пользователя из Update для логирования
     */
    private String extractUsername(Update update) {
        try {
            if (update.hasMessage() && update.getMessage().getFrom() != null) {
                String username = update.getMessage().getFrom().getUserName();
                return username != null ? username : "user_" + update.getMessage().getFrom().getId();
            } else if (update.hasCallbackQuery() && update.getCallbackQuery().getFrom() != null) {
                String username = update.getCallbackQuery().getFrom().getUserName();
                return username != null ? username : "user_" + update.getCallbackQuery().getFrom().getId();
            }
        } catch (Exception e) {
            // Игнорируем ошибки извлечения username - не критично для работы
        }
        return "unknown";
    }
}