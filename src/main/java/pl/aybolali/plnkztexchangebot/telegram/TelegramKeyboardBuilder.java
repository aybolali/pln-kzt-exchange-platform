package pl.aybolali.plnkztexchangebot.telegram;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder для упрощенного создания inline-клавиатур Telegram
 */
public class TelegramKeyboardBuilder {

    private final List<List<InlineKeyboardButton>> rows = new ArrayList<>();
    private List<InlineKeyboardButton> currentRow = new ArrayList<>();

    /**
     * Добавить кнопку с callback data
     */
    public TelegramKeyboardBuilder addButton(String text, String callbackData) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setCallbackData(callbackData);
        currentRow.add(btn);
        return this;
    }

    /**
     * Добавить кнопку с URL
     */
    public TelegramKeyboardBuilder addUrlButton(String text, String url) {
        InlineKeyboardButton btn = new InlineKeyboardButton();
        btn.setText(text);
        btn.setUrl(url);
        currentRow.add(btn);
        return this;
    }

    /**
     * Перейти на новый ряд кнопок
     */
    public TelegramKeyboardBuilder newRow() {
        if (!currentRow.isEmpty()) {
            rows.add(new ArrayList<>(currentRow));
            currentRow.clear();
        }
        return this;
    }

    /**
     * Собрать клавиатуру
     */
    public InlineKeyboardMarkup build() {
        newRow();
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        keyboard.setKeyboard(rows);
        return keyboard;
    }

    /**
     * Создать пустую клавиатуру (для начала построения)
     */
    public static TelegramKeyboardBuilder create() {
        return new TelegramKeyboardBuilder();
    }
}