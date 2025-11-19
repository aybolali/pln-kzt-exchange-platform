package pl.aybolali.plnkztexchangebot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateService {

    private final RestTemplate restTemplate;

    @Value("${app.currency-api.enabled}")
    private boolean apiEnabled;

    @Value("${app.currency-api.primary-url}")
    private String primaryUrl;

    @Value("${app.currency-api.fallback-url}")
    private String fallbackUrl;

    @Value("${app.currency-api.fallback-rate}")
    private Double fallbackRate;

    private BigDecimal roundRate(BigDecimal value, int scale) {
        return BigDecimal.valueOf(Math.round(value.doubleValue() * Math.pow(10, scale)) / Math.pow(10, scale));
    }


    @Cacheable(value = "exchangeRates", key = "'PLN_KZT'")
    public BigDecimal getCurrentPLNtoKZTRate() {
        if (!apiEnabled) {
            log.debug("API disabled, using fallback: {}", fallbackRate);
            return BigDecimal.valueOf(fallbackRate);
        }

        BigDecimal rateFromNationalbank = fetchFromNationalbankKz();
        if (rateFromNationalbank != null) {
            log.info("✅ PLN→KZT получен из Nationalbank.kz: {}", rateFromNationalbank);
            return rateFromNationalbank;
        }

        BigDecimal directRate = getRate("pln", "kzt");
        if (directRate != null) {
            log.info("✅ PLN→KZT получен из fallback API: {}", directRate);
            return directRate;
        }

        BigDecimal inverseRate = getRate("kzt", "pln");
        if (inverseRate != null && inverseRate.doubleValue() > 0) {
            BigDecimal calculated = roundRate(BigDecimal.valueOf(1.0 / inverseRate.doubleValue()), 4);
            log.debug("PLN→KZT from inverse: {}", calculated);
            return calculated;
        }

        log.info("All sources failed, using fallback: {}", fallbackRate);
        return BigDecimal.valueOf(fallbackRate);
    }


    @Cacheable(value = "exchangeRates", key = "'KZT_PLN'")
    public BigDecimal getCurrentKZTtoPLNRate() {
        if (!apiEnabled) {
            BigDecimal fallbackInverse = roundRate(BigDecimal.valueOf(1.0 / fallbackRate), 8);
            log.debug("API disabled, using fallback inverse: {}", fallbackInverse);
            return fallbackInverse;
        }

        BigDecimal plnToKzt = getCurrentPLNtoKZTRate();

        if (plnToKzt.doubleValue() > 0) {
            BigDecimal calculated = roundRate(BigDecimal.valueOf(1.0 / plnToKzt.doubleValue()), 6);
            log.debug("KZT→PLN from inverse: {}", calculated);
            return calculated;
        }


        BigDecimal fallbackInverse = roundRate(BigDecimal.valueOf(1.0 / fallbackRate), 6);
        log.debug("Using fallback inverse: {}", fallbackInverse);
        return fallbackInverse;
    }

    private BigDecimal getRate(String from, String to) {
        BigDecimal rate = fetchRate(fallbackUrl, from, to);
        if (rate != null) {
            log.debug("Курс {}→{} получен из fallback API", from.toUpperCase(), to.toUpperCase());
            return rate;
        }
        return null;
    }

    private BigDecimal fetchRate(String baseUrl, String from, String to) {
        try {
            String url = String.format("%s/%s.json", baseUrl, from.toLowerCase());

            log.debug("📡 Запрос к fallback API: {}", url);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rates = (Map<String, Object>) response.get(from.toLowerCase());

                if (rates != null && rates.containsKey(to.toLowerCase())) {
                    Object value = rates.get(to.toLowerCase());

                    if (value instanceof Number) {
                        double numValue = ((Number) value).doubleValue();
                        if (numValue > 0) {
                            return BigDecimal.valueOf(numValue);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to fetch {}→{} from fallback API: {}",
                    from.toUpperCase(), to.toUpperCase(), e.getMessage());
        }
        return null;
    }

    private BigDecimal fetchFromNationalbankKz() {
        try {
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            String url = primaryUrl + "?fdate=" + date;

            log.debug("🇰🇿 Запрос к Nationalbank.kz: {}", url);

            String xmlResponse = restTemplate.getForObject(url, String.class);

            if (xmlResponse.isEmpty()) {
                log.warn("Пустой ответ от Nationalbank.kz");
                return null;
            }

            // Парсинг XML для PLN
            BigDecimal rate = parsePlnFromXml(xmlResponse);

            if (rate != null) {
                log.debug("Распарсен курс PLN из Nationalbank.kz: {}", rate);
                return rate;
            }

            log.warn("PLN не найден в ответе Nationalbank.kz");
            return null;

        } catch (Exception e) {
            log.debug("Ошибка при запросе к Nationalbank.kz: {}", e.getMessage());
            return null;
        }
    }

    private BigDecimal parsePlnFromXml(String xml) {
        try {
            // Ищем блок с PLN
            int plnIndex = xml.indexOf("<title>PLN</title>");
            if (plnIndex == -1) {
                return null;
            }

            // Ищем <description> после <title>PLN</title>
            int descStart = xml.indexOf("<description>", plnIndex);
            int descEnd = xml.indexOf("</description>", plnIndex);

            if (descStart == -1 || descEnd == -1) {
                return null;
            }

            // Извлекаем значение курса
            String rateStr = xml.substring(descStart + 13, descEnd).trim();
            return new BigDecimal(rateStr);

        } catch (Exception e) {
            log.debug("Ошибка парсинга XML: {}", e.getMessage());
            return null;
        }
    }
}