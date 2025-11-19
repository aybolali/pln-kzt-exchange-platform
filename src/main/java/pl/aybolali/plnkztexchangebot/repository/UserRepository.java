package pl.aybolali.plnkztexchangebot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pl.aybolali.plnkztexchangebot.entity.User;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * ⭐ ГЛАВНЫЙ МЕТОД - поиск по Telegram User ID
     */
    Optional<User> findByTelegramUserId(Long telegramUserId);

    @Query("SELECT u FROM User u WHERE u.telegramUsername = :username")
    Optional<User> findByTelegramUsername(@Param("username") String username);
    boolean existsByTelegramUsername(String telegramUsername);

    @Query("SELECT u from User u where u.isEnabled=true")
    List<User> findAllActiveUsers();
}