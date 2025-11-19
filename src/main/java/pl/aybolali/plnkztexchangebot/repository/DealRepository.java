package pl.aybolali.plnkztexchangebot.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.aybolali.plnkztexchangebot.entity.Deal;

import java.util.List;
import java.util.Optional;

@Repository
public interface DealRepository extends JpaRepository<Deal, Long> {

    // ===== ДЛЯ GET /api/deals (User's deals - all statuses) =====
    @Query("SELECT d FROM Deal d JOIN FETCH d.requester JOIN FETCH d.provider " +
            "WHERE d.requester.id = :userId OR d.provider.id = :userId " +
            "ORDER BY d.createdAt DESC")
    Page<Deal> findAllUserDeals(@Param("userId") Long userId, Pageable pageable);

    // ===== ДЛЯ /deals (Telegram Bot) =====
    @Query("SELECT d FROM Deal d WHERE (d.requester.id = :userId OR d.provider.id = :userId) " +
            "AND d.finishedAt IS NOT NULL ORDER BY d.finishedAt DESC")
    Page<Deal> findFinishedUserDeals(@Param("userId") Long userId, Pageable pageable);

    // ===== ДЛЯ сделок по запросу =====


    @Query("SELECT COUNT(d) FROM Deal d WHERE (d.requester.id = :userId OR d.provider.id = :userId) AND d.status = 'COMPLETED'")
    Long countCompletedByUserId(@Param("userId") Long userId);

    @Query("""
    SELECT DISTINCT d FROM Deal d
    LEFT JOIN FETCH d.requester
    LEFT JOIN FETCH d.provider
    WHERE (d.requester.id = :userId OR d.provider.id = :userId)
    AND d.status = 'COMPLETED'
    ORDER BY d.finishedAt DESC
    """)
    Page<Deal> findFinishedUserDealsWithUsers(@Param("userId") Long userId, Pageable pageable);

    //Загрузка Deal с Users (для оценки)
    @Query("""
    SELECT d FROM Deal d
    LEFT JOIN FETCH d.requester
    LEFT JOIN FETCH d.provider
    WHERE d.id = :id
    """)
    Optional<Deal> findByIdWithUsers(@Param("id") Long id);
}
