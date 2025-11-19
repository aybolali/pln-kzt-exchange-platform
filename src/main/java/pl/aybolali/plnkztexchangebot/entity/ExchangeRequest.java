
package pl.aybolali.plnkztexchangebot.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "exchange_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"user"})
public class ExchangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "User is required")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_need", nullable = false, length = 3)
    @NotNull(message = "Currency is required")
    private Currency currencyNeed;

    @Column(name = "amount_need", nullable = false, precision = 12, scale = 2)
    @PositiveOrZero(message = "Amount must be positive or zero") // 🔥 ИЗМЕНЕНО с @Positive
    @NotNull(message = "Amount is required")
    private BigDecimal amountNeed;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExchangeRequestStatus status = ExchangeRequestStatus.ACTIVE;

    @Column(length = 500)
    @Size(max = 500, message = "Notes cannot exceed 500 characters")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_method", nullable = false, length = 20)
    @NotNull(message = "Transfer method is required")
    private TransferMethod transferMethod;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    // Все бизнес-методы остаются как были
    public boolean canCreateDeal(BigDecimal dealAmount) {
        if (this.status != ExchangeRequestStatus.ACTIVE) return false;
        if (dealAmount == null || dealAmount.compareTo(BigDecimal.ZERO) <= 0) return false;
        return dealAmount.compareTo(this.amountNeed) <= 0; //// надо поменять
    }

    public void updateAmountAfterDeal(BigDecimal dealAmount) {
        if (dealAmount == null || dealAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deal amount must be positive");
        }
        if (dealAmount.compareTo(this.amountNeed) > 0) {
            throw new IllegalArgumentException("Deal amount exceeds needed amount");
        }

        this.amountNeed = this.amountNeed.subtract(dealAmount);
        this.updatedAt = LocalDateTime.now();

        if (this.amountNeed.compareTo(BigDecimal.ONE) < 0) {
            this.status = ExchangeRequestStatus.COMPLETED;
            this.finishedAt = LocalDateTime.now();
            this.amountNeed = BigDecimal.ZERO;
        }
    }

    public void complete() {
        this.status = ExchangeRequestStatus.COMPLETED;
        this.finishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = ExchangeRequestStatus.CANCELLED;
        this.finishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void expire() {
        this.status = ExchangeRequestStatus.EXPIRED;
        this.finishedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() { return this.status == ExchangeRequestStatus.ACTIVE; }
    public boolean isCompleted() { return this.status == ExchangeRequestStatus.COMPLETED; }
    public boolean isCancelled() { return this.status == ExchangeRequestStatus.CANCELLED; }

    public boolean belongsToUser(Long userId) {
        return this.user != null && this.user.getId().equals(userId);
    }

    public enum Currency { PLN, KZT }
}