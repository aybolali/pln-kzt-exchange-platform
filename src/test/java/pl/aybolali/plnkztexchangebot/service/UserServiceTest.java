package pl.aybolali.plnkztexchangebot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;
import pl.aybolali.plnkztexchangebot.entity.User;
import pl.aybolali.plnkztexchangebot.repository.DealRepository;
import pl.aybolali.plnkztexchangebot.repository.RatingRepository;
import pl.aybolali.plnkztexchangebot.repository.UserRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RatingRepository ratingRepository;

    @Mock
    private DealRepository dealRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void registerUser_ShouldCreateNewUser() {
        // Given
        User expectedUser = User.builder()
                .telegramUserId(123456789L)  // ⭐ ДОБАВЛЕНО
                .telegramUsername("newuser")
                .firstName("John")
                .lastName("Doe")
                .trustRating(BigDecimal.ZERO)
                .successfulDeals(0)
                .isEnabled(true)
                .isPhoneVerified(false)
                .build();

        when(userRepository.findByTelegramUserId(123456789L)).thenReturn(Optional.empty());  // ⭐ ДОБАВЛЕНО
        when(userRepository.save(any(User.class))).thenReturn(expectedUser);

        // When
        // ⭐ ИЗМЕНЕНО: добавлен telegramUserId как первый параметр
        User result = userService.registerUser(123456789L, "newuser", "John", "Doe");

        // Then
        assertNotNull(result);
        assertEquals(123456789L, result.getTelegramUserId());  // ⭐ ДОБАВЛЕНО
        assertEquals("newuser", result.getTelegramUsername());
        assertEquals("John", result.getFirstName());
        assertEquals("Doe", result.getLastName());
        assertEquals(BigDecimal.ZERO, result.getTrustRating());
        assertTrue(result.getIsEnabled());
        assertFalse(result.getIsPhoneVerified());

        verify(userRepository).findByTelegramUserId(123456789L);  // ⭐ ДОБАВЛЕНО
        verify(userRepository).save(any(User.class));
    }

    @Test
    void findByTelegramUsername_ShouldReturnUser() {
        // Given
        User user = createTestUser();
        when(userRepository.findByTelegramUsername("testuser")).thenReturn(Optional.of(user));

        // When
        Optional<User> result = userService.findByTelegramUsername("testuser");

        // Then
        assertTrue(result.isPresent());
        assertEquals("testuser", result.get().getTelegramUsername());
        verify(userRepository).findByTelegramUsername("testuser");
    }

    @Test
    void updateUserStatsAfterDeal_ShouldUpdateSuccessfulDealsFromDatabase() {
        // Given
        User user = createTestUser();
        user.setSuccessfulDeals(3);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(dealRepository.countCompletedByUserId(1L)).thenReturn(5L);
        when(ratingRepository.getAverageRatingByUserId(1L)).thenReturn(4.5);

        // When
        userService.updateUserStatsAfterDeal(1L);

        // Then
        verify(userRepository).findById(1L);
        verify(dealRepository).countCompletedByUserId(1L);
        verify(ratingRepository).getAverageRatingByUserId(1L);

        verify(userRepository).save(argThat(savedUser ->
                savedUser.getSuccessfulDeals() == 5
        ));
    }

    private User createTestUser() {
        return User.builder()
                .id(1L)
                .telegramUserId(999888777L)
                .telegramUsername("testuser")
                .firstName("Test")
                .trustRating(BigDecimal.valueOf(4.5))
                .successfulDeals(3)
                .isEnabled(true)
                .build();
    }
}