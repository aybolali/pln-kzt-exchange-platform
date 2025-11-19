package pl.aybolali.plnkztexchangebot.exception;

/**
 * ⏳ Исключение превышения лимитов запросов
 * Используется в RateLimitingService для защиты от спама
 */
public class RateLimitException extends RuntimeException {

    private Long userId;
    private String action;
    private int limit;
    private int currentCount;

    public RateLimitException(String message) {
        super(message);
    }

    public RateLimitException(Long userId, String action, int limit, int currentCount) {
        super("Rate limit exceeded for user " + userId + " action " + action +
                ": " + currentCount + "/" + limit + " requests");
        this.userId = userId;
        this.action = action;
        this.limit = limit;
        this.currentCount = currentCount;
    }

    public Long getUserId() { return userId; }
    public String getAction() { return action; }
    public int getLimit() { return limit; }
    public int getCurrentCount() { return currentCount; }
}