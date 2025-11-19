package pl.aybolali.plnkztexchangebot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Exchange Rate Service Tests (No Redis)")
@ActiveProfiles("test")
class ExchangeRateServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private ExchangeRateService exchangeRateService;

    @BeforeEach
    void setUp() {
        // Configure service properties via reflection
        ReflectionTestUtils.setField(exchangeRateService, "apiEnabled", true);
        ReflectionTestUtils.setField(exchangeRateService, "primaryUrl", "http://api.test.com/v1/currencies");
        ReflectionTestUtils.setField(exchangeRateService, "fallbackUrl", "http://fallback.test.com/v1/backup");
        ReflectionTestUtils.setField(exchangeRateService, "fallbackRate", 147.5);
    }

    @Test
    @DisplayName("Should return rate from primary API when available")
    void getCurrentPLNtoKZTRate_ShouldFetchFromPrimaryAPI() {
        Map<String, Object> apiResponse = Map.of(
                "pln", Map.of("kzt", 147.5)
        );
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(apiResponse);

        BigDecimal result = exchangeRateService.getCurrentPLNtoKZTRate();

        assertEquals(0, result.compareTo(new BigDecimal("147.5")));
        verify(restTemplate, atLeastOnce()).getForObject(anyString(), eq(Map.class));
    }

    @Test
    @DisplayName("Should return fallback rate when API fails")
    void getCurrentPLNtoKZTRate_ShouldReturnFallbackWhenAPIFails() {
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("API unavailable"));

        BigDecimal result = exchangeRateService.getCurrentPLNtoKZTRate();

        assertEquals(new BigDecimal("147.5"), result);
        verify(restTemplate, atLeastOnce()).getForObject(anyString(), eq(Map.class));
    }

    @Test
    @DisplayName("Should return fallback rate when API disabled")
    void getCurrentPLNtoKZTRate_ShouldReturnFallbackWhenAPIDisabled() {
        ReflectionTestUtils.setField(exchangeRateService, "apiEnabled", false);

        BigDecimal result = exchangeRateService.getCurrentPLNtoKZTRate();

        assertEquals(new BigDecimal("147.5"), result);
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Should calculate inverse rate for KZT to PLN")
    void getCurrentKZTtoPLNRate_ShouldCalculateInverseRate() {
        ReflectionTestUtils.setField(exchangeRateService, "apiEnabled", false);

        BigDecimal plnToKzt = exchangeRateService.getCurrentPLNtoKZTRate();
        BigDecimal kztToPln = exchangeRateService.getCurrentKZTtoPLNRate();

        BigDecimal product = plnToKzt.multiply(kztToPln);
        assertTrue(product.compareTo(new BigDecimal("0.99")) > 0);
        assertTrue(product.compareTo(new BigDecimal("1.01")) < 0);
    }

    @Test
    @DisplayName("Should handle invalid API response gracefully")
    void getCurrentPLNtoKZTRate_ShouldHandleInvalidResponse() {
        Map<String, Object> invalidResponse = Map.of(
                "error", "invalid"
        );
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(invalidResponse);

        BigDecimal result = exchangeRateService.getCurrentPLNtoKZTRate();

        assertEquals(new BigDecimal("147.5"), result);
    }

    @Test
    @DisplayName("Should handle zero rate from API")
    void getCurrentPLNtoKZTRate_ShouldHandleZeroRate() {
        Map<String, Object> zeroResponse = Map.of(
                "pln", Map.of("kzt", 0)
        );
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(zeroResponse);

        BigDecimal result = exchangeRateService.getCurrentPLNtoKZTRate();

        assertEquals(new BigDecimal("147.5"), result);
    }

    @Test
    @DisplayName("Should handle negative rate from API")
    void getCurrentPLNtoKZTRate_ShouldHandleNegativeRate() {
        Map<String, Object> negativeResponse = Map.of(
                "pln", Map.of("kzt", -100)
        );
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenReturn(negativeResponse);

        BigDecimal result = exchangeRateService.getCurrentPLNtoKZTRate();

        assertEquals(new BigDecimal("147.5"), result);
    }
}