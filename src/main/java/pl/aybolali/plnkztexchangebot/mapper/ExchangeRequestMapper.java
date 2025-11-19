package pl.aybolali.plnkztexchangebot.mapper;

import org.springframework.stereotype.Component;
import pl.aybolali.plnkztexchangebot.dto.CreateExchangeRequestDTO;
import pl.aybolali.plnkztexchangebot.dto.ExchangeRequestDTO;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequest;
import pl.aybolali.plnkztexchangebot.entity.User;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 🔄 EXCHANGE REQUEST MAPPER: Конвертеры Entity ↔ DTO для ExchangeRequest
 *
 * Централизованное место для всех преобразований ExchangeRequest.
 * Включает бизнес-логику расчета времени "назад".
 */
@Component
public class ExchangeRequestMapper {

    /**
     * 📤 ENTITY TO DTO: ExchangeRequest → ExchangeRequestDTO
     *
     * Основной метод конвертации с расчетом hoursAgo
     */
    public static ExchangeRequestDTO toDTO(ExchangeRequest request) {
        if (request == null) return null;

        // Расчет времени "назад" в часах
        Integer hoursAgo = calculateHoursAgo(request.getCreatedAt());

        return new ExchangeRequestDTO(
                request.getId(),
                UserMapper.toPublicDTO(request.getUser()),
                request.getCurrencyNeed().toString(),
                request.getAmountNeed(),
                request.getStatus(),
                request.getNotes(),
                request.getTransferMethod(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getFinishedAt(),
                hoursAgo
        );
    }

    /**
     * 📥 DTO TO ENTITY: CreateExchangeRequestDTO → ExchangeRequest
     *
     * Для создания новых запросов
     */
    public static ExchangeRequest toEntity(CreateExchangeRequestDTO dto, User user) {
        if (dto == null || user == null) return null;

        return ExchangeRequest.builder()
                .user(user)
                .currencyNeed(ExchangeRequest.Currency.valueOf(dto.currencyNeed()))
                .amountNeed(dto.amountNeed())
                .transferMethod(dto.transferMethod())
                .notes(dto.notes())
                .build();
    }

    /**
     * 📤 LIST CONVERSION: List<ExchangeRequest> → List<ExchangeRequestDTO>
     *
     * Удобный метод для конвертации списков
     */
    public static List<ExchangeRequestDTO> toDTOList(List<ExchangeRequest> requests) {
        if (requests == null) return Collections.emptyList();

        return requests.stream()
                .map(ExchangeRequestMapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * ⏰ HOURS AGO CALCULATION: Расчет времени "назад"
     *
     * Приватный метод для расчета времени создания запроса
     */
    private static Integer calculateHoursAgo(LocalDateTime createdAt) {
        if (createdAt == null) return null;

        Duration duration = Duration.between(createdAt, LocalDateTime.now());
        long hours = duration.toHours();

        return (int) Math.max(0, hours);
    }
}