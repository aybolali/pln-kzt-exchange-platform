package pl.aybolali.plnkztexchangebot.telegram;

import java.math.BigDecimal;

/**
 * Константы для Telegram бота
 */
public class TelegramConstants {

    // Лимиты сумм
    public static final BigDecimal MIN_EXCHANGE_AMOUNT = BigDecimal.TEN;

    // Лимиты отображения
    public static final int MAX_SEARCH_RESULTS = 10;
    public static final int BUTTONS_PER_ROW = 3;
    public static final int HISTORY_PAGE_SIZE = 10;
    public static final int MAX_MATCHING_OFFERS = 5;

    // ============================================
    // CALLBACK DATA - Основные действия
    // ============================================
    public static final String CALLBACK_SHOW_MENU = "show_menu";
    public static final String CALLBACK_CURRENT_PAGE = "current_page";

    // ============================================
    // CALLBACK DATA - Меню и навигация
    // ============================================
    public static final String CALLBACK_MENU = "menu:";
    public static final String CALLBACK_CURRENCY = "currency:";
    public static final String CALLBACK_METHOD = "method:";

    // ============================================
    // CALLBACK DATA - Поиск и заявки
    // ============================================
    public static final String CALLBACK_SEARCH_CURRENCY = "search_currency:";
    public static final String CALLBACK_RESPOND = "respond:";
    public static final String CALLBACK_VIEW_REQUEST = "view_request:";

    // ============================================
    // CALLBACK DATA - Создание и редактирование заявок
    // ============================================
    public static final String CALLBACK_SKIP_COMMENT = "skip_comment";
    public static final String CALLBACK_CANCEL_REQUEST = "cancel_request";
    public static final String CALLBACK_CONFIRM_REQUEST = "confirm_request";
    public static final String CALLBACK_EDIT_REQUEST = "edit_request";

    // ============================================
    // CALLBACK DATA - Управление заявками
    // ============================================
    public static final String CALLBACK_SHOW_REQUEST_MANAGEMENT = "show_request_management";
    public static final String CALLBACK_ACTION_EDIT_REQUEST = "action_edit_request";
    public static final String CALLBACK_ACTION_CANCEL_REQUEST = "action_cancel_request";
    public static final String CALLBACK_SELECT_CANCEL = "select_cancel_index:";
    public static final String CALLBACK_SELECT_EDIT = "select_edit_index:";
    public static final String CALLBACK_CANCEL_REQUEST_BUTTON = "cancel_request:";

    // ============================================
    // CALLBACK DATA - Сделки
    // ============================================
    public static final String CALLBACK_CREATE_DEAL = "create_deal:";
    public static final String CALLBACK_CONFIRM_DEAL_AUTO = "confirm_deal_auto";
    public static final String CALLBACK_CONFIRM_COMPLETED_DEAL = "confirm_completed_deal:";
    public static final String CALLBACK_AUTHOR_CONFIRM = "author_confirm_deal:";

    // ============================================
    // CALLBACK DATA - Оценки
    // ============================================
    public static final String CALLBACK_RATE_DEAL = "rate_deal:";
    public static final String CALLBACK_SKIP_RATING = "skip_rating:";

    // ============================================
    // CALLBACK DATA - История
    // ============================================
    public static final String CALLBACK_VIEW_DEAL = "view_deal:";
    public static final String CALLBACK_HISTORY_PAGE = "history_page:";

    public static final String USER_NOT_FOUND = "User not found";

    public static final String PLN = "PLN";
    public static final String KZT = "KZT";

    // КОМАНДЫ БОТА
// ============================================
    public static final String COMMAND_START = "/start";
    public static final String COMMAND_MENU = "/menu";
    public static final String COMMAND_NEED = "/need";
    public static final String COMMAND_SEARCH = "/search";
    public static final String COMMAND_MY_REQUESTS = "/my_requests";
    public static final String COMMAND_HISTORY = "/history";
    public static final String COMMAND_PROFILE = "/profile";
    public static final String COMMAND_RATES = "/rates";
    public static final String COMMAND_HELP = "/help";

    public static final String RATE_LIMIT_TELEGRAM_COMMAND = "telegram_command";
    public static final String RATE_LIMIT_API_CALL = "api_call";

    private TelegramConstants() {
        // Utility class
    }
}