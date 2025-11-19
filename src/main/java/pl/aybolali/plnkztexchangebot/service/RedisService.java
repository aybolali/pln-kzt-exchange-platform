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
public class RedisService {

    private final StringRedisTemplate redisTemplate;


     * Сохранить строку с TTL

    public void setValue(String key, String value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            log.debug("Saved to Redis: {} = {} (TTL: {})", key, value, ttl);
        } catch (Exception e) {
            log.error("Redis save error: {}", e.getMessage());
        }
    }

    /**
     * Получить строку

    public String getValue(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            log.debug("Retrieved from Redis: {} = {}", key, value);
            return value;
        } catch (Exception e) {
            log.error("Redis get error: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Увеличить счетчик атомарно

    public Long increment(String key) {
        try {
            Long newValue = redisTemplate.opsForValue().increment(key);
            log.debug("Incremented Redis key: {} = {}", key, newValue);
            return newValue;
        } catch (Exception e) {
            log.error("Redis increment error: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * Проверить существование ключа

    public boolean hasKey(String key) {
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return exists != null && exists;
        } catch (Exception e) {
            log.error("Redis hasKey error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Удалить ключ

    public void deleteKey(String key) {
        try {
            redisTemplate.delete(key);
            log.debug("Deleted Redis key: {}", key);
        } catch (Exception e) {
            log.error("Redis delete error: {}", e.getMessage());
        }
    }
}
 */
