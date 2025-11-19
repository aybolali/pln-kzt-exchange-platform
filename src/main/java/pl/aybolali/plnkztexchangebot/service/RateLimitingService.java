/*
package pl.aybolali.plnkztexchangebot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class RateLimitingService {

    private final StringRedisTemplate redisTemplate;

    /**
     * Проверка лимита с подробной информацией

    public RateLimitResult checkLimit(Long userId, String action) {
        String currentThread = Thread.currentThread().getName();
        log.debug("Проверка лимита для пользователя {} действие {} в потоке {}",
                userId, action, currentThread);

        try {
            long currentMinute = System.currentTimeMillis() / 60000;
            String key = String.format("rate_limit:%d:%s:%d", userId, action, currentMinute);

            // Атомарное увеличение счетчика
            Long count = redisTemplate.opsForValue().increment(key);

            // Установка TTL только для первого запроса - УТОЧНИТЬ
            if (count == 1) {
                redisTemplate.expire(key, Duration.ofMinutes(1));
            }

            int limit = getLimitForAction(action);
            boolean allowed = count <= limit;

            RateLimitResult result = new RateLimitResult(
                    allowed, count.intValue(), limit,
                    allowed ? 0 : 60, // секунд до сброса
                    key
            );

            if (!allowed) {
                log.warn("Лимит превышен: пользователь {} действие {} - {}/{}",
                        userId, action, count, limit);
            }

            return result;

        } catch (Exception e) {
            log.error("Ошибка Redis в rate limiting: {}", e.getMessage());
            // Возвращаем "разрешено" если Redis недоступен
            return new RateLimitResult(true, 0, 999, 0, "fallback");
        }
    }

    private int getLimitForAction(String action) {
        return switch (action) {
            case "telegram_command" -> 20;
            case "api_request" -> 60;
            case "deal_creation" -> 5;
            default -> 10;
        };
    }


    /**
     * Результат проверки лимита

    public record RateLimitResult(
            boolean allowed,      // Разрешен ли запрос
            int currentCount,     // Текущий счетчик
            int maxLimit,         // Максимальный лимит
            int resetInSeconds,   // Через сколько секунд сброс
            String redisKey       // Ключ в Redis (для отладки)
    ) {}
}

 */
