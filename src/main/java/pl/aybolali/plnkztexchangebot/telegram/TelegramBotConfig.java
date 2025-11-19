package pl.aybolali.plnkztexchangebot.telegram;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * ⚙️ КОНФИГУРАЦИЯ TELEGRAM БОТА - Long Polling Mode
 *
 * ⭐ НЕ запускается в test profile!
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
@Profile("!test")  // ⭐ ВАЖНО: НЕ запускать в тестах
public class TelegramBotConfig {

    private final PLNKZTExchangeBot plnkztExchangeBot;
    private TelegramBotsApi telegramBotsApi;

    @PostConstruct
    public void initializeTelegramBot() {
        try {
            log.info("========================================");
            log.info("🤖 Initializing PLN-KZT Exchange Bot...");
            log.info("========================================");

            telegramBotsApi = new TelegramBotsApi(DefaultBotSession.class);
            telegramBotsApi.registerBot(plnkztExchangeBot);

            log.info("✅ Telegram Bot registered successfully!");
            log.info("📱 Bot Username: @{}", plnkztExchangeBot.getBotUsername());
            log.info("🔄 Mode: Long Polling (no HTTPS required)");
            log.info("🌐 Ready to receive messages from users!");
            log.info("========================================");

        } catch (TelegramApiException e) {
            log.error("========================================");
            log.error("❌ FAILED TO REGISTER TELEGRAM BOT!");
            log.error("========================================");
            log.error("Error: {}", e.getMessage());

            if (e.getMessage() != null) {
                if (e.getMessage().contains("token") || e.getMessage().contains("401")) {
                    log.error("💡 Проверьте TELEGRAM_BOT_TOKEN");
                    log.error("   Получить токен: https://t.me/BotFather");
                } else if (e.getMessage().contains("username")) {
                    log.error("💡 Проверьте TELEGRAM_BOT_USERNAME");
                } else if (e.getMessage().contains("timeout") || e.getMessage().contains("connection")) {
                    log.error("💡 Проверьте подключение к интернету");
                } else {
                    log.error("💡 Проверьте корректность настроек в application.yml");
                }
            }

            log.error("========================================");
            throw new RuntimeException("Failed to initialize Telegram bot", e);
        }
    }

    @PreDestroy
    public void shutdownTelegramBot() {
        try {
            if (telegramBotsApi != null) {
                log.info("🛑 Shutting down Telegram Bot...");
                log.info("✅ Telegram Bot shutdown completed");
            }
        } catch (Exception e) {
            log.warn("⚠️ Error during Telegram bot shutdown: {}", e.getMessage());
        }
    }
}