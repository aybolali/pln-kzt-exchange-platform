package pl.aybolali.plnkztexchangebot.exception;

/**
 * 👤 Исключение когда пользователь не найден в системе
 * Используется в Telegram боте когда пользователь пытается выполнить действие без регистрации
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }

    public UserNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public UserNotFoundException(Long userId) {
        super("User not found with ID: " + userId);
    }

    public UserNotFoundException(String username, String field) {
        super("User not found with " + field + ": " + username);
    }
}
