package pl.aybolali.plnkztexchangebot.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import pl.aybolali.plnkztexchangebot.entity.*;
import pl.aybolali.plnkztexchangebot.exception.BusinessException;
import pl.aybolali.plnkztexchangebot.repository.ExchangeRequestRepository;
import pl.aybolali.plnkztexchangebot.repository.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
@ActiveProfiles("test")
class ExchangeRequestServiceTest {

    @Mock private ExchangeRequestRepository exchangeRequestRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ExchangeRequestService exchangeRequestService;

    @Test
    void createExchangeRequest_ShouldCreateValidRequest() {
        Long userId = 1L;
        String currencyNeed = "PLN";
        BigDecimal amountNeed = new BigDecimal("500");
        TransferMethod transferMethod = TransferMethod.BANK_TRANSFER;
        String notes = "University fees";

        User user = createUser(userId, "testuser");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(exchangeRequestRepository.findByUserIdAndStatus(userId, ExchangeRequestStatus.ACTIVE))
                .thenReturn(List.of());

        ExchangeRequest expectedRequest = ExchangeRequest.builder()
                .id(1L)
                .user(user)
                .currencyNeed(ExchangeRequest.Currency.PLN)
                .amountNeed(amountNeed)
                .status(ExchangeRequestStatus.ACTIVE)
                .transferMethod(transferMethod)
                .notes(notes)
                .build();

        when(exchangeRequestRepository.save(any(ExchangeRequest.class))).thenReturn(expectedRequest);

        ExchangeRequest result = exchangeRequestService.createExchangeRequest(
                userId, currencyNeed, amountNeed, transferMethod, notes
        );

        assertNotNull(result);
        assertEquals(ExchangeRequest.Currency.PLN, result.getCurrencyNeed());
        assertEquals(amountNeed, result.getAmountNeed());
        assertEquals(ExchangeRequestStatus.ACTIVE, result.getStatus());
        assertEquals(notes, result.getNotes());

        verify(userRepository).findById(userId);
        verify(exchangeRequestRepository).save(any(ExchangeRequest.class));
    }

    @Test
    void cancelExchangeRequest_ShouldCancelActiveRequest() {
        Long requestId = 1L;
        Long userId = 1L;

        User user = createUser(userId, "testuser");
        ExchangeRequest request = ExchangeRequest.builder()
                .id(requestId)
                .user(user)
                .currencyNeed(ExchangeRequest.Currency.PLN)
                .amountNeed(new BigDecimal("500"))
                .status(ExchangeRequestStatus.ACTIVE)
                .transferMethod(TransferMethod.BANK_TRANSFER)
                .build();

        when(exchangeRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        ExchangeRequest cancelledRequest = ExchangeRequest.builder()
                .id(requestId)
                .user(user)
                .currencyNeed(ExchangeRequest.Currency.PLN)
                .amountNeed(new BigDecimal("500"))
                .status(ExchangeRequestStatus.CANCELLED)
                .transferMethod(TransferMethod.BANK_TRANSFER)
                .finishedAt(LocalDateTime.now())
                .build();

        when(exchangeRequestRepository.save(any(ExchangeRequest.class))).thenReturn(cancelledRequest);

        ExchangeRequest result = exchangeRequestService.cancelExchangeRequest(requestId, userId);

        assertNotNull(result);
        assertEquals(ExchangeRequestStatus.CANCELLED, result.getStatus());
        assertNotNull(result.getFinishedAt());

        verify(exchangeRequestRepository).findById(requestId);
        verify(exchangeRequestRepository).save(any(ExchangeRequest.class));
    }


    @Test
    void cancelExchangeRequest_ShouldThrowExceptionWhenNotOwner() {

        Long requestId = 1L;
        Long ownerId = 1L;
        Long otherUserId = 2L;

        User owner = createUser(ownerId, "owner");
        ExchangeRequest request = ExchangeRequest.builder()
                .id(requestId)
                .user(owner)
                .status(ExchangeRequestStatus.ACTIVE)
                .build();

        when(exchangeRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> exchangeRequestService.cancelExchangeRequest(requestId, otherUserId)
        );

        assertEquals("Вы не можете отменить чужую заявку", exception.getMessage());
        verify(exchangeRequestRepository, never()).save(any());
    }

    @Test
    void cancelExchangeRequest_ShouldThrowExceptionWhenAlreadyFinished() {
        Long requestId = 1L;
        Long userId = 1L;

        User user = createUser(userId, "testuser");
        ExchangeRequest request = ExchangeRequest.builder()
                .id(requestId)
                .user(user)
                .status(ExchangeRequestStatus.COMPLETED)
                .build();

        when(exchangeRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> exchangeRequestService.cancelExchangeRequest(requestId, userId)
        );

        assertEquals("Заявка уже завершена", exception.getMessage());
        verify(exchangeRequestRepository, never()).save(any());
    }

    @Test
    void updateExchangeRequest_ShouldUpdateAmountAndNotes() {
        Long requestId = 1L;
        BigDecimal newAmount = new BigDecimal("600");
        String newNotes = "Updated notes";

        User user = createUser(1L, "testuser");
        ExchangeRequest request = ExchangeRequest.builder()
                .id(requestId)
                .user(user)
                .currencyNeed(ExchangeRequest.Currency.PLN)
                .amountNeed(new BigDecimal("500"))
                .status(ExchangeRequestStatus.ACTIVE)
                .notes("Old notes")
                .build();

        when(exchangeRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(exchangeRequestRepository.save(any(ExchangeRequest.class))).thenAnswer(i -> i.getArgument(0));

        ExchangeRequest result = exchangeRequestService.updateExchangeRequest(
                requestId, newAmount, newNotes
        );

        assertNotNull(result);
        assertEquals(newAmount, result.getAmountNeed());
        assertEquals(newNotes, result.getNotes());
        verify(exchangeRequestRepository).save(any(ExchangeRequest.class));
    }


    @Test
    void updateAfterPartialDeal_ShouldDecreaseAmount() {
        Long requestId = 1L;
        BigDecimal dealAmount = new BigDecimal("300");

        User user = createUser(1L, "testuser");
        ExchangeRequest request = ExchangeRequest.builder()
                .id(requestId)
                .user(user)
                .currencyNeed(ExchangeRequest.Currency.PLN)
                .amountNeed(new BigDecimal("1000"))
                .status(ExchangeRequestStatus.ACTIVE)
                .build();

        when(exchangeRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(exchangeRequestRepository.save(any(ExchangeRequest.class))).thenAnswer(i -> i.getArgument(0));

        exchangeRequestService.updateAfterPartialDeal(requestId, dealAmount);

        verify(exchangeRequestRepository).save(argThat(req ->
                req.getAmountNeed().compareTo(new BigDecimal("700")) == 0
        ));
    }


    @Test
    void getActiveByUserId_ShouldReturnActiveRequests() {
        Long userId = 1L;
        User user = createUser(userId, "testuser");

        List<ExchangeRequest> activeRequests = List.of(
                createExchangeRequest(user, ExchangeRequestStatus.ACTIVE),
                createExchangeRequest(user, ExchangeRequestStatus.ACTIVE)
        );

        when(exchangeRequestRepository.findByUserIdAndStatus(userId, ExchangeRequestStatus.ACTIVE))
                .thenReturn(activeRequests);

        List<ExchangeRequest> result = exchangeRequestService.getActiveByUserId(userId);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(r -> r.getStatus() == ExchangeRequestStatus.ACTIVE));
    }


    @Test
    void getRequestsByCurrency_ShouldReturnFilteredRequests() {
        ExchangeRequest.Currency currency = ExchangeRequest.Currency.PLN;
        Pageable pageable = Pageable.unpaged();

        User user = createUser(1L, "testuser");
        List<ExchangeRequest> plnRequests = List.of(
                createExchangeRequest(user, currency, ExchangeRequestStatus.ACTIVE)
        );

        when(exchangeRequestRepository.findActiveByCurrency(currency, pageable))
                .thenReturn(new PageImpl<>(plnRequests));

        Page<ExchangeRequest> result = exchangeRequestService.getRequestsByCurrency(currency, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(currency, result.getContent().get(0).getCurrencyNeed());
    }


    @Test
    void createExchangeRequest_ShouldThrowExceptionWhenTooManyActiveRequests() {
        Long userId = 1L;
        User user = createUser(userId, "testuser");

        List<ExchangeRequest> fiveActiveRequests = List.of(
                createExchangeRequest(user, ExchangeRequestStatus.ACTIVE),
                createExchangeRequest(user, ExchangeRequestStatus.ACTIVE),
                createExchangeRequest(user, ExchangeRequestStatus.ACTIVE),
                createExchangeRequest(user, ExchangeRequestStatus.ACTIVE),
                createExchangeRequest(user, ExchangeRequestStatus.ACTIVE)
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(exchangeRequestRepository.findByUserIdAndStatus(userId, ExchangeRequestStatus.ACTIVE))
                .thenReturn(fiveActiveRequests);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> exchangeRequestService.createExchangeRequest(
                        userId, "PLN", new BigDecimal("500"), TransferMethod.BANK_TRANSFER, "test"
                )
        );

        assertEquals("У вас уже есть 5 активных заявок. Закройте одну из существующих через /my_requests", exception.getMessage());
        verify(exchangeRequestRepository, never()).save(any());
    }

    private User createUser(Long id, String username) {
        return User.builder()
                .id(id)
                .telegramUsername(username)
                .firstName("Test")
                .trustRating(new BigDecimal("5.0"))
                .successfulDeals(0)
                .isPhoneVerified(true)
                .isEnabled(true)
                .build();
    }

    private ExchangeRequest createExchangeRequest(User user, ExchangeRequestStatus status) {
        return createExchangeRequest(user, ExchangeRequest.Currency.PLN, status);
    }

    private ExchangeRequest createExchangeRequest(User user, ExchangeRequest.Currency currency, ExchangeRequestStatus status) {
        return ExchangeRequest.builder()
                .id(1L)
                .user(user)
                .currencyNeed(currency)
                .amountNeed(new BigDecimal("500"))
                .status(status)
                .transferMethod(TransferMethod.BANK_TRANSFER)
                .build();
    }
}