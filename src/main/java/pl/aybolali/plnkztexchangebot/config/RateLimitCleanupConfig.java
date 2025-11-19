package pl.aybolali.plnkztexchangebot.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import pl.aybolali.plnkztexchangebot.service.SimpleRateLimitService;

/**
 * Конфигурация для очистки старых счетчиков rate limiting
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class RateLimitCleanupConfig {

    private final SimpleRateLimitService rateLimitService;

    /**
     * Очистка каждые 5 минут
     */
    @Scheduled(fixedRate = 300000) // 5 минут
    public void cleanupRateLimitCounters() {
        rateLimitService.cleanupOldCounters();
    }
}