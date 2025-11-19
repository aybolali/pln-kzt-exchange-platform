package pl.aybolali.plnkztexchangebot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.aybolali.plnkztexchangebot.telegram.PLNKZTExchangeBot;

/**
 * 🧪 Основной тест загрузки контекста приложения
 */
@SpringBootTest
@ActiveProfiles("test")
class PlnKztExchangeBotApplicationTests {

    /**
     * Mock бота чтобы не запускался реальный
     */
    @MockitoBean
    private PLNKZTExchangeBot bot;

    @Test
    void contextLoads() {
        // Проверяем что Spring контекст загружается без ошибок
    }
}