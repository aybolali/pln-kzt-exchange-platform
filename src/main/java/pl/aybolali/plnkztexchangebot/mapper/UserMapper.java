package pl.aybolali.plnkztexchangebot.mapper;

import org.springframework.stereotype.Component;
import pl.aybolali.plnkztexchangebot.dto.CreateUserDTO;
import pl.aybolali.plnkztexchangebot.dto.UserProfileDTO;
import pl.aybolali.plnkztexchangebot.dto.UserPublicDTO;
import pl.aybolali.plnkztexchangebot.entity.User;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 🔄 USER MAPPER: Конвертеры Entity ↔ DTO для User
 *
 * Поддерживает различные представления пользователя:
 * - UserPublicDTO - для показа другим пользователям
 * - UserProfileDTO - для собственного профиля
 * - CreateUserDTO - для создания новых пользователей
 */
@Component
public class UserMapper {

    /**
     * 📤 ENTITY TO PUBLIC DTO: User → UserPublicDTO
     *
     * Для показа пользователя другим (скрывает приватную информацию)
     */
    public static UserPublicDTO toPublicDTO(User user) {
        if (user == null) return null;

        return new UserPublicDTO(
                user.getId(),
                user.getTelegramUsername(),
                user.getFirstName(),
                user.getTrustRating(),
                user.getSuccessfulDeals(),
                user.getCreatedAt()
        );
    }

    /**
     * 📤 ENTITY TO PROFILE DTO: User → UserProfileDTO
     *
     * Для собственного профиля (включает приватную информацию)
     */
    public static UserProfileDTO toProfileDTO(User user) {
        if (user == null) return null;

        return new UserProfileDTO(
                user.getId(),
                user.getTelegramUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhone(),
                user.getTrustRating(),
                user.getSuccessfulDeals(),
                user.getIsPhoneVerified(),
                user.getIsEnabled(),
                user.getCreatedAt()
        );
    }

    /**
     * 📥 DTO TO ENTITY: CreateUserDTO → User
     *
     * Для создания новых пользователей
     */
    public static User toEntity(CreateUserDTO dto) {
        if (dto == null) return null;

        return User.builder()
                .telegramUsername(dto.telegramUsername())
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .build();
    }

    /**
     * 📤 LIST CONVERSION: List<User> → List<UserPublicDTO>
     *
     * Удобный метод для конвертации списков в публичные DTO
     */
    public static List<UserPublicDTO> toPublicDTOList(List<User> users) {
        if (users == null) return Collections.emptyList();

        return users.stream()
                .map(UserMapper::toPublicDTO)
                .collect(Collectors.toList());
    }

    /**
     * 📤 LIST CONVERSION: List<User> → List<UserProfileDTO>
     *
     * Удобный метод для конвертации списков в профильные DTO
     */
    public static List<UserProfileDTO> toProfileDTOList(List<User> users) {
        if (users == null) return Collections.emptyList();

        return users.stream()
                .map(UserMapper::toProfileDTO)
                .collect(Collectors.toList());
    }
}