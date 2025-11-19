package pl.aybolali.plnkztexchangebot.telegram;

/**
 * Состояния разговора при создании заявки на обмен
 */
public enum ConversationState {
    INITIAL,

    // Создание заявки
    AWAITING_CURRENCY,
    AWAITING_AMOUNT,
    AWAITING_TRANSFER_METHOD,
    AWAITING_COMMENT,

    // Редактирование заявки
    EDITING_EXCHANGE_REQUEST_AMOUNT,

    // Создание сделки (отклик)
    AWAITING_DEAL_AMOUNT,          // Ручной ввод суммы
    AWAITING_DEAL_CONFIRMATION,    // Подтверждение автоматической суммы
}