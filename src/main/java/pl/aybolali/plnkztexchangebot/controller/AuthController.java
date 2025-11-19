package pl.aybolali.plnkztexchangebot.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import pl.aybolali.plnkztexchangebot.dto.ApiResponseDTO;
import pl.aybolali.plnkztexchangebot.dto.CreateUserDTO;
import pl.aybolali.plnkztexchangebot.dto.UserProfileDTO;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.mapper.UserMapper;
import pl.aybolali.plnkztexchangebot.service.UserService;

import jakarta.validation.Valid;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserService userService;

    /**
     * Регистрация нового пользователя
     * POST /api/auth/register
     */
    @PostMapping("/register")
    @Transactional
    public ApiResponseDTO<UserProfileDTO> register(
            @Valid @RequestBody CreateUserDTO dto,
            BindingResult bindingResult) { // 🔥 ДОБАВЛЕНО: BindingResult для детальных ошибок

        log.info("Registering new user: {}", dto.telegramUsername());

        // 🔥 ДОБАВЛЕНО: Проверяем ошибки валидации
        if (bindingResult.hasErrors()) {
            String errorMessage = bindingResult.getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));

            log.warn("Validation errors for user {}: {}", dto.telegramUsername(), errorMessage);
            return ApiResponseDTO.error("Ошибка валидации: " + errorMessage);
        }

        try {
            // Проверяем что username свободен
            if (userService.findByTelegramUsername(dto.telegramUsername()).isPresent()) {
                return ApiResponseDTO.error("Пользователь с таким username уже существует");
            }

            Long generatedTelegramUserId = System.currentTimeMillis() + (long)(Math.random() * 1000);

            // Создаем пользователя
            User user = userService.registerUser(
                    0L, //это для REST API тестов, не важно.
                    dto.telegramUsername(),
                    dto.firstName(),
                    dto.lastName()
            );

            log.info("User registered successfully: {}", user.getId());

            // Возвращаем профиль
            UserProfileDTO responseDTO = UserMapper.toProfileDTO(user);
            return ApiResponseDTO.success(responseDTO, "Пользователь успешно зарегистрирован");

        } catch (Exception e) {
            log.error("Error registering user: " + dto.telegramUsername(), e);
            return ApiResponseDTO.error("Ошибка регистрации пользователя: " + e.getMessage());
        }
    }

    /**
     * Проверить доступность username
     * GET /api/auth/check/john_doe
     */
    @GetMapping("/check/{username}")
    public ApiResponseDTO<Boolean> checkUsernameAvailability(@PathVariable String username) {
        log.info("Checking username availability: {}", username);

        try {
            boolean isAvailable = userService.findByTelegramUsername(username).isEmpty();
            String message = isAvailable ? "Username доступен" : "Username занят";

            return ApiResponseDTO.success(isAvailable, message);

        } catch (Exception e) {
            log.error("Error checking username availability: " + username, e);
            return ApiResponseDTO.error("Ошибка проверки доступности username");
        }
    }
}