package pl.aybolali.plnkztexchangebot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Simple Rate Limit Service Tests")
@ActiveProfiles("test")
class SimpleRateLimitServiceTest {

    private SimpleRateLimitService service;

    @BeforeEach
    void setUp() {
        service = new SimpleRateLimitService();
    }

    @Test
    @DisplayName("Should allow requests within limit (20 commands/min)")
    void testWithinLimit() {
        for (int i = 0; i < 20; i++) {
            boolean allowed = service.checkLimit(123L, "telegram_command");
            assertThat(allowed)
                    .as("Request %d should be allowed", i + 1)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Should block requests exceeding limit (21th request)")
    void testExceedLimit() {
        for (int i = 0; i < 20; i++) {
            service.checkLimit(123L, "telegram_command");
        }

        boolean allowed = service.checkLimit(123L, "telegram_command");
        assertThat(allowed)
                .as("21th request should be blocked")
                .isFalse();
    }

    @Test
    @DisplayName("Should reset counter after time window")
    void testCounterReset() throws InterruptedException {
        for (int i = 0; i < 21; i++) {
            service.checkLimit(123L, "telegram_command");
        }

        assertThat(service.getCurrentCount(123L, "telegram_command"))
                .isEqualTo(21);

        Thread.sleep(61000);

        boolean allowed = service.checkLimit(123L, "telegram_command");
        assertThat(allowed)
                .as("Request should be allowed after reset")
                .isTrue();
    }

    @Test
    @DisplayName("Should track different users separately")
    void testSeparateUsers() {
        for (int i = 0; i < 21; i++) {
            service.checkLimit(111L, "telegram_command");
        }

        boolean allowed = service.checkLimit(222L, "telegram_command");
        assertThat(allowed)
                .as("Different user should be allowed")
                .isTrue();
    }

    @Test
    @DisplayName("Should track different actions separately")
    void testSeparateActions() {
        for (int i = 0; i < 21; i++) {
            service.checkLimit(123L, "telegram_command");
        }

        boolean allowed = service.checkLimit(123L, "api_call");
        assertThat(allowed)
                .as("Different action should be allowed")
                .isTrue();
    }

    @Test
    @DisplayName("Should respect different limits for different actions")
    void testDifferentLimits() {
        for (int i = 0; i < 20; i++) {
            boolean allowed = service.checkLimit(123L, "telegram_command");
            assertThat(allowed).isTrue();
        }
        assertThat(service.checkLimit(123L, "telegram_command")).isFalse();

        for (int i = 0; i < 6; i++) {
            boolean allowed = service.checkLimit(456L, "api_call");
            assertThat(allowed).isTrue();
        }
        assertThat(service.checkLimit(456L, "api_call")).isFalse();
    }

    @Test
    @DisplayName("Should get current count correctly")
    void testGetCurrentCount() {
        for (int i = 0; i < 10; i++) {
            service.checkLimit(123L, "telegram_command");
        }

        int count = service.getCurrentCount(123L, "telegram_command");
        assertThat(count).isEqualTo(10);
    }

    @Test
    @DisplayName("Should reset limit manually")
    void testManualReset() {
        for (int i = 0; i < 21; i++) {
            service.checkLimit(123L, "telegram_command");
        }

        service.resetLimit(123L, "telegram_command");

        boolean allowed = service.checkLimit(123L, "telegram_command");
        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("Should warn when approaching limit")
    void testApproachingLimit() {
        for (int i = 0; i < 16; i++) {
            service.checkLimit(123L, "telegram_command");
        }

        // Следующие запросы должны логировать предупреждение
        // (проверяется визуально в логах)
        for (int i = 0; i < 6; i++) {
            service.checkLimit(123L, "telegram_command");
        }
    }
}