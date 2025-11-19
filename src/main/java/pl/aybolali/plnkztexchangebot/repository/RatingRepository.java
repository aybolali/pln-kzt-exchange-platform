package pl.aybolali.plnkztexchangebot.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.aybolali.plnkztexchangebot.entity.Rating;

import java.util.Optional;

@Repository
public interface RatingRepository extends JpaRepository<Rating, Long> {

    // ===== ДЛЯ GET /api/ratings/deal/{id} =====
    Page<Rating> findByDealIdOrderByCreatedAtDesc(Long dealId, Pageable pageable);
    Page<Rating> findByRatedUserIdOrderByCreatedAtDesc(Long ratedUserId, Pageable pageable);

    // ===== ДЛЯ GET /api/users/{id}/rating =====
    @Query("SELECT AVG(r.rating) FROM Rating r WHERE r.ratedUser.id = :userId")
    Double getAverageRatingByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(r) FROM Rating r WHERE r.ratedUser.id = :userId")
    Long countRatingsByUserId(@Param("userId") Long userId);

    // ===== ДЛЯ проверки дублей при POST /api/ratings =====
    boolean existsByDealIdAndRaterId(Long dealId, Long raterId);

    // ===== ДЛЯ /rate_123 (Telegram Bot) =====
    Optional<Rating> findByDealIdAndRaterId(Long dealId, Long raterId);
}
