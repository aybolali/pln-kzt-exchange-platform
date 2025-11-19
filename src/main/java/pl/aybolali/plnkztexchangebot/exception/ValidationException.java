package pl.aybolali.plnkztexchangebot.exception;

/**
 * ✅ Исключение валидации пользовательского ввода
 * Используется когда данные от пользователя не прошли проверку
 * Например: неверная сумма, неподдерживаемая валюта, некорректный формат
 */
public class ValidationException extends RuntimeException {

    private String field;
    private Object value;

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ValidationException(String field, Object value, String message) {
        super(message);
        this.field = field;
        this.value = value;
    }

    public ValidationException(String field, Object value) {
        super("Validation failed for field '" + field + "' with value '" + value + "'");
        this.field = field;
        this.value = value;
    }

    public String getField() {
        return field;
    }

    public Object getValue() {
        return value;
    }
}