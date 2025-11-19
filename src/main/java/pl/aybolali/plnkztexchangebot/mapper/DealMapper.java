package pl.aybolali.plnkztexchangebot.mapper;

import org.springframework.stereotype.Component;
import pl.aybolali.plnkztexchangebot.dto.DealDTO;
import pl.aybolali.plnkztexchangebot.entity.Deal;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 🔄 DEAL MAPPER: Конвертеры Entity ↔ DTO для Deal
 *
 * Централизованное место для всех преобразований Deal.
 * Используется во всех сервисах для единообразия.
 */
@Component
public class DealMapper {

    /**
     * 📤 ENTITY TO DTO: Deal → DealDTO
     * Основной метод конвертации для всех случаев использования
     */
    public static DealDTO toDTO(Deal deal) {
        if (deal == null) return null;

        return new DealDTO(
                deal.getId(),
                UserMapper.toPublicDTO(deal.getRequester()),
                UserMapper.toPublicDTO(deal.getProvider()),
                deal.getAmount(),
                deal.getCurrency(),
                deal.getExchangeRate(),
                deal.getTransferMethod(),
                deal.getStatus(),
                deal.getCreatedAt(),
                deal.getFinishedAt(),
                deal.getConvertedAmount(),    // Бизнес-метод из Entity
                deal.getOppositeCurrency()    // Бизнес-метод из Entity
        );
    }

    /**
     * 📤 LIST CONVERSION: List<Deal> → List<DealDTO>
     * Удобный метод для конвертации списков
     */
    public static List<DealDTO> toDTOList(List<Deal> deals) {
        if (deals == null) return Collections.emptyList();

        return deals.stream()
                .map(DealMapper::toDTO)
                .collect(Collectors.toList());
    }
}