package pl.aybolali.plnkztexchangebot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import pl.aybolali.plnkztexchangebot.service.SimpleRateLimitService;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/rate-limit")
@RequiredArgsConstructor
@Slf4j
public class RateLimitAdminController {

    private final SimpleRateLimitService rateLimitService;

    /**
     * Получить текущий счетчик пользователя
     * GET /api/v1/admin/rate-limit/check?userId=123&action=telegram_command
     */
    @GetMapping("/check")
    public Map<String, Object> checkLimit(
            @RequestParam Long userId,
            @RequestParam String action) {

        int currentCount = rateLimitService.getCurrentCount(userId, action);

        return Map.of(
                "userId", userId,
                "action", action,
                "currentCount", currentCount,
                "timestamp", System.currentTimeMillis()
        );
    }

    /**
     * Сбросить лимит для пользователя
     * POST /api/v1/admin/rate-limit/reset?userId=123&action=telegram_command
     */
    @PostMapping("/reset")
    public String resetLimit(
            @RequestParam Long userId,
            @RequestParam String action) {

        rateLimitService.resetLimit(userId, action);
        return "Rate limit reset for user " + userId + " action " + action;
    }

    /**
     * Очистить все старые счетчики
     * POST /api/v1/admin/rate-limit/cleanup
     */
    @PostMapping("/cleanup")
    public String cleanup() {
        rateLimitService.cleanupOldCounters();
        return "Old counters cleaned up";
    }
}