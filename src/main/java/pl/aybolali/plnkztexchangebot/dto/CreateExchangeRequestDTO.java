package pl.aybolali.plnkztexchangebot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import pl.aybolali.plnkztexchangebot.entity.TransferMethod;
import java.math.BigDecimal;

/**
 * Создание запроса обмена
 */
public record CreateExchangeRequestDTO(
        @JsonProperty("currencyNeed")
        @NotBlank(message = "Укажите валюту (PLN или KZT)")
        @Pattern(regexp = "PLN|KZT", message = "Поддерживаются только валюты PLN и KZT")
        String currencyNeed,

        @JsonProperty("amountNeed")
        @NotNull(message = "Укажите сумму обмена")
        @Positive(message = "Сумма должна быть больше 0")
        @DecimalMin(value = "0.01", message = "Минимальная сумма: 0.01")
        @DecimalMax(value = "1000000", message = "Максимальная сумма: 1,000,000")
        BigDecimal amountNeed,

        @JsonProperty("transferMethod")
        @NotNull(message = "Выберите способ перевода")
        TransferMethod transferMethod,

        @JsonProperty("notes")
        @Size(max = 500, message = "Описание не может быть длиннее 500 символов")
        String notes
) {}