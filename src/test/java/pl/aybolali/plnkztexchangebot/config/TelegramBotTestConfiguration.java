package pl.aybolali.plnkztexchangebot.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import pl.aybolali.plnkztexchangebot.telegram.PLNKZTExchangeBot;
import pl.aybolali.plnkztexchangebot.telegram.TelegramBotService;

import static org.mockito.Mockito.mock;

@TestConfiguration
@Profile("test")
public class TelegramBotTestConfiguration {
    @Bean
    @Primary
    public PLNKZTExchangeBot mockTelegramBot(TelegramBotService botService) {
        // Возвращаем mock вместо реального бота - Это предотвращает попытку подключения к Telegram API
        return mock(PLNKZTExchangeBot.class);
    }
}