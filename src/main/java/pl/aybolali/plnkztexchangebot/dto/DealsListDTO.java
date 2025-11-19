package pl.aybolali.plnkztexchangebot.dto;

import pl.aybolali.plnkztexchangebot.entity.Deal;
import pl.aybolali.plnkztexchangebot.entity.DealStatus;

/**
 * Минимальный DTO для списка сделок /my
 */
public record DealsListDTO(
        Long deal_id,
        String deal_with,       // С кем была сделка
        DealStatus status
) {

    // Статический метод для создания из полного Deal
    public static DealsListDTO fromDeal(Deal deal, Long currentUserId) {
        // Определяем с кем была сделка
        boolean isRequester = deal.getRequester().getId().equals(currentUserId);
        String dealWith = isRequester
                ? deal.getProvider().getTelegramUsername()
                : deal.getRequester().getTelegramUsername();

        return new DealsListDTO(
                deal.getId(),
                dealWith,
                deal.getStatus()
        );
    }
}