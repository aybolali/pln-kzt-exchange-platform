package pl.aybolali.plnkztexchangebot.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * ⭐ Telegram User ID - для авторизации
     */
    @Column(name = "telegram_user_id", nullable = false, unique = true)
    @NotNull(message = "Telegram user ID is required")
    private Long telegramUserId;

    @Column(name = "telegram_username", unique = true, nullable = false, length = 32)
    @NotBlank(message = "Telegram username is required")
    @Size(min = 3, max = 32)
    private String telegramUsername;

    @Column(name = "first_name", length = 32)
    @Size(max = 32)
    private String firstName;

    @Column(name = "last_name", length = 32)
    @Size(max = 32)
    private String lastName;

    @Column(name = "phone", length = 20)
    @Size(max = 20)
    private String phone;

    // ✅ ИСПРАВЛЕНО: @Builder.Default
    @Column(name = "trust_rating", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal trustRating = new BigDecimal("5.00");

    @Column(name = "successful_deals")
    @Builder.Default
    private Integer successfulDeals = 0;

    @Column(name = "is_phone_verified")
    @Builder.Default
    private Boolean isPhoneVerified = false;

    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Relationships
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ExchangeRequest> exchangeRequests;

    @OneToMany(mappedBy = "requester", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Deal> requestedDeals;

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Deal> providedDeals;

    @OneToMany(mappedBy = "rater", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Rating> givenRatings;

    @OneToMany(mappedBy = "ratedUser", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Rating> receivedRatings;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ✅ Clean Code: короткие методы
    public String getFullName() {
        if (firstName == null) return telegramUsername;
        if (lastName == null) return firstName;
        return firstName + " " + lastName;
    }

    /**
     * @deprecated Больше не используется. Используй setSuccessfulDeals() вместо этого.
     * Оставлен для обратной совместимости.
     */
    @Deprecated
    public void incrementSuccessfulDeals() {
        this.successfulDeals = (this.successfulDeals == null ? 0 : this.successfulDeals) + 1;
    }
}