package pl.aybolali.plnkztexchangebot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class SimpleRateLimitService {

    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();
    private final Map<String, Long> resetTimes = new ConcurrentHashMap<>();


    public boolean checkLimit(Long userId, String action) {
        String key = userId + ":" + action;
        long now = System.currentTimeMillis();

        // Сброс счетчика каждую минуту
        Long resetTime = resetTimes.get(key);
        if (resetTime == null || now > resetTime) {
            counters.put(key, new AtomicInteger(0));
            resetTimes.put(key, now + 60000); // +1 минута
        }

        int count = counters.get(key).incrementAndGet();
        int limit = getLimitForAction(action);

        if (count > limit) {
            log.warn("⚠️ Rate limit exceeded for user {} action {}: {}/{}",
                    userId, action, count, limit);
            return false;
        }

        if (count > limit * 0.8) {
            log.info("⚠️ User {} approaching limit for {}: {}/{}",
                    userId, action, count, limit);
        }

        return true;
    }


    private int getLimitForAction(String action) {
        return switch (action) {
            // Больше = явный спам
            case "telegram_command" -> 20;

            case "api_call" -> 6;

            default -> 25;
        };
    }


    public int getCurrentCount(Long userId, String action) {
        String key = userId + ":" + action;
        AtomicInteger counter = counters.get(key);
        return counter != null ? counter.get() : 0;
    }

    public void resetLimit(Long userId, String action) {
        String key = userId + ":" + action;
        counters.remove(key);
        resetTimes.remove(key);
        log.info("🔧 Rate limit reset for user {} action {}", userId, action);
    }

    public void cleanupOldCounters() {
        long now = System.currentTimeMillis();
        int removed = 0;

        for (Map.Entry<String, Long> entry : resetTimes.entrySet()) {
            if (now > entry.getValue() + 60000) { // Прошло больше 2 минут
                String key = entry.getKey();
                counters.remove(key);
                resetTimes.remove(key);
                removed++;
            }
        }

        if (removed > 0) {
            log.debug("🧹 Cleaned up {} old rate limit counters", removed);
        }
    }
}