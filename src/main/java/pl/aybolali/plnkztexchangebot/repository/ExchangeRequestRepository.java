package pl.aybolali.plnkztexchangebot.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequest;
import pl.aybolali.plnkztexchangebot.entity.ExchangeRequestStatus;
import pl.aybolali.plnkztexchangebot.entity.TransferMethod;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeRequestRepository extends JpaRepository<ExchangeRequest, Long> {

    /**
     * Найти все ACTIVE запросы (для публичного списка)
     * Сортировка: от старых к новым (FIFO)
     */
    @Query("SELECT DISTINCT er FROM ExchangeRequest er " +
            "JOIN FETCH er.user " +
            "WHERE er.status = 'ACTIVE' " +
            "ORDER BY er.createdAt DESC")
    Page<ExchangeRequest> findAllActiveRequests(Pageable pageable);

    /**
     * Найти все запросы пользователя (для /my)
     * Сортировка: от новых к старым
     */
    Page<ExchangeRequest> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);


    /**
     * ⭐ НОВЫЙ МЕТОД: Найти запросы пользователя по статусу
     * Используется для проверки лимита активных запросов (max 5)
     */
    List<ExchangeRequest> findByUserIdAndStatus(Long userId, ExchangeRequestStatus status);


    /**
     * Найти ACTIVE запросы по валюте
     * Сортировка: от новых к старым
     */
    @Query("SELECT DISTINCT er FROM ExchangeRequest er " +
            "JOIN FETCH er.user " +
            "WHERE er.currencyNeed = :currency AND er.status = 'ACTIVE' " +
            "ORDER BY er.createdAt DESC")
    Page<ExchangeRequest> findActiveByCurrency(
            @Param("currency") ExchangeRequest.Currency currency,
            Pageable pageable
    );

    /**
     * Найти ACTIVE запрос пользователя по валюте (для counterparty update)
     * Используется в DealService для обновления запроса provider'а
     */
    @Query("SELECT er FROM ExchangeRequest er WHERE er.user.id = :userId " +
            "AND er.currencyNeed = :currency AND er.status = 'ACTIVE' " +
            "ORDER BY er.createdAt DESC")
    Optional<ExchangeRequest> findActiveRequestByUserAndCurrency(@Param("userId") Long userId,
                                                                 @Param("currency") ExchangeRequest.Currency currency);

    /**
     * Найти matching offers для запроса
     * Исключает текущего пользователя, фильтрует по валюте и методу перевода
     *
     * ⚠️ ВАЖНО: В JPQL используется <> вместо !=
     */
    @Query("SELECT er FROM ExchangeRequest er WHERE " +
            "er.currencyNeed = :oppositeCurrency AND " +
            "er.status = 'ACTIVE' AND " +
            "er.transferMethod = :transferMethod AND " +
            "er.user.id <> :currentUserId " +
            "ORDER BY er.createdAt ASC")
    Page<ExchangeRequest> findMatchesForRequest(
            @Param("oppositeCurrency") ExchangeRequest.Currency oppositeCurrency,
            @Param("transferMethod") TransferMethod transferMethod,
            @Param("currentUserId") Long currentUserId,
            Pageable pageable);

    // ========================================================================
    // EXPIRATION MANAGEMENT (NEW in v5.5.1)
    // ========================================================================

    /**
     * Найти ACTIVE запросы старше указанной даты (для автоистечения)
     * Используется в scheduled task для пометки EXPIRED
     */
    @Query("SELECT r FROM ExchangeRequest r WHERE r.status = 'ACTIVE' AND r.createdAt < :cutoffDate")
    List<ExchangeRequest> findOldActiveRequests(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Найти все EXPIRED запросы (для удаления в воскресенье)
     */
    @Query("SELECT r FROM ExchangeRequest r WHERE r.status = 'EXPIRED'")
    List<ExchangeRequest> findAllExpiredRequests();

    /**
     * Удалить все EXPIRED запросы (только EXPIRED статус!)
     * ⚠️ Безопасно: deals сохраняются благодаря отсутствию CASCADE
     */
    @Modifying
    @Query("DELETE FROM ExchangeRequest r WHERE r.status = 'EXPIRED'")
    int deleteAllExpiredRequests();

    @Query("SELECT r FROM ExchangeRequest r JOIN FETCH r.user WHERE r.id = :id")
    Optional<ExchangeRequest> findByIdWithUser(@Param("id") Long id);
    /**
     * Подсчитать requests по статусу
     */
    long countByStatus(ExchangeRequestStatus status);

    /**
     * Подсчитать ACTIVE запросы старше указанной даты
     */
    @Query("SELECT COUNT(r) FROM ExchangeRequest r WHERE r.status = 'ACTIVE' AND r.createdAt < :cutoffDate")
    long countOldActiveRequests(@Param("cutoffDate") LocalDateTime cutoffDate);

    // Найти активные заявки по валюте без пагинации (для поиска)
    @Query("""
    SELECT DISTINCT er FROM ExchangeRequest er
    LEFT JOIN FETCH er.user
    WHERE er.currencyNeed = :currency
    AND er.status = :status
    ORDER BY er.createdAt DESC
    """)
    List<ExchangeRequest> findByCurrencyNeedAndStatusOrderByCreatedAtDesc(
            @Param("currency") ExchangeRequest.Currency currencyNeed,
            @Param("status") ExchangeRequestStatus status
    );

    /**
     * Найти COMPLETED запросы старше указанной даты (для cleanup)
     */
    @Query("SELECT r FROM ExchangeRequest r " +
            "WHERE r.status = 'COMPLETED' AND r.updatedAt < :cutoffDate")
    List<ExchangeRequest> findOldCompletedRequests(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Удалить старые COMPLETED и CANCELLED запросы
     * ⚠️ Безопасно: deals сохраняются благодаря отсутствию CASCADE
     */
    @Modifying
    @Query("DELETE FROM ExchangeRequest r " +
            "WHERE (r.status = 'COMPLETED' OR r.status = 'CANCELLED') " +
            "AND r.updatedAt < :cutoffDate")
    int deleteOldCompletedAndCancelledRequests(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Найти ACTIVE запросы старше указанной даты (для cleanup - отмена)
     */
    @Query("SELECT r FROM ExchangeRequest r " +
            "WHERE r.status = :status AND r.createdAt < :cutoffDate")
    List<ExchangeRequest> findByStatusAndCreatedAtBefore(
            @Param("status") ExchangeRequestStatus status,
            @Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Удалить старые COMPLETED заявки
     * ⚠️ БЕЗОПАСНО: deals больше не имеют FK на exchange_requests
     */
    @Modifying
    @Query("DELETE FROM ExchangeRequest r " +
            "WHERE r.status = 'COMPLETED' AND r.updatedAt < :cutoffDate")
    int deleteOldCompletedRequests(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Удалить старые CANCELLED заявки
     * ⚠️ БЕЗОПАСНО: deals больше не имеют FK на exchange_requests
     */
    @Modifying
    @Query("DELETE FROM ExchangeRequest r " +
            "WHERE r.status = 'CANCELLED' AND r.updatedAt < :cutoffDate")
    int deleteOldCancelledRequests(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query(value = "ALTER SEQUENCE exchange_requests_id_seq RESTART WITH 1", nativeQuery = true)
    void resetSequence();
}