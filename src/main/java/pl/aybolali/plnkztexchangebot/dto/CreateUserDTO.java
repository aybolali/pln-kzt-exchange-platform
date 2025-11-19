package pl.aybolali.plnkztexchangebot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Создание нового пользователя (регистрация через Telegram)
 */
public record CreateUserDTO(
        @NotBlank
        @NotNull
        @Pattern(regexp = "^[a-zA-Z0-9_]+$")
        String telegramUsername,

        @Size(min = 2, max = 32)
        String firstName,

        @Size(max = 32)
        String lastName
) {}