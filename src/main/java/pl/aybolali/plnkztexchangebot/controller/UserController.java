package pl.aybolali.plnkztexchangebot.controller;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import pl.aybolali.plnkztexchangebot.dto.ApiResponseDTO;
import pl.aybolali.plnkztexchangebot.dto.PagedResponseDTO;
import pl.aybolali.plnkztexchangebot.dto.UserProfileDTO;
import pl.aybolali.plnkztexchangebot.dto.UserPublicDTO;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.mapper.UserMapper;
import pl.aybolali.plnkztexchangebot.service.UserService;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*") // Для разработки, в продакшене ограничить - security config
public class UserController {
    private final  UserService userService;

    // ================================
    // ПУБЛИЧНЫЕ ENDPOINTS (без авторизации)
    // ================================

    /**
     * Получить список всех пользователей (сортированный по рейтингу)
     * GET /api/users?page=0&size=20
     */
    @GetMapping
    public ApiResponseDTO<PagedResponseDTO<UserPublicDTO>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("Getting all users, page: {}, size: {}", page, size);

        try {
            // 📥 Entity из БД (отсортированные)
            List<User> sortedUsers = userService.getAllUsersSorted();

            //Пересортировка по ID (от 1 до N)
            sortedUsers.sort(Comparator.comparingLong(User::getId));

            // Простая пагинация в памяти (для MVP)
            int start = page * size;
            int end = Math.min(start + size, sortedUsers.size());
            List<User> pageContent = sortedUsers.subList(start, end);

            // Entity → DTO
            List<UserPublicDTO> userDTOs = pageContent.stream()
                    .map(UserMapper::toPublicDTO)
                    .toList();

            // Создаем PagedResponse вручную
            PagedResponseDTO<UserPublicDTO> pagedResponse = new PagedResponseDTO<>(
                    userDTOs,
                    page,
                    size,
                    sortedUsers.size(),
                    (int) Math.ceil((double) sortedUsers.size() / size),
                    end < sortedUsers.size(),
                    page > 0
            );

            return ApiResponseDTO.success(pagedResponse);

        } catch (Exception e) {
            log.error("Error getting all users", e);
            return ApiResponseDTO.error("Ошибка получения списка пользователей");
        }
    }

    // ================================
    // АВТОРИЗОВАННЫЕ ENDPOINTS
    // ================================

    /**
     * Получить свой профиль
     * GET /api/users/me
     */
    @GetMapping("/me")
    public ApiResponseDTO<UserProfileDTO> getMyProfile(Authentication auth) {
        String username = auth.getName(); // Telegram username
        log.info("Getting profile for user: {}", username);

        try {
            // 📥 Entity из БД
            User user = userService.findByTelegramUsername(username)
                    .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

            // Entity → DTO
            UserProfileDTO profileDTO = UserMapper.toProfileDTO(user);
            return ApiResponseDTO.success(profileDTO);

        } catch (RuntimeException e) {
            log.warn("User not found: {}", username);
            return ApiResponseDTO.error("Пользователь не найден");

        } catch (Exception e) {
            log.error("Error getting profile for user: " + username, e);
            return ApiResponseDTO.error("Ошибка получения профиля");
        }
    }

    // Вспомогательный метод для получения текущего пользователя
    private User getCurrentUser(Authentication auth) {
        String username = auth.getName();
        return userService.findByTelegramUsername(username)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }
}
