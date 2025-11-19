package pl.aybolali.plnkztexchangebot.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import pl.aybolali.plnkztexchangebot.dto.*;
import pl.aybolali.plnkztexchangebot.entity.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
class MappersTest {

    @Test
    void userMapper_ShouldConvertToPublicDTO() {
        User user = User.builder()
                .id(1L)
                .telegramUsername("testuser")
                .firstName("John")
                .trustRating(BigDecimal.valueOf(4.5))
                .successfulDeals(10)
                .createdAt(LocalDateTime.now())
                .build();

        UserPublicDTO result = UserMapper.toPublicDTO(user);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("testuser", result.telegramUsername());
        assertEquals("John", result.firstName());
        assertEquals(BigDecimal.valueOf(4.5), result.trustRating());
        assertEquals(10, result.successfulDeals());
    }

    @Test
    void userMapper_ShouldConvertToProfileDTO() {
        // Given
        User user = User.builder()
                .id(1L)
                .telegramUsername("testuser")
                .firstName("John")
                .lastName("Doe")
                .phone("+1234567890")
                .trustRating(BigDecimal.valueOf(4.5))
                .successfulDeals(10)
                .isPhoneVerified(true)
                .isEnabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        UserProfileDTO result = UserMapper.toProfileDTO(user);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("testuser", result.telegramUsername());
        assertEquals("John", result.firstName());
        assertEquals("Doe", result.lastName());
        assertEquals("+1234567890", result.phone());
        assertTrue(result.isPhoneVerified());
        assertTrue(result.isEnabled());
    }

    @Test
    void dealMapper_ShouldConvertToDTO() {
        User requester = createTestUser(1L, "requester");
        User provider = createTestUser(2L, "provider");
        ExchangeRequest request = createTestExchangeRequest();

        Deal deal = Deal.builder()
                .id(1L)
                .requester(requester)
                .provider(provider)
                .amount(new BigDecimal("500"))
                .currency(ExchangeRequest.Currency.PLN)
                .exchangeRate(new BigDecimal("182.5"))
                .status(DealStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();

        DealDTO result = DealMapper.toDTO(deal);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("requester", result.requester().telegramUsername());
        assertEquals("provider", result.provider().telegramUsername());
        assertEquals(new BigDecimal("500"), result.amount());
        assertEquals(ExchangeRequest.Currency.PLN, result.currency());
    }

    @Test
    void exchangeRequestMapper_ShouldConvertToDTO() {
        User user = createTestUser(1L, "testuser");
        ExchangeRequest request = ExchangeRequest.builder()
                .id(1L)
                .user(user)
                .currencyNeed(ExchangeRequest.Currency.PLN)
                .amountNeed(new BigDecimal("1000"))
                .status(ExchangeRequestStatus.ACTIVE)
                .notes("Test request")
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();

        ExchangeRequestDTO result = ExchangeRequestMapper.toDTO(request);

        assertNotNull(result);
        assertEquals(1L, result.id());
        assertEquals("PLN", result.currencyNeed());
        assertEquals(new BigDecimal("1000"), result.amountNeed());
        assertEquals("Test request", result.notes());
        assertEquals(2, result.hoursAgo()); // 2 hours ago
    }

    @Test
    void mappers_ShouldHandleNullInput() {
        assertNull(UserMapper.toPublicDTO(null));
        assertNull(DealMapper.toDTO(null));
        assertNull(ExchangeRequestMapper.toDTO(null));
        assertNull(RatingMapper.toDTO(null));
    }

    @Test
    void mappers_ShouldConvertLists() {
        List<User> users = List.of(
                createTestUser(1L, "user1"),
                createTestUser(2L, "user2")
        );

        List<UserPublicDTO> result = UserMapper.toPublicDTOList(users);

        assertEquals(2, result.size());
        assertEquals("user1", result.get(0).telegramUsername());
        assertEquals("user2", result.get(1).telegramUsername());
    }

    private User createTestUser(Long id, String username) {
        return User.builder()
                .id(id)
                .telegramUsername(username)
                .firstName("Test")
                .trustRating(BigDecimal.valueOf(5.0))
                .successfulDeals(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ExchangeRequest createTestExchangeRequest() {
        return ExchangeRequest.builder()
                .id(1L)
                .currencyNeed(ExchangeRequest.Currency.PLN)
                .amountNeed(new BigDecimal("1000"))
                .status(ExchangeRequestStatus.ACTIVE)
                .build();
    }
}
