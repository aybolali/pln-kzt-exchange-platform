package pl.aybolali.plnkztexchangebot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import pl.aybolali.plnkztexchangebot.dto.ApiResponseDTO;
import pl.aybolali.plnkztexchangebot.dto.ExchangeRateDTO;
import pl.aybolali.plnkztexchangebot.service.ExchangeRateService;

import java.math.BigDecimal;

/**
 * REST API для получения актуальных курсов валют
 */
@RestController
@RequestMapping("/api/rates")
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    @GetMapping("/current")
    public ApiResponseDTO<ExchangeRateDTO> getCurrentRates() {
        try {
            BigDecimal plnToKzt = exchangeRateService.getCurrentPLNtoKZTRate();
            BigDecimal kztToPln = exchangeRateService.getCurrentKZTtoPLNRate();

            ExchangeRateDTO rates = ExchangeRateDTO.builder()
                    .plnToKzt(plnToKzt)
                    .kztToPln(kztToPln)
                    .date(java.time.LocalDate.now().toString())
                    .build();

            String message = String.format("Актуальный курс: 1 PLN = %.2f KZT, 1 KZT = %.6f PLN",
                    plnToKzt.doubleValue(), kztToPln.doubleValue());

            return ApiResponseDTO.success(rates, message);

        } catch (Exception e) {
            log.error("Error getting current rates: {}", e.getMessage());
            return ApiResponseDTO.error("Ошибка получения курсов валют");
        }
    }
}