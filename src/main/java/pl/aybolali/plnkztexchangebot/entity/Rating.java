package pl.aybolali.plnkztexchangebot.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ratings",
        uniqueConstraints = @UniqueConstraint(columnNames = {"deal_id", "rater_id"}))
@Data
@Builder
@NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"deal", "rater", "ratedUser"})  //used for LOGs
public class Rating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== СВЯЗИ =====
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id", nullable = false)
    private Deal deal;                                 // Оцениваемая сделка

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rater_id", nullable = false)
    private User rater;                                // Кто оценивает

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rated_user_id", nullable = false)
    private User ratedUser;                            // Кого оценивают

    // ===== ОЦЕНКА =====
    @Column(name = "rating", nullable = false)
    @NotNull
    private BigDecimal rating;                            // 1-5 звезд

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ===== LIFECYCLE МЕТОДЫ =====
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
