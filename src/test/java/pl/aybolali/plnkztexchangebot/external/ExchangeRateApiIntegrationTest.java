package pl.aybolali.plnkztexchangebot.external;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pl.aybolali.plnkztexchangebot.service.ExchangeRateService;
import pl.aybolali.plnkztexchangebot.telegram.PLNKZTExchangeBot;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@DisplayName("Exchange Rate API Integration Tests")
@ActiveProfiles("test")
class ExchangeRateApiIntegrationTest {

    @Autowired
    private ExchangeRateService exchangeRateService;

    @MockitoBean
    private PLNKZTExchangeBot bot;

    @Test
    @DisplayName("Should use fallback PLN to KZT rate when API disabled")
    void testFallbackPLNtoKZTRate() {
        BigDecimal plnToKzt = exchangeRateService.getCurrentPLNtoKZTRate();

        assertThat(plnToKzt).isNotNull();
        assertThat(plnToKzt).isEqualByComparingTo(new BigDecimal("147.50"));
    }

    @Test
    @DisplayName("Should calculate inverse KZT to PLN rate from fallback")
    void testFallbackKZTtoPLNRate() {
        BigDecimal kztToPln = exchangeRateService.getCurrentKZTtoPLNRate();

        assertThat(kztToPln).isNotNull();
        assertThat(kztToPln).isGreaterThan(BigDecimal.ZERO);
        assertThat(kztToPln).isLessThan(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("Should return consistent rates on multiple calls")
    void testRateConsistency() {
        BigDecimal rate1 = exchangeRateService.getCurrentPLNtoKZTRate();
        BigDecimal rate2 = exchangeRateService.getCurrentPLNtoKZTRate();
        BigDecimal rate3 = exchangeRateService.getCurrentPLNtoKZTRate();

        assertThat(rate1).isEqualByComparingTo(rate2);
        assertThat(rate2).isEqualByComparingTo(rate3);
        assertThat(rate1).isEqualByComparingTo(new BigDecimal("147.50"));
    }

    @Test
    @DisplayName("Should return positive non-zero rates")
    void testRateValidity() {
        BigDecimal plnToKzt = exchangeRateService.getCurrentPLNtoKZTRate();
        BigDecimal kztToPln = exchangeRateService.getCurrentKZTtoPLNRate();

        assertThat(plnToKzt).isGreaterThan(BigDecimal.ZERO);
        assertThat(kztToPln).isGreaterThan(BigDecimal.ZERO);

        assertThat(plnToKzt).isGreaterThan(new BigDecimal("100"));
        assertThat(plnToKzt).isLessThan(new BigDecimal("200"));

        assertThat(kztToPln).isGreaterThan(new BigDecimal("0.005"));
        assertThat(kztToPln).isLessThan(new BigDecimal("0.01"));
    }

    @Test
    @DisplayName("Should maintain inverse relationship between rates")
    void testInverseRelationship() {
        BigDecimal plnToKzt = exchangeRateService.getCurrentPLNtoKZTRate();
        BigDecimal kztToPln = exchangeRateService.getCurrentKZTtoPLNRate();

        BigDecimal product = plnToKzt.multiply(kztToPln);

        assertThat(product).isGreaterThan(new BigDecimal("0.99"));
        assertThat(product).isLessThan(new BigDecimal("1.01"));
    }
}